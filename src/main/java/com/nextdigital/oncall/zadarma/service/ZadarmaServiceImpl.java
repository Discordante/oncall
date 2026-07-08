package com.nextdigital.oncall.zadarma.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.nextdigital.oncall.exception.ZadarmaApiException;
import com.nextdigital.oncall.repository.OnCallGroupRepository;
import com.nextdigital.oncall.repository.PersonRepository;
import com.nextdigital.oncall.zadarma.client.ZadarmaApiClient;
import com.nextdigital.oncall.zadarma.client.ZadarmaReadonlyClient;
import com.nextdigital.oncall.zadarma.client.ZadarmaResponse;
import com.nextdigital.oncall.zadarma.dto.SipInfoResponse;
import com.nextdigital.oncall.zadarma.dto.ZadarmaDirectNumbersResponse;
import com.nextdigital.oncall.zadarma.dto.ZadarmaSipListResponse;
import com.nextdigital.oncall.zadarma.dto.ZadarmaSipRedirectionResponse;
import com.nextdigital.oncall.zadarma.dto.ZadarmaStatsResponse;

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
    private final ZadarmaReadonlyClient zadarmaReadonlyClient;
    private final OnCallGroupRepository groupRepository;
    private final PersonRepository personRepository;

    @Override
    public boolean redirectToPerson(UUID groupId, UUID personId) {
        var group = groupRepository.findById(groupId).orElse(null);
        if (group == null) {
            log.error("redirectToPerson: group {} not found", groupId);
            return false;
        }

        var zadarmaConfig = group.getZadarmaConfig();
        if (zadarmaConfig == null || zadarmaConfig.getSipId() == null || zadarmaConfig.getSipId().isBlank()) {
            log.debug("Group '{}' has no Zadarma config configured — skipping redirection", group.getName());
            return true; // Not an error — the group simply doesn't use Zadarma
        }

        String sipId = zadarmaConfig.getSipId();

        if (Boolean.FALSE.equals(zadarmaConfig.getActive())) {
            log.debug("Group '{}' has Zadarma redirection disabled (active=false) — disabling API redirection",
                    group.getName());
            return disableRedirectionBySipId(group.getName(), sipId);
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

        var zadarmaConfig = group.getZadarmaConfig();
        if (zadarmaConfig == null || zadarmaConfig.getSipId() == null || zadarmaConfig.getSipId().isBlank()) {
            return true;
        }

        return disableRedirectionBySipId(group.getName(), zadarmaConfig.getSipId());
    }

    private boolean disableRedirectionBySipId(String groupName, String sipId) {
        try {
            ZadarmaResponse response = zadarmaApiClient.disableSipRedirection(sipId);
            if (response.isSuccess()) {
                log.info("Call redirection disabled for group '{}' (SIP {})", groupName, sipId);
                return true;
            } else {
                log.error("Failed to disable redirection for group '{}': status={}, message={}",
                        groupName, response.status(), response.message());
                return false;
            }
        } catch (ZadarmaApiException e) {
            log.error("Zadarma API call failed while disabling redirection for group '{}': {}",
                    groupName, e.getMessage());
            return false;
        }
    }

    @Override
    public Optional<String> getCurrentRedirectionDestination(UUID groupId) {
        var group = groupRepository.findById(groupId).orElse(null);
        if (group == null) {
            return Optional.empty();
        }

        var zadarmaConfig = group.getZadarmaConfig();
        if (zadarmaConfig == null || zadarmaConfig.getSipId() == null || zadarmaConfig.getSipId().isBlank()) {
            return Optional.empty();
        }

        String sipId = zadarmaConfig.getSipId();

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

    // ──────────────────────────────────────────────
    // Read-only analytics queries
    // ──────────────────────────────────────────────

    @Override
    public List<SipInfoResponse> getSipList() {
        try {
            ZadarmaSipListResponse response = zadarmaReadonlyClient.getSipList();
            if (response.sips() == null || response.sips().isEmpty()) {
                log.warn("Zadarma /v1/sip/ returned null or empty sips list");
                return Collections.emptyList();
            }

            // Build a map: sipId → group name for quick lookup
            Map<String, String> sipToGroup = groupRepository.findAll().stream()
                    .filter(g -> g.getZadarmaConfig() != null
                            && g.getZadarmaConfig().getSipId() != null
                            && !g.getZadarmaConfig().getSipId().isBlank())
                    .collect(Collectors.toMap(
                            g -> g.getZadarmaConfig().getSipId().trim(),
                            g -> g.getName(),
                            (existing, replacement) -> existing));

            List<SipInfoResponse> enriched = response.sips().stream()
                    .map(sipObj -> new SipInfoResponse(sipObj.id(), sipToGroup.get(sipObj.id().trim())))
                    .toList();

            log.debug("Retrieved {} SIP IDs from Zadarma, {} assigned to groups",
                    enriched.size(), enriched.stream().filter(s -> s.groupName() != null).count());
            return enriched;
        } catch (ZadarmaApiException e) {
            log.error("Failed to retrieve SIP list: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public ZadarmaSipRedirectionResponse getSipRedirections(String sipId) {
        try {
            ZadarmaSipRedirectionResponse response = zadarmaReadonlyClient.getSipRedirections(sipId);
            log.debug("Received {} SIP redirection entries from Zadarma",
                    response.info() != null ? response.info().size() : 0);
            return response;
        } catch (ZadarmaApiException e) {
            log.error("Failed to retrieve SIP redirections: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public ZadarmaStatsResponse getCallsSummary(String start, String end, String sip) {
        try {
            ZadarmaStatsResponse response = zadarmaReadonlyClient.getStatistics(start, end, sip);
            log.debug("Received {} incoming call records from Zadarma",
                    response.stats() != null ? response.stats().size() : 0);
            return response;
        } catch (ZadarmaApiException e) {
            log.error("Failed to retrieve incoming call statistics: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public ZadarmaDirectNumbersResponse getDirectNumbers() {
        try {
            ZadarmaDirectNumbersResponse response = zadarmaReadonlyClient.getDirectNumbers();
            log.debug("Retrieved {} direct numbers from Zadarma",
                    response.info() != null ? response.info().size() : 0);
            return response;
        } catch (ZadarmaApiException e) {
            log.error("Failed to retrieve direct numbers: {}", e.getMessage());
            throw e;
        }
    }
}
