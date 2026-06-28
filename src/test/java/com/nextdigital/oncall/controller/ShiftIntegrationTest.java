package com.nextdigital.oncall.controller;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.test.web.servlet.MockMvc;

import com.nextdigital.oncall.config.TestcontainersConfig;
import com.nextdigital.oncall.dto.ApiResponse;
import com.nextdigital.oncall.dto.OnCallGroupRequest;
import com.nextdigital.oncall.dto.OnCallGroupResponse;
import com.nextdigital.oncall.dto.PersonRequest;
import com.nextdigital.oncall.dto.PersonResponse;
import com.nextdigital.oncall.dto.ShiftRequest;
import com.nextdigital.oncall.dto.ShiftResponse;
import com.nextdigital.oncall.dto.ApplicationRequest;
import com.nextdigital.oncall.dto.ApplicationResponse;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ShiftIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID appId;
    private UUID personId;
    private UUID groupId;

    private final OffsetDateTime DAY10_00 = OffsetDateTime.parse("2026-07-10T00:00:00Z");
    private final OffsetDateTime DAY12_00 = OffsetDateTime.parse("2026-07-12T00:00:00Z");
    private final OffsetDateTime DAY14_00 = OffsetDateTime.parse("2026-07-14T00:00:00Z");
    private final OffsetDateTime DAY15_12 = OffsetDateTime.parse("2026-07-15T12:00:00Z");
    private final OffsetDateTime DAY15_21 = OffsetDateTime.parse("2026-07-15T21:30:00Z");
    private final OffsetDateTime DAY17_08 = OffsetDateTime.parse("2026-07-17T08:00:00Z");

    @BeforeEach
    void setUp() throws Exception {
        // Create an application
        var appResult = mockMvc.perform(post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ApplicationRequest("Monitor App", null))))
                .andExpect(status().isCreated())
                .andReturn();
        var appResponse = objectMapper.readValue(
                appResult.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<ApplicationResponse>>() {
                });
        appId = appResponse.data().id();

        // Create a person
        var personResult = mockMvc.perform(post("/api/v1/people")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PersonRequest("Alice", "alice@example.com", null, true))))
                .andExpect(status().isCreated())
                .andReturn();
        var personResponse = objectMapper.readValue(
                personResult.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<PersonResponse>>() {
                });
        personId = personResponse.data().id();

        // Create an on-call group
        var groupResult = mockMvc.perform(post("/api/v1/on-call-groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new OnCallGroupRequest("Weekend OnCall", null, null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        var groupResponse = objectMapper.readValue(
                groupResult.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<OnCallGroupResponse>>() {
                });
        groupId = groupResponse.data().id();
    }

    @Test
    void fullCrud() throws Exception {
        // --- CREATE ---
        var createRequest = new ShiftRequest(groupId, personId, DAY10_00, DAY17_08);

        var createResult = mockMvc.perform(post("/api/v1/shifts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        var createResponse = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<ShiftResponse>>() {
                });

        assertThat(createResponse.data()).isNotNull();
        assertThat(createResponse.data().personName()).isEqualTo("Alice");
        assertThat(createResponse.data().onCallGroupName()).isEqualTo("Weekend OnCall");
        assertThat(createResponse.data().startTime()).isEqualTo(DAY10_00);
        assertThat(createResponse.data().endTime()).isEqualTo(DAY17_08);
        assertThat(createResponse.errors()).isNull();

        var createdId = createResponse.data().id();

        // --- GET BY ID ---
        var getResult = mockMvc.perform(get("/api/v1/shifts/{id}", createdId))
                .andExpect(status().isOk())
                .andReturn();

        var getResponse = objectMapper.readValue(
                getResult.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<ShiftResponse>>() {
                });

        assertThat(getResponse.data().personName()).isEqualTo("Alice");
        assertThat(getResponse.data().onCallGroupName()).isEqualTo("Weekend OnCall");

        // --- LIST ALL ---
        var listResult = mockMvc.perform(get("/api/v1/shifts"))
                .andExpect(status().isOk())
                .andReturn();

        var listResponse = objectMapper.readValue(
                listResult.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<List<ShiftResponse>>>() {
                });

        assertThat(listResponse.data()).isNotEmpty();

        // --- LIST WITH FILTERS ---
        var filteredResult = mockMvc.perform(get("/api/v1/shifts")
                .param("personId", personId.toString())
                .param("from", DAY10_00.toString())
                .param("to", DAY17_08.toString()))
                .andExpect(status().isOk())
                .andReturn();

        var filteredResponse = objectMapper.readValue(
                filteredResult.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<List<ShiftResponse>>>() {
                });

        assertThat(filteredResponse.data()).hasSize(1);

        // --- UPDATE ---
        var updateRequest = new ShiftRequest(groupId, personId, DAY10_00, DAY14_00);

        mockMvc.perform(put("/api/v1/shifts/{id}", createdId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        var getUpdatedResult = mockMvc.perform(get("/api/v1/shifts/{id}", createdId))
                .andExpect(status().isOk())
                .andReturn();

        var getUpdated = objectMapper.readValue(
                getUpdatedResult.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<ShiftResponse>>() {
                });

        assertThat(getUpdated.data().endTime()).isEqualTo(DAY14_00);

        // --- DELETE ---
        mockMvc.perform(delete("/api/v1/shifts/{id}", createdId))
                .andExpect(status().isNoContent());

        // --- VERIFY 404 AFTER DELETE ---
        mockMvc.perform(get("/api/v1/shifts/{id}", createdId))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_returns409WhenSamePersonOverlaps() throws Exception {
        // Create first shift
        var firstRequest = new ShiftRequest(groupId, personId, DAY10_00, DAY14_00);
        mockMvc.perform(post("/api/v1/shifts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated());

        // Try to create overlapping shift for same person
        var overlapRequest = new ShiftRequest(groupId, personId, DAY12_00, DAY17_08);
        var result = mockMvc.perform(post("/api/v1/shifts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(overlapRequest)))
                .andExpect(status().isConflict())
                .andReturn();

        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<Void>>() {
                });

        assertThat(response.errors()).isNotEmpty();
        assertThat(response.errors().get(0).code()).isEqualTo("SHIFT_OVERLAP");
    }

    @Test
    void create_splitsShiftWhenDifferentPersonOverlaps() throws Exception {
        // Create Person B (Bob)
        var bobResult = mockMvc.perform(post("/api/v1/people")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new PersonRequest("Bob", "bob@example.com", null, true))))
                .andExpect(status().isCreated())
                .andReturn();
        var bobResponse = objectMapper.readValue(
                bobResult.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<PersonResponse>>() {
                });
        var bobId = bobResponse.data().id();

        // Alice has a shift DAY10_00 → DAY17_08
        mockMvc.perform(post("/api/v1/shifts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ShiftRequest(groupId, personId, DAY10_00, DAY17_08))))
                .andExpect(status().isCreated());

        // Bob takes DAY15_12 → DAY15_21 → should split Alice's shift
        mockMvc.perform(post("/api/v1/shifts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ShiftRequest(groupId, bobId, DAY15_12, DAY15_21))))
                .andExpect(status().isCreated());

        // Verify Alice now has 2 shifts (before and after Bob's)
        var aliceShiftsResult = mockMvc.perform(get("/api/v1/shifts")
                .param("personId", personId.toString())
                .param("from", DAY10_00.toString())
                .param("to", DAY17_08.toString()))
                .andExpect(status().isOk())
                .andReturn();

        var aliceShifts = objectMapper.readValue(
                aliceShiftsResult.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<List<ShiftResponse>>>() {
                });

        // Alice should have 2 shifts: DAY10→DAY15_12 and DAY15_21→DAY17_08
        assertThat(aliceShifts.data()).hasSize(2);

        var firstPart = aliceShifts.data().get(0);
        var secondPart = aliceShifts.data().get(1);
        assertThat(firstPart.endTime()).isEqualTo(DAY15_12);
        assertThat(secondPart.startTime()).isEqualTo(DAY15_21);
    }

    @Test
    void create_returns400WhenStartAfterEnd() throws Exception {
        var invalidRequest = new ShiftRequest(groupId, personId, DAY17_08, DAY10_00);

        var result = mockMvc.perform(post("/api/v1/shifts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andReturn();

        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<Void>>() {
                });

        assertThat(response.errors()).isNotEmpty();
        assertThat(response.errors().get(0).code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void update_returns400WhenAdjacentShiftBecomesInvalid() throws Exception {
        // Create first shift: DAY10_00 → DAY14_00
        var createResult = mockMvc.perform(post("/api/v1/shifts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ShiftRequest(groupId, personId, DAY10_00, DAY14_00))))
                .andExpect(status().isCreated())
                .andReturn();

        var createResponse = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<ShiftResponse>>() {
                });
        var shift1Id = createResponse.data().id();

        // Create second shift: DAY14_00 → DAY17_08 (same person, adjacent)
        mockMvc.perform(post("/api/v1/shifts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ShiftRequest(groupId, personId, DAY14_00, DAY17_08))))
                .andExpect(status().isCreated());

        // Try to extend shift1 to DAY17_08 → would make shift2 invalid (startTime =
        // endTime)
        mockMvc.perform(put("/api/v1/shifts/{id}", shift1Id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ShiftRequest(groupId, personId, DAY10_00, DAY17_08))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_returns404WhenNotFound() throws Exception {
        var result = mockMvc.perform(get("/api/v1/shifts/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andReturn();

        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<Void>>() {
                });

        assertThat(response.errors()).isNotEmpty();
        assertThat(response.errors().get(0).code()).isEqualTo("NOT_FOUND");
    }
}
