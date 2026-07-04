package com.nextdigital.oncall.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import com.nextdigital.oncall.dto.PersonRequest;
import com.nextdigital.oncall.dto.PersonResponse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class PersonIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        private PersonRequest request;

        @BeforeEach
        void setUp() {
                request = new PersonRequest("Alice", "alice@example.com", "+34 600 000 000", true);
        }

        @Test
        void fullCrud() throws Exception {
                // --- CREATE ---
                var createResult = mockMvc.perform(post("/api/v1/people")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(jwt()))
                                .andExpect(status().isCreated())
                                .andReturn();

                var createResponse = objectMapper.readValue(
                                createResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<PersonResponse>>() {
                                });

                assertThat(createResponse.data()).isNotNull();
                assertThat(createResponse.data().name()).isEqualTo("Alice");
                assertThat(createResponse.data().email()).isEqualTo("alice@example.com");
                assertThat(createResponse.data().isActive()).isTrue();
                assertThat(createResponse.errors()).isNull();

                var createdId = createResponse.data().id();

                // --- GET BY ID ---
                var getResult = mockMvc.perform(get("/api/v1/people/{id}", createdId).with(jwt()))
                                .andExpect(status().isOk())
                                .andReturn();

                var getResponse = objectMapper.readValue(
                                getResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<PersonResponse>>() {
                                });

                assertThat(getResponse.data().name()).isEqualTo("Alice");

                // --- LIST ACTIVE (default) ---
                var listResult = mockMvc.perform(get("/api/v1/people").with(jwt()))
                                .andExpect(status().isOk())
                                .andReturn();

                var listResponse = objectMapper.readValue(
                                listResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<List<PersonResponse>>>() {
                                });

                assertThat(listResponse.data()).isNotEmpty();

                // --- UPDATE ---
                var updateRequest = new PersonRequest("Alice Updated", "alice.new@example.com", "+34 600 000 001",
                                true);
                mockMvc.perform(put("/api/v1/people/{id}", createdId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest))
                                .with(jwt()))
                                .andExpect(status().isOk());

                var getUpdatedResult = mockMvc.perform(get("/api/v1/people/{id}", createdId).with(jwt()))
                                .andExpect(status().isOk())
                                .andReturn();

                var getUpdated = objectMapper.readValue(
                                getUpdatedResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<PersonResponse>>() {
                                });

                assertThat(getUpdated.data().name()).isEqualTo("Alice Updated");
                assertThat(getUpdated.data().email()).isEqualTo("alice.new@example.com");

                // --- SOFT DELETE ---
                mockMvc.perform(delete("/api/v1/people/{id}", createdId).with(jwt()))
                                .andExpect(status().isNoContent());

                // --- VERIFY NOT IN ACTIVE LIST ---
                var afterDeleteListResult = mockMvc.perform(get("/api/v1/people").with(jwt()))
                                .andExpect(status().isOk())
                                .andReturn();

                var afterDeleteList = objectMapper.readValue(
                                afterDeleteListResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<List<PersonResponse>>>() {
                                });

                assertThat(afterDeleteList.data())
                                .extracting(PersonResponse::id)
                                .doesNotContain(createdId);

                // --- VERIFY VISIBLE WITH includeInactive=true ---
                var withInactiveResult = mockMvc.perform(get("/api/v1/people?includeInactive=true").with(jwt()))
                                .andExpect(status().isOk())
                                .andReturn();

                var withInactive = objectMapper.readValue(
                                withInactiveResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<List<PersonResponse>>>() {
                                });

                assertThat(withInactive.data())
                                .extracting(PersonResponse::id)
                                .contains(createdId);

                var deletedPerson = withInactive.data().stream()
                                .filter(p -> p.id().equals(createdId))
                                .findFirst().get();
                assertThat(deletedPerson.isActive()).isFalse();
        }

        @Test
        void create_returns400WhenEmailIsInvalid() throws Exception {
                var invalidRequest = new PersonRequest("Bob", "invalid-email", null, true);

                var result = mockMvc.perform(post("/api/v1/people")
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
                var result = mockMvc.perform(get("/api/v1/people/{id}", UUID.randomUUID()).with(jwt()))
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
