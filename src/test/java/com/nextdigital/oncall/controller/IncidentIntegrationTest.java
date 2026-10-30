package com.nextdigital.oncall.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nextdigital.oncall.config.TestcontainersConfig;
import com.nextdigital.oncall.dto.ApiResponse;
import com.nextdigital.oncall.dto.IncidentRequest;
import com.nextdigital.oncall.dto.IncidentResponse;
import com.nextdigital.oncall.entity.Application;
import com.nextdigital.oncall.entity.ZadarmaCall;
import com.nextdigital.oncall.repository.ApplicationRepository;
import com.nextdigital.oncall.repository.IncidentRepository;
import com.nextdigital.oncall.repository.ZadarmaCallRepository;
import com.nextdigital.oncall.zadarma.client.ZadarmaReadonlyClient;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class IncidentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ZadarmaCallRepository callRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    // Prevent ZadarmaReadonlyClient from calling real API during context startup
    @MockitoBean
    private ZadarmaReadonlyClient zadarmaReadonlyClient;

    private Application testApp;
    private ZadarmaCall testCall;

    @BeforeEach
    void setUp() {
        incidentRepository.deleteAll(); // cascades incident_call_records
        callRepository.deleteAll();

        testApp = applicationRepository.save(
                Application.builder()
                        .name("IncidentTest App " + UUID.randomUUID())
                        .description("Integration test app")
                        .build());

        testCall = callRepository.save(
                ZadarmaCall.builder()
                        .externalCallId("ext-incident-test-" + UUID.randomUUID())
                        .sipId("921495")
                        .callerNumber("34600000001")
                        .destinationNumber("34921495")
                        .callStartedAt(OffsetDateTime.parse("2026-10-05T10:00:00Z"))
                        .billSeconds(90)
                        .disposition("answered")
                        .build());
    }

    @Test
    void create_withNoCalls_startedAtIsNull() throws Exception {
        var request = new IncidentRequest(
                "Server down", testApp.getId(), "Main server crashed", true, null, null, null);

        var result = mockMvc.perform(post("/api/v1/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt()))
                .andExpect(status().isCreated())
                .andReturn();

        var response = parse(result.getResponse().getContentAsString());

        assertThat(response.data().startedAt()).isNull();
        assertThat(response.data().linkedCallsCount()).isZero();
        assertThat(response.data().applicationName()).isEqualTo(testApp.getName());
        assertThat(response.data().bridgeOpened()).isTrue();
        assertThat(response.data().durationSeconds()).isNull();
    }

    @Test
    void create_withManualStartedAt_storesIt() throws Exception {
        var startedAt = OffsetDateTime.parse("2026-10-05T09:30:00Z");
        var request = new IncidentRequest("DB outage", testApp.getId(), null, false, startedAt, null, null);

        var result = mockMvc.perform(post("/api/v1/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt()))
                .andExpect(status().isCreated())
                .andReturn();

        var response = parse(result.getResponse().getContentAsString());

        assertThat(response.data().startedAt()).isEqualTo(startedAt);
    }

    @Test
    void create_withCalls_startedAtAutoFromCall() throws Exception {
        var request = new IncidentRequest(
                "Network issue", testApp.getId(), null, false, null, null, List.of(testCall.getId()));

        var result = mockMvc.perform(post("/api/v1/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt()))
                .andExpect(status().isCreated())
                .andReturn();

        var response = parse(result.getResponse().getContentAsString());

        assertThat(response.data().startedAt()).isEqualTo(testCall.getCallStartedAt());
        assertThat(response.data().linkedCallsCount()).isEqualTo(1);
    }

    @Test
    void linkCall_populatesStartedAtAutomatically() throws Exception {
        var incidentId = createIncidentWithoutCalls();

        // Before link — startedAt is null
        var before = getIncident(incidentId);
        assertThat(before.startedAt()).isNull();

        // Link the call
        var linked = mockMvc.perform(post("/api/v1/incidents/{id}/calls/{callId}", incidentId, testCall.getId())
                .with(jwt()))
                .andExpect(status().isOk())
                .andReturn();

        var response = parse(linked.getResponse().getContentAsString());
        assertThat(response.data().startedAt()).isEqualTo(testCall.getCallStartedAt());
        assertThat(response.data().linkedCallsCount()).isEqualTo(1);
    }

    @Test
    void unlinkLastCall_setsStartedAtToNull() throws Exception {
        // Create with call → startedAt auto-populated
        var request = new IncidentRequest(
                "Test", testApp.getId(), null, false, null, null, List.of(testCall.getId()));
        var createResult = mockMvc.perform(post("/api/v1/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID incidentId = parse(createResult.getResponse().getContentAsString()).data().id();

        assertThat(getIncident(incidentId).startedAt()).isNotNull();

        // Unlink
        mockMvc.perform(delete("/api/v1/incidents/{id}/calls/{callId}", incidentId, testCall.getId())
                .with(jwt()))
                .andExpect(status().isNoContent());

        assertThat(getIncident(incidentId).startedAt()).isNull();
        assertThat(getIncident(incidentId).linkedCallsCount()).isZero();
    }

    @Test
    void durationSeconds_computedFromStartedAtAndEndedAt() throws Exception {
        var startedAt = OffsetDateTime.parse("2026-10-05T10:00:00Z");
        var endedAt = OffsetDateTime.parse("2026-10-05T11:30:00Z"); // 5400 seconds
        var request = new IncidentRequest("Test", testApp.getId(), null, false, startedAt, endedAt, null);

        var result = mockMvc.perform(post("/api/v1/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt()))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(parse(result.getResponse().getContentAsString()).data().durationSeconds()).isEqualTo(5400L);
    }

    @Test
    void update_replacesCallsAndRecalculatesStartedAt() throws Exception {
        // Create with no calls
        var incidentId = createIncidentWithoutCalls();

        // Update with a call
        var updateRequest = new IncidentRequest(
                "Updated title", testApp.getId(), "desc", false, null, null, List.of(testCall.getId()));
        var updateResult = mockMvc.perform(put("/api/v1/incidents/{id}", incidentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest))
                .with(jwt()))
                .andExpect(status().isOk())
                .andReturn();

        var updated = parse(updateResult.getResponse().getContentAsString()).data();
        assertThat(updated.title()).isEqualTo("Updated title");
        assertThat(updated.startedAt()).isEqualTo(testCall.getCallStartedAt());
        assertThat(updated.linkedCallsCount()).isEqualTo(1);
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/incidents/{id}", UUID.randomUUID()).with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_removesIncident() throws Exception {
        var incidentId = createIncidentWithoutCalls();

        mockMvc.perform(delete("/api/v1/incidents/{id}", incidentId).with(jwt()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/incidents/{id}", incidentId).with(jwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_returns400WhenTitleIsBlank() throws Exception {
        var request = new IncidentRequest("", testApp.getId(), null, false, null, null, null);

        mockMvc.perform(post("/api/v1/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns404WhenApplicationNotFound() throws Exception {
        var request = new IncidentRequest("Test", UUID.randomUUID(), null, false, null, null, null);

        mockMvc.perform(post("/api/v1/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt()))
                .andExpect(status().isNotFound());
    }

    // ── helpers ──────────────────────────────────

    private UUID createIncidentWithoutCalls() throws Exception {
        var request = new IncidentRequest("Test incident", testApp.getId(), null, false, null, null, null);
        var result = mockMvc.perform(post("/api/v1/incidents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt()))
                .andExpect(status().isCreated())
                .andReturn();
        return parse(result.getResponse().getContentAsString()).data().id();
    }

    private IncidentResponse getIncident(UUID id) throws Exception {
        var result = mockMvc.perform(get("/api/v1/incidents/{id}", id).with(jwt()))
                .andExpect(status().isOk())
                .andReturn();
        return parse(result.getResponse().getContentAsString()).data();
    }

    private ApiResponse<IncidentResponse> parse(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<ApiResponse<IncidentResponse>>() {
        });
    }
}
