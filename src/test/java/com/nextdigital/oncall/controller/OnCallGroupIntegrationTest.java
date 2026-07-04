package com.nextdigital.oncall.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.nextdigital.oncall.config.TestcontainersConfig;
import com.nextdigital.oncall.dto.ApiResponse;
import com.nextdigital.oncall.dto.ApplicationRequest;
import com.nextdigital.oncall.dto.ApplicationResponse;
import com.nextdigital.oncall.dto.GenerateShiftsResponse;
import com.nextdigital.oncall.dto.OnCallGroupRequest;
import com.nextdigital.oncall.dto.OnCallGroupResponse;
import com.nextdigital.oncall.dto.PersonRequest;
import com.nextdigital.oncall.dto.PersonResponse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class OnCallGroupIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        private UUID appId;
        private UUID personId;

        @BeforeEach
        void setUp() throws Exception {
                // Create an application to reference
                var appResult = mockMvc.perform(post("/api/v1/applications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new ApplicationRequest("Monitor App", null)))
                                .with(jwt()))
                                .andExpect(status().isCreated())
                                .andReturn();

                var appResponse = objectMapper.readValue(
                                appResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<ApplicationResponse>>() {
                                });
                appId = appResponse.data().id();

                // Create a person to reference
                var personResult = mockMvc.perform(post("/api/v1/people")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new PersonRequest("Alice", "alice@example.com", null, true)))
                                .with(jwt()))
                                .andExpect(status().isCreated())
                                .andReturn();

                var personResponse = objectMapper.readValue(
                                personResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<PersonResponse>>() {
                                });
                personId = personResponse.data().id();
        }

        @Test
        void fullCrud() throws Exception {
                // --- CREATE with applications and people ---
                var createRequest = new OnCallGroupRequest(
                                "Weekend OnCall",
                                new BigDecimal("150.00"),
                                null, null, null,
                                Set.of(appId),
                                Set.of(personId),
                                null, null);

                var createResult = mockMvc.perform(post("/api/v1/on-call-groups")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest))
                                .with(jwt()))
                                .andExpect(status().isCreated())
                                .andReturn();

                var createResponse = objectMapper.readValue(
                                createResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<OnCallGroupResponse>>() {
                                });

                assertThat(createResponse.data()).isNotNull();
                assertThat(createResponse.data().name()).isEqualTo("Weekend OnCall");
                assertThat(createResponse.data().grossPricePerDay()).isEqualByComparingTo(new BigDecimal("150.00"));
                assertThat(createResponse.data().applications()).hasSize(1);
                assertThat(createResponse.data().people()).hasSize(1);
                assertThat(createResponse.errors()).isNull();

                var createdId = createResponse.data().id();

                // --- GET BY ID ---
                var getResult = mockMvc.perform(get("/api/v1/on-call-groups/{id}", createdId).with(jwt()))
                                .andExpect(status().isOk())
                                .andReturn();

                var getResponse = objectMapper.readValue(
                                getResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<OnCallGroupResponse>>() {
                                });

                assertThat(getResponse.data().applications()).hasSize(1);
                assertThat(getResponse.data().people()).hasSize(1);

                // --- LIST ALL ---
                var listResult = mockMvc.perform(get("/api/v1/on-call-groups").with(jwt()))
                                .andExpect(status().isOk())
                                .andReturn();

                var listResponse = objectMapper.readValue(
                                listResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<java.util.List<OnCallGroupResponse>>>() {
                                });

                assertThat(listResponse.data()).isNotEmpty();

                // --- UPDATE with different people ---
                var updateRequest = new OnCallGroupRequest(
                                "Holiday OnCall",
                                new BigDecimal("200.00"),
                                null, null, null,
                                Set.of(appId),
                                Set.<UUID>of(),
                                null, null);

                mockMvc.perform(put("/api/v1/on-call-groups/{id}", createdId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest))
                                .with(jwt()))
                                .andExpect(status().isOk());

                var getUpdatedResult = mockMvc.perform(get("/api/v1/on-call-groups/{id}", createdId).with(jwt()))
                                .andExpect(status().isOk())
                                .andReturn();

                var getUpdated = objectMapper.readValue(
                                getUpdatedResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<OnCallGroupResponse>>() {
                                });

                assertThat(getUpdated.data().name()).isEqualTo("Holiday OnCall");
                assertThat(getUpdated.data().grossPricePerDay()).isEqualByComparingTo(new BigDecimal("200.00"));
                assertThat(getUpdated.data().applications()).hasSize(1);
                assertThat(getUpdated.data().people()).isEmpty();

                // --- DELETE ---
                mockMvc.perform(delete("/api/v1/on-call-groups/{id}", createdId).with(jwt()))
                                .andExpect(status().isNoContent());

                // --- VERIFY 404 AFTER DELETE ---
                mockMvc.perform(get("/api/v1/on-call-groups/{id}", createdId).with(jwt()))
                                .andExpect(status().isNotFound());
        }

        @Test
        void create_returns400WhenNameIsBlank() throws Exception {
                var invalidRequest = new OnCallGroupRequest("", null, null, null, null, null, null, null, null);

                var result = mockMvc.perform(post("/api/v1/on-call-groups")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(invalidRequest))
                                .with(jwt()))
                                .andExpect(status().isBadRequest())
                                .andReturn();

                var response = objectMapper.readValue(
                                result.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<Void>>() {
                                });

                assertThat(response.errors()).isNotEmpty();
        }

        @Test
        void getById_returns404WhenNotFound() throws Exception {
                var result = mockMvc.perform(get("/api/v1/on-call-groups/{id}", UUID.randomUUID()).with(jwt()))
                                .andExpect(status().isNotFound())
                                .andReturn();

                var response = objectMapper.readValue(
                                result.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<Void>>() {
                                });

                assertThat(response.errors()).isNotEmpty();
                assertThat(response.errors().get(0).code()).isEqualTo("NOT_FOUND");
        }

        @Test
        void generateShifts_returns200WithCreatedShifts() throws Exception {
                // Create a full group with people
                var createRequest = new OnCallGroupRequest(
                                "AutoGen Group",
                                new BigDecimal("100.00"),
                                null, null, null,
                                Set.of(appId),
                                Set.of(personId),
                                null, null);

                var createResult = mockMvc.perform(post("/api/v1/on-call-groups")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest))
                                .with(jwt()))
                                .andExpect(status().isCreated())
                                .andReturn();

                var created = objectMapper.readValue(
                                createResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<OnCallGroupResponse>>() {
                                });
                var groupId = created.data().id();

                // Trigger generation
                var genResult = mockMvc.perform(post("/api/v1/on-call-groups/{id}/generate-shifts", groupId)
                                .with(jwt()))
                                .andExpect(status().isOk())
                                .andReturn();

                var genResponse = objectMapper.readValue(
                                genResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<GenerateShiftsResponse>>() {
                                });

                assertThat(genResponse.data()).isNotNull();
                assertThat(genResponse.data().onCallGroupId()).isEqualTo(groupId);
                assertThat(genResponse.data().shiftsCreated()).isEqualTo(4);
                assertThat(genResponse.data().createdShifts()).hasSize(4);
                assertThat(genResponse.data().warnings()).isEmpty();
                assertThat(genResponse.data().coverageStart()).isNotNull();
                assertThat(genResponse.data().coverageEnd()).isNotNull();
                assertThat(genResponse.data().coverageEnd()).isAfter(genResponse.data().coverageStart());
        }

        @Test
        void generateShifts_returns404WhenGroupNotFound() throws Exception {
                mockMvc.perform(post("/api/v1/on-call-groups/{id}/generate-shifts", UUID.randomUUID())
                                .with(jwt()))
                                .andExpect(status().isNotFound());
        }
}
