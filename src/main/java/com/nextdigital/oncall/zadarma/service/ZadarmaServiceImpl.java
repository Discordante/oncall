package com.nextdigital.oncall.zadarma.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.nextdigital.oncall.exception.ZadarmaApiException;
import com.nextdigital.oncall.repository.OnCallGroupRepository;
import com.nextdigital.oncall.repository.PersonRepository;
import com.nextdigital.oncall.zadarma.client.ZadarmaApiClient;
import com.nextdigital.oncall.zadarma.client.ZadarmaResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implements Zadarma call redirection business logic.
 *
 * <p>
 * Key behaviors:
 * </p>
 * <ul>
 * <li>Groups without {@code zadarmaSipId} are silently skipped (backward
 * compatible)</li>
 * <li>Persons without {@code phoneNumber} cause the redirect to fail with a log
 * error</li>
 * <li>Phone numbers are normalized (strip "+", spaces, dashes) before sending
 * to Zadarma</li>
 * <li>API errors are caught and returned as false (caller decides how to
 * handle)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZadarmaServiceImpl implements ZadarmaService {

    private final ZadarmaApiClient zadarmaApiClient;
    private final OnCallGroupRepository groupRepository;
    private final PersonRepository personRepository;

    @Override
    public boolean redirectToPerson(UUID groupId, UUID personId) {
        var group = groupRepository.findById(groupId).orElse(null);
        if (group == null) {
            log.error("redirectToPerson: group {} not found", groupId);
            return false;
        }

        String sipId = group.getZadarmaSipId();
        if (sipId == null || sipId.isBlank()) {
            log.debug("Group '{}' has no zadarmaSipId configured — skipping redirection", group.getName());
            return true; // Not an error — the group simply doesn't use Zadarma
        }

        var person = personRepository.findById(personId).orElse(null);
        if (person == null) {
            log.error("redirectToPerson: person {} not found", personId);
            return false;
        }

        String rawPhone = person.getPhoneNumber();
        if (rawPhone == null || rawPhone.isBlank()) {
            log.error("Person '{}' ({}) has no phone number configured — cannot redirect calls",
                    person.getName(), personId);
            return false;
        }

        String normalizedPhone = normalizePhoneNumber(rawPhone);
        if (normalizedPhone == null || normalizedPhone.isBlank()) {
            log.error("Person '{}' phone '{}' could not be normalized", person.getName(), rawPhone);
            return false;
        }

        try {
            ZadarmaResponse response = zadarmaApiClient.setSipRedirection(sipId, normalizedPhone);
            if (response.isSuccess()) {
                log.info("Call redirection set: group='{}' (SIP {}) → {} (phone ***{})",
                        group.getName(), sipId, person.getName(),
                        normalizedPhone.substring(Math.max(0, normalizedPhone.length() - 4)));
                return true;
            } else {
                log.error("Zadarma API returned error for group '{}': status={}, message={}",
                        group.getName(), response.status(), response.message());
                return false;
            }
        } catch (ZadarmaApiException e) {
            log.error("Zadarma API call failed for group '{}': {}", group.getName(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean disableRedirection(UUID groupId) {
        var group = groupRepository.findById(groupId).orElse(null);
        if (group == null) {
            log.error("disableRedirection: group {} not found", groupId);
            return false;
        }

        String sipId = group.getZadarmaSipId();
        if (sipId == null || sipId.isBlank()) {
            return true;
        }

        try {
            ZadarmaResponse response = zadarmaApiClient.disableSipRedirection(sipId);
            if (response.isSuccess()) {
                log.info("Call redirection disabled for group '{}' (SIP {})", group.getName(), sipId);
                return true;
            } else {
                log.error("Failed to disable redirection for group '{}': status={}, message={}",
                        group.getName(), response.status(), response.message());
                return false;
            }
        } catch (ZadarmaApiException e) {
            log.error("Zadarma API call failed while disabling redirection for group '{}': {}",
                    group.getName(), e.getMessage());
            return false;
        }
    }

    @Override
    public Optional<String> getCurrentRedirectionDestination(UUID groupId) {
        var group = groupRepository.findById(groupId).orElse(null);
        if (group == null) {
            return Optional.empty();
        }

        String sipId = group.getZadarmaSipId();
        if (sipId == null || sipId.isBlank()) {
            return Optional.empty();
        }

        try {
            ZadarmaResponse response = zadarmaApiClient.getSipRedirection(sipId);
            if (response.isSuccess()) {
                String dest = response.destination();
                if (dest == null) {
                    dest = response.destinationValue();
                }
                return Optional.ofNullable(dest);
            }
            return Optional.empty();
        } catch (ZadarmaApiException e) {
            log.warn("Failed to get redirection status for group '{}': {}", group.getName(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public String normalizePhoneNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        // Strip "+", spaces, dashes, parentheses, and dots
        String normalized = raw.replaceAll("[+\\s\\-().]", "");
        // Validate: must contain only digits
        if (!normalized.matches("\\d+")) {
            log.warn("Phone number '{}' contains non-digit characters after normalization", raw);
            return null;
        }
        // Validate reasonable length (7-15 digits per E.164)
        if (normalized.length() < 7 || normalized.length() > 15) {
            log.warn("Phone number '{}' has invalid length: {} digits", raw, normalized.length());
            return null;
        }
        return normalized;
    }
}
