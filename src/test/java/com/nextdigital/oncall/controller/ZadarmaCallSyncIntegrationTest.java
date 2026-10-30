package com.nextdigital.oncall.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nextdigital.oncall.config.TestcontainersConfig;
import com.nextdigital.oncall.dto.ApiResponse;
import com.nextdigital.oncall.dto.CallRecordResponse;
import com.nextdigital.oncall.repository.IncidentRepository;
import com.nextdigital.oncall.repository.ZadarmaCallRepository;
import com.nextdigital.oncall.repository.ZadarmaSyncedDayRepository;
import com.nextdigital.oncall.zadarma.client.ZadarmaReadonlyClient;
import com.nextdigital.oncall.zadarma.dto.ZadarmaCallDisposition;
import com.nextdigital.oncall.zadarma.dto.ZadarmaCallRecord;
import com.nextdigital.oncall.zadarma.dto.ZadarmaStatsResponse;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ZadarmaCallSyncIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ZadarmaCallRepository callRepository;

    @Autowired
    private ZadarmaSyncedDayRepository syncedDayRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @MockitoBean
    private ZadarmaReadonlyClient zadarmaReadonlyClient;

    private static final String SIP = "921495";
    // September 2026: fully in the past as of test date 2026-10-30
    private static final String START = "2026-09-01 00:00:00";
    private static final String END = "2026-09-30 23:59:59";

    @BeforeEach
    void setUp() {
        incidentRepository.deleteAll(); // cascades incident_call_records
        callRepository.deleteAll();
        syncedDayRepository.deleteAll();

        var record = new ZadarmaCallRecord(
                "ext-sync-1", SIP, "2026-09-05 10:00:00",
                34600000001L, 34921495L, 90, ZadarmaCallDisposition.ANSWERED, "Spain, Madrid");
        when(zadarmaReadonlyClient.getStatistics(eq(START), eq(END), eq(SIP)))
                .thenReturn(new ZadarmaStatsResponse("success", START, END, List.of(record)));
    }

    @Test
    void firstQuery_syncsFromZadarmaAndPersistsRecords() throws Exception {
        var result = mockMvc.perform(get("/api/v1/zadarma/calls-summary")
                .param("start", START)
                .param("end", END)
                .param("sip", SIP)
                .with(jwt()))
                .andExpect(status().isOk())
                .andReturn();

        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<List<CallRecordResponse>>>() {
                });

        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).externalCallId()).isEqualTo("ext-sync-1");
        assertThat(response.data().get(0).disposition()).isEqualTo("answered");
        assertThat(callRepository.count()).isEqualTo(1);
        verify(zadarmaReadonlyClient, times(1)).getStatistics(eq(START), eq(END), eq(SIP));
    }

    @Test
    void secondQuery_sameRange_servesFromDBWithoutCallingZadarma() throws Exception {
        // First query — populates DB and synced_days
        mockMvc.perform(get("/api/v1/zadarma/calls-summary")
                .param("start", START).param("end", END).param("sip", SIP)
                .with(jwt()))
                .andExpect(status().isOk());

        clearInvocations(zadarmaReadonlyClient);

        // Second query — should hit the local cache
        var result = mockMvc.perform(get("/api/v1/zadarma/calls-summary")
                .param("start", START).param("end", END).param("sip", SIP)
                .with(jwt()))
                .andExpect(status().isOk())
                .andReturn();

        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<List<CallRecordResponse>>>() {
                });

        assertThat(response.data()).hasSize(1);
        verifyNoInteractions(zadarmaReadonlyClient);
    }

    @Test
    void forceParam_bypassesCacheAndCallsZadarmaAgain() throws Exception {
        // Pre-populate
        mockMvc.perform(get("/api/v1/zadarma/calls-summary")
                .param("start", START).param("end", END).param("sip", SIP)
                .with(jwt()))
                .andExpect(status().isOk());

        clearInvocations(zadarmaReadonlyClient);
        when(zadarmaReadonlyClient.getStatistics(any(), any(), any()))
                .thenReturn(new ZadarmaStatsResponse("success", START, END, List.of()));

        mockMvc.perform(get("/api/v1/zadarma/calls-summary")
                .param("start", START).param("end", END).param("sip", SIP)
                .param("force", "true")
                .with(jwt()))
                .andExpect(status().isOk());

        verify(zadarmaReadonlyClient, times(1)).getStatistics(any(), any(), any());
    }

    @Test
    void duplicateExternalCallId_isNotPersistedTwice() throws Exception {
        // First sync
        mockMvc.perform(get("/api/v1/zadarma/calls-summary")
                .param("start", START).param("end", END).param("sip", SIP)
                .with(jwt()))
                .andExpect(status().isOk());

        assertThat(callRepository.count()).isEqualTo(1);

        // Force second sync with same record
        when(zadarmaReadonlyClient.getStatistics(any(), any(), any()))
                .thenReturn(new ZadarmaStatsResponse("success", START, END, List.of(
                        new ZadarmaCallRecord("ext-sync-1", SIP, "2026-09-05 10:00:00",
                                34600000001L, 34921495L, 90, ZadarmaCallDisposition.ANSWERED, "Spain"))));

        mockMvc.perform(get("/api/v1/zadarma/calls-summary")
                .param("start", START).param("end", END).param("sip", SIP)
                .param("force", "true")
                .with(jwt()))
                .andExpect(status().isOk());

        // Still only 1 record — upsert by external_call_id works
        assertThat(callRepository.count()).isEqualTo(1);
    }
}
