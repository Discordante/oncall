package com.nextdigital.oncall.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    private static final DateTimeFormatter TITLE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ROOT);

    private final ZadarmaReadonlyClient zadarmaReadonlyClient;
    private final ZadarmaCallRepository callRepository;
    private final ZadarmaSyncedDayRepository syncedDayRepository;
    private final OnCallGroupRepository groupRepository;
    private final IncidentRepository incidentRepository;

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
        if (!endDate.isBefore(LocalDate.now(ZoneOffset.UTC))) {
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
        for (var record : response.stats()) {
            if (!callRepository.existsByExternalCallId(record.id())) {
                var saved = callRepository.save(ZadarmaCallMapper.toEntity(record, group));
                newRecords++;
                tryCreateDraftIncident(saved, group);
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
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            if (cursor.isBefore(today)) { // only mark fully-past days
                var dayId = new ZadarmaSyncedDay.ZadarmaSyncedDayId(sip, cursor);
                if (!syncedDayRepository.existsById(dayId)) {
                    syncedDayRepository.save(
                            ZadarmaSyncedDay.builder()
                                    .id(dayId)
                                    .syncedAt(OffsetDateTime.now(ZoneOffset.UTC))
                                    .build());
                }
            }
            cursor = cursor.plusDays(1);
        }
    }

    // ─────────────────────────────────────────────
    // Auto draft incident for answered calls
    // ─────────────────────────────────────────────

    private void tryCreateDraftIncident(ZadarmaCall call, OnCallGroup group) {
        if (!"answered".equals(call.getDisposition())) {
            return;
        }
        if (group == null || group.getApplications().isEmpty()) {
            log.debug("Answered call {} has no associated application — skipping draft incident",
                    call.getExternalCallId());
            return;
        }
        try {
            var application = group.getApplications().iterator().next();
            var title = "Llamada atendida vía SIP %s – %s".formatted(
                    call.getSipId(), call.getCallStartedAt().format(TITLE_FMT));
            var incident = Incident.builder()
                    .title(title)
                    .application(application)
                    .startedAt(call.getCallStartedAt())
                    .calls(new HashSet<>(Set.of(call)))
                    .build();
            incidentRepository.save(incident);
            log.info("Draft incident created for answered call {} (app: {})",
                    call.getExternalCallId(), application.getName());
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
        return records.stream().map(ZadarmaCallMapper::toResponse).toList();
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
            return LocalDateTime.parse(dateStr, ZADARMA_FMT).atOffset(ZoneOffset.UTC);
        } catch (Exception e) {
            log.warn("Could not parse datetime string: '{}'", dateStr);
            return null;
        }
    }
}
