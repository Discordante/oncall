package com.nextdigital.oncall.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextdigital.oncall.entity.Application;
import com.nextdigital.oncall.entity.OnCallGroup;
import com.nextdigital.oncall.entity.ZadarmaCall;

import com.nextdigital.oncall.repository.IncidentRepository;
import com.nextdigital.oncall.repository.OnCallGroupRepository;
import com.nextdigital.oncall.repository.ZadarmaCallRepository;
import com.nextdigital.oncall.repository.ZadarmaSyncedDayRepository;
import com.nextdigital.oncall.zadarma.client.ZadarmaReadonlyClient;
import com.nextdigital.oncall.zadarma.dto.ZadarmaCallDisposition;
import com.nextdigital.oncall.zadarma.dto.ZadarmaCallRecord;
import com.nextdigital.oncall.zadarma.dto.ZadarmaStatsResponse;

@ExtendWith(MockitoExtension.class)
class CallSyncServiceImplTest {

    @Mock
    ZadarmaReadonlyClient zadarmaReadonlyClient;
    @Mock
    ZadarmaCallRepository callRepository;
    @Mock
    ZadarmaSyncedDayRepository syncedDayRepository;
    @Mock
    OnCallGroupRepository groupRepository;
    @Mock
    IncidentRepository incidentRepository;

    @InjectMocks
    CallSyncServiceImpl service;

    private static final String SIP = "921495";
    private static final String START = "2026-09-01 00:00:00";
    private static final String END = "2026-09-30 23:59:59"; // 30 days, all in the past

    @Test
    void getCallRecords_serveFromDB_whenRangeFullyCovered() {
        when(syncedDayRepository.countCoveredDays(eq(SIP), any(), any())).thenReturn(30L);
        when(callRepository.findByCallStartedAtBetweenAndSipId(any(), any(), eq(SIP))).thenReturn(List.of());

        service.getCallRecords(START, END, SIP);

        verifyNoInteractions(zadarmaReadonlyClient);
    }

