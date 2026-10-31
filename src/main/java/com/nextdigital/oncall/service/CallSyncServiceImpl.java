package com.nextdigital.oncall.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextdigital.oncall.dto.CallRecordResponse;
import com.nextdigital.oncall.entity.Incident;
import com.nextdigital.oncall.entity.OnCallGroup;
import com.nextdigital.oncall.entity.ZadarmaCall;
import com.nextdigital.oncall.entity.ZadarmaSyncedDay;
import com.nextdigital.oncall.mapper.ZadarmaCallMapper;
import com.nextdigital.oncall.repository.IncidentRepository;
import com.nextdigital.oncall.repository.OnCallGroupRepository;
import com.nextdigital.oncall.repository.PersonRepository;
import com.nextdigital.oncall.repository.ZadarmaCallRepository;
import com.nextdigital.oncall.repository.ZadarmaSyncedDayRepository;
import com.nextdigital.oncall.zadarma.client.ZadarmaReadonlyClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallSyncServiceImpl implements CallSyncService {

    private static final DateTimeFormatter ZADARMA_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Zadarma timestamps and day boundaries use the account timezone
     * (Europe/Madrid).
     */
    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Madrid");

    private static final DateTimeFormatter TITLE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT);

    private final ZadarmaReadonlyClient zadarmaReadonlyClient;
    private final ZadarmaCallRepository callRepository;
    private final ZadarmaSyncedDayRepository syncedDayRepository;
    private final OnCallGroupRepository groupRepository;
    private final IncidentRepository incidentRepository;
    private final PersonRepository personRepository;

    @Override
    @Transactional
    public List<CallRecordResponse> getCallRecords(String start, String end, String sip) {
        if (isCovered(start, end, sip)) {
            log.debug("Cache hit for range [{}, {}] sip={} — serving from DB", start, end, sip);
        } else {
            fetchAndPersist(start, end, sip);
        }
        return queryLocal(start, end, sip);
    }

    @Override
    @Transactional
    public List<CallRecordResponse> syncAndGet(String start, String end, String sip) {
        fetchAndPersist(start, end, sip);
        return queryLocal(start, end, sip);
    }

    // ─────────────────────────────────────────────
    // Coverage check
    // ─────────────────────────────────────────────

    private boolean isCovered(String start, String end, String sip) {
        if (sip == null || sip.isBlank()) {
            return false; // cannot track coverage without a SIP
        }
        LocalDate startDate = parseDate(start);
        LocalDate endDate = parseDate(end);
        if (startDate == null || endDate == null) {
            return false;
        }
        if (!endDate.isBefore(LocalDate.now(APP_ZONE))) {
            return false; // range includes today or future — always refresh
        }
        long expectedDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        long coveredDays = syncedDayRepository.countCoveredDays(sip, startDate, endDate);
        return coveredDays >= expectedDays;
    }

    // ─────────────────────────────────────────────
    // Sync from Zadarma
    // ─────────────────────────────────────────────

    private void fetchAndPersist(String start, String end, String sip) {
        log.debug("Syncing from Zadarma: range=[{}, {}] sip={}", start, end, sip);

        var response = zadarmaReadonlyClient.getStatistics(start, end, sip);
        if (response.stats() == null || response.stats().isEmpty()) {
            log.debug("Zadarma returned no records for range [{}, {}] sip={}", start, end, sip);
            markDaysCovered(start, end, sip);
            return;
        }

        var group = (sip != null && !sip.isBlank())
                ? groupRepository.findByZadarmaConfigSipId(sip).orElse(null)
                : null;

        int newRecords = 0;
        var checkNumbers = loadCheckNumbers();
        for (var record : response.stats()) {
            if (!callRepository.existsByExternalCallId(record.id())) {
                var saved = callRepository.save(ZadarmaCallMapper.toEntity(record, group));
                newRecords++;
                tryCreateDraftIncident(saved, group, checkNumbers);
            }
        }
        log.info("Zadarma sync: {} new records persisted (range=[{}, {}] sip={})", newRecords, start, end, sip);

        markDaysCovered(start, end, sip);
    }

    private void markDaysCovered(String start, String end, String sip) {
        if (sip == null || sip.isBlank()) {
            return;
        }
        LocalDate startDate = parseDate(start);
        LocalDate endDate = parseDate(end);
        if (startDate == null || endDate == null) {
            return;
        }
        LocalDate today = LocalDate.now(APP_ZONE);
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            if (cursor.isBefore(today)) { // only mark fully-past days
                var dayId = new ZadarmaSyncedDay.ZadarmaSyncedDayId(sip, cursor);
                if (!syncedDayRepository.existsById(dayId)) {
                    syncedDayRepository.save(
                            ZadarmaSyncedDay.builder()
                                    .id(dayId)
                                    .syncedAt(OffsetDateTime.now(APP_ZONE))
                                    .build());
                }
            }
            cursor = cursor.plusDays(1);
        }
    }

    // ─────────────────────────────────────────────
    // Auto draft incident for answered, non-check calls
    // ─────────────────────────────────────────────

    private void tryCreateDraftIncident(ZadarmaCall call, OnCallGroup group, Set<String> checkNumbers) {
        if (!"answered".equals(call.getDisposition())) {
            return;
        }
        if (isCheckCall(call, checkNumbers)) {
            log.info("Answered call {} originates from a registered person phone — skipping draft incident",
                    call.getExternalCallId());
            return;
        }
        if (group == null) {
            log.debug("Answered call {} has no associated on-call group — skipping draft incident",
                    call.getExternalCallId());
            return;
        }
        try {
            var title = "Llamada atendida vía SIP %s – %s".formatted(
                    call.getSipId(), call.getCallStartedAt().format(TITLE_FMT));
            // Application is intentionally left empty: only the user knows which
            // application is affected and assigns it when editing the incident.
            // Calls are NOT linked here — avoids FK ordering issue within the same
            // transaction; the user links them manually.
            var incident = Incident.builder()
                    .title(title)
                    .startedAt(call.getCallStartedAt())
                    .build();
            incidentRepository.save(incident);
            log.info("Draft incident created for answered call {}", call.getExternalCallId());
        } catch (Exception e) {
            log.warn("Could not auto-create draft incident for call {}: {}",
                    call.getExternalCallId(), e.getMessage());
        }
    }

    // ─────────────────────────────────────────────
    // DB query
    // ─────────────────────────────────────────────

    private List<CallRecordResponse> queryLocal(String start, String end, String sip) {
        OffsetDateTime startDt = parseDateTime(start);
        OffsetDateTime endDt = parseDateTime(end);
        if (startDt == null || endDt == null) {
            return List.of();
        }
        List<com.nextdigital.oncall.entity.ZadarmaCall> records = (sip != null && !sip.isBlank())
                ? callRepository.findByCallStartedAtBetweenAndSipId(startDt, endDt, sip)
                : callRepository.findByCallStartedAtBetween(startDt, endDt);
        var checkNumbers = loadCheckNumbers();
        return records.stream()
                .map(record -> ZadarmaCallMapper.toResponse(record, isCheckCall(record, checkNumbers)))
                .toList();
    }

    // ─────────────────────────────────────────────
    // Check-call detection
    // ─────────────────────────────────────────────

    /**
     * Loads the normalized phone numbers of every person registered in the
     * application. A call whose origin is one of those numbers is considered a
     * "comprobación" call (a check performed by a registered user), not a real
     * inbound call.
     */
    private Set<String> loadCheckNumbers() {
        List<String> phoneNumbers = personRepository.findAllPhoneNumbers();
        if (phoneNumbers == null) {
            return Set.of();
        }
        return phoneNumbers.stream()
                .map(CallSyncServiceImpl::normalizePhone)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static boolean isCheckCall(ZadarmaCall call, Set<String> checkNumbers) {
        if (checkNumbers.isEmpty()) {
            return false;
        }
        var caller = normalizePhone(call.getCallerNumber());
        return caller != null && checkNumbers.contains(caller);
    }

    /**
     * Reduces a phone number to its 9 significant digits (Spanish numbering
     * plan), so {@code +34 600 000 001}, {@code 34600000001} and
     * {@code 600000001} all match.
     */
    private static String normalizePhone(String raw) {
        if (raw == null) {
            return null;
        }
        var digits = raw.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        return digits.length() > 9 ? digits.substring(digits.length() - 9) : digits;
    }

    // ─────────────────────────────────────────────
    // Date parsing
    // ─────────────────────────────────────────────

    private static LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateStr, ZADARMA_FMT).toLocalDate();
        } catch (Exception e) {
            log.warn("Could not parse date string: '{}'", dateStr);
            return null;
        }
    }

    private static OffsetDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateStr, ZADARMA_FMT).atZone(APP_ZONE).toOffsetDateTime();
        } catch (Exception e) {
            log.warn("Could not parse datetime string: '{}'", dateStr);
            return null;
        }
    }
}
