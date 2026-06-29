package com.nextdigital.oncall.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class ApplicationIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        private ApplicationRequest request;

        @BeforeEach
        void setUp() {
                request = new ApplicationRequest("Monitor App", "Server monitoring application");
        }

        @Test
        void fullCrud() throws Exception {
                // --- CREATE ---
                var createResult = mockMvc.perform(post("/api/v1/applications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(jwt()))
                                .andExpect(status().isCreated())
                                .andReturn();

                var createResponse = objectMapper.readValue(
                                createResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<ApplicationResponse>>() {
                                });

                assertThat(createResponse.data()).isNotNull();
                assertThat(createResponse.data().name()).isEqualTo("Monitor App");
                assertThat(createResponse.data().id()).isNotNull();
                assertThat(createResponse.errors()).isNull();

                var createdId = createResponse.data().id();

                // --- GET BY ID ---
                var getResult = mockMvc.perform(get("/api/v1/applications/{id}", createdId).with(jwt()))
                                .andExpect(status().isOk())
                                .andReturn();

                var getResponse = objectMapper.readValue(
                                getResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<ApplicationResponse>>() {
                                });

                assertThat(getResponse.data().name()).isEqualTo("Monitor App");
                assertThat(getResponse.data().description()).isEqualTo("Server monitoring application");

                // --- GET ALL ---
                var findAllResult = mockMvc.perform(get("/api/v1/applications").with(jwt()))
                                .andExpect(status().isOk())
                                .andReturn();

                var findAllResponse = objectMapper.readValue(
                                findAllResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<java.util.List<ApplicationResponse>>>() {
                                });

                assertThat(findAllResponse.data()).hasSizeGreaterThanOrEqualTo(1);

                // --- UPDATE ---
                var updateRequest = new ApplicationRequest("Updated App", "Updated description");
                mockMvc.perform(put("/api/v1/applications/{id}", createdId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest))
                                .with(jwt()))
                                .andExpect(status().isOk());

                var getUpdatedResult = mockMvc.perform(get("/api/v1/applications/{id}", createdId).with(jwt()))
                                .andExpect(status().isOk())
                                .andReturn();

                var getUpdated = objectMapper.readValue(
                                getUpdatedResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<ApplicationResponse>>() {
                                });

                assertThat(getUpdated.data().name()).isEqualTo("Updated App");
                assertThat(getUpdated.data().description()).isEqualTo("Updated description");

                // --- DELETE ---
                mockMvc.perform(delete("/api/v1/applications/{id}", createdId).with(jwt()))
                                .andExpect(status().isNoContent());

                // --- VERIFY 404 AFTER DELETE ---
                mockMvc.perform(get("/api/v1/applications/{id}", createdId).with(jwt()))
                                .andExpect(status().isNotFound());
        }

        @Test
        void create_returns400WhenNameIsBlank() throws Exception {
                var invalidRequest = new ApplicationRequest("", "Some description");

                var result = mockMvc.perform(post("/api/v1/applications")
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
                assertThat(response.errors().get(0).code()).isEqualTo("VALIDATION_ERROR");
        }

        @Test
        void getById_returns404WhenNotFound() throws Exception {
                var result = mockMvc.perform(get("/api/v1/applications/{id}", UUID.randomUUID()).with(jwt()))
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