    @Test
    void getCallRecords_fetchesFromZadarma_whenRangeNotCovered() {
        when(syncedDayRepository.countCoveredDays(eq(SIP), any(), any())).thenReturn(10L); // only 10 of 30
        when(zadarmaReadonlyClient.getStatistics(START, END, SIP)).thenReturn(statsWithOneRecord());
        when(callRepository.existsByExternalCallId("ext-1")).thenReturn(false);
        when(callRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(groupRepository.findByZadarmaConfigSipId(SIP)).thenReturn(Optional.empty());
        when(callRepository.findByCallStartedAtBetweenAndSipId(any(), any(), eq(SIP))).thenReturn(List.of());

        service.getCallRecords(START, END, SIP);

        verify(zadarmaReadonlyClient).getStatistics(START, END, SIP);
        verify(callRepository).save(any());
    }

    @Test
    void getCallRecords_skipsAlreadyPersistedRecords() {
        when(syncedDayRepository.countCoveredDays(eq(SIP), any(), any())).thenReturn(0L);
        when(zadarmaReadonlyClient.getStatistics(START, END, SIP)).thenReturn(statsWithOneRecord());
        when(callRepository.existsByExternalCallId("ext-1")).thenReturn(true);
        when(groupRepository.findByZadarmaConfigSipId(SIP)).thenReturn(Optional.empty());
        when(callRepository.findByCallStartedAtBetweenAndSipId(any(), any(), eq(SIP))).thenReturn(List.of());

        service.getCallRecords(START, END, SIP);

        verify(callRepository, never()).save(any());
    }

    @Test
    void getCallRecords_alwaysFetchesWhenEndDateIsToday() {
        String todayEnd = LocalDate.now(ZoneId.of("Europe/Madrid"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + " 23:59:59";
        when(zadarmaReadonlyClient.getStatistics(any(), any(), eq(SIP))).thenReturn(emptyStats());
        when(callRepository.findByCallStartedAtBetweenAndSipId(any(), any(), eq(SIP))).thenReturn(List.of());

        service.getCallRecords(START, todayEnd, SIP);

        verify(zadarmaReadonlyClient).getStatistics(any(), any(), eq(SIP));
        // coverage count is skipped — range includes today
        verify(syncedDayRepository, never()).countCoveredDays(any(), any(), any());
    }

    @Test
    void getCallRecords_alwaysFetchesWhenSipIsNull() {
        when(zadarmaReadonlyClient.getStatistics(START, END, null)).thenReturn(emptyStats());
        when(callRepository.findByCallStartedAtBetween(any(), any())).thenReturn(List.of());

        service.getCallRecords(START, END, null);

        verify(zadarmaReadonlyClient).getStatistics(START, END, null);
        verifyNoInteractions(syncedDayRepository);
    }

    @Test
    void syncAndGet_alwaysFetchesFromZadarma_regardlessOfCoverage() {
        when(zadarmaReadonlyClient.getStatistics(START, END, SIP)).thenReturn(emptyStats());
        when(callRepository.findByCallStartedAtBetweenAndSipId(any(), any(), eq(SIP))).thenReturn(List.of());

        service.syncAndGet(START, END, SIP);

        verify(zadarmaReadonlyClient).getStatistics(START, END, SIP);
        // coverage check never consulted for a forced sync
        verify(syncedDayRepository, never()).countCoveredDays(any(), any(), any());
    }

    @Test
    void getCallRecords_returnsResponsesFromLocalDB() {
        when(syncedDayRepository.countCoveredDays(eq(SIP), any(), any())).thenReturn(30L);
        var storedCall = com.nextdigital.oncall.entity.ZadarmaCall.builder()
                .id(java.util.UUID.randomUUID())
                .externalCallId("ext-stored")
                .sipId(SIP)
                .callStartedAt(java.time.OffsetDateTime.parse("2026-09-05T10:00:00Z"))
                .billSeconds(60)
                .disposition("answered")
                .build();
        when(callRepository.findByCallStartedAtBetweenAndSipId(any(), any(), eq(SIP)))
                .thenReturn(List.of(storedCall));

        var result = service.getCallRecords(START, END, SIP);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).externalCallId()).isEqualTo("ext-stored");
    }

    // ── helpers ──────────────────────────────────

    private ZadarmaStatsResponse statsWithOneRecord() {
        var record = new ZadarmaCallRecord(
                "ext-1", SIP, "2026-09-05 10:00:00", 34600000001L, 34921495L,
                60, ZadarmaCallDisposition.ANSWERED, "Spain");
        return new ZadarmaStatsResponse("success", START, END, List.of(record));
    }

    private ZadarmaStatsResponse emptyStats() {
        return new ZadarmaStatsResponse("success", START, END, List.of());
    }

    @Test
    void answeredCall_withGroupAndApplication_createsDraftIncident() {
        when(syncedDayRepository.countCoveredDays(eq(SIP), any(), any())).thenReturn(0L);
        when(zadarmaReadonlyClient.getStatistics(START, END, SIP)).thenReturn(statsWithOneRecord());
        when(callRepository.existsByExternalCallId("ext-1")).thenReturn(false);

        var app = Application.builder().id(java.util.UUID.randomUUID()).name("App A").build();
        var group = OnCallGroup.builder()
                .id(java.util.UUID.randomUUID())
                .name("Guardia test")
                .applications(new HashSet<>(java.util.Set.of(app)))
                .build();
        when(groupRepository.findByZadarmaConfigSipId(SIP)).thenReturn(Optional.of(group));

        var savedCall = ZadarmaCall.builder()
                .id(java.util.UUID.randomUUID())
                .externalCallId("ext-1").sipId(SIP)
                .callStartedAt(OffsetDateTime.parse("2026-09-05T10:00:00Z"))
                .billSeconds(60).disposition("answered")
                .build();
        when(callRepository.save(any())).thenReturn(savedCall);
        when(incidentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(callRepository.findByCallStartedAtBetweenAndSipId(any(), any(), eq(SIP))).thenReturn(List.of());

        service.getCallRecords(START, END, SIP);

        verify(incidentRepository).save(any());
    }

    @Test
    void answeredCall_withNoGroup_doesNotCreateDraftIncident() {
        when(syncedDayRepository.countCoveredDays(eq(SIP), any(), any())).thenReturn(0L);
        when(zadarmaReadonlyClient.getStatistics(START, END, SIP)).thenReturn(statsWithOneRecord());
        when(callRepository.existsByExternalCallId("ext-1")).thenReturn(false);
        when(groupRepository.findByZadarmaConfigSipId(SIP)).thenReturn(Optional.empty());

        var savedCall = ZadarmaCall.builder()
                .id(java.util.UUID.randomUUID())
                .externalCallId("ext-1").sipId(SIP)
                .callStartedAt(OffsetDateTime.parse("2026-09-05T10:00:00Z"))
                .billSeconds(60).disposition("answered")
                .build();
        when(callRepository.save(any())).thenReturn(savedCall);
        when(callRepository.findByCallStartedAtBetweenAndSipId(any(), any(), eq(SIP))).thenReturn(List.of());

        service.getCallRecords(START, END, SIP);

        verify(incidentRepository, never()).save(any());
    }

    @Test
    void nonAnsweredCall_doesNotCreateDraftIncident() {
        when(syncedDayRepository.countCoveredDays(eq(SIP), any(), any())).thenReturn(0L);
        var busyRecord = new ZadarmaCallRecord(
                "ext-busy", SIP, "2026-09-05 10:00:00", 34600000001L, 34921495L,
                0, ZadarmaCallDisposition.NO_ANSWER, "Spain");
        when(zadarmaReadonlyClient.getStatistics(START, END, SIP))
                .thenReturn(new ZadarmaStatsResponse("success", START, END, List.of(busyRecord)));
        when(callRepository.existsByExternalCallId("ext-busy")).thenReturn(false);
        when(groupRepository.findByZadarmaConfigSipId(SIP)).thenReturn(Optional.empty());

        var savedCall = ZadarmaCall.builder()
                .id(java.util.UUID.randomUUID())
                .externalCallId("ext-busy").sipId(SIP)
                .callStartedAt(OffsetDateTime.parse("2026-09-05T10:00:00Z"))
                .billSeconds(0).disposition("no answer")
                .build();
        when(callRepository.save(any())).thenReturn(savedCall);
        when(callRepository.findByCallStartedAtBetweenAndSipId(any(), any(), eq(SIP))).thenReturn(List.of());

        service.getCallRecords(START, END, SIP);

        verify(incidentRepository, never()).save(any());
    }
}
