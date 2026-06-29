package com.nextdigital.oncall.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
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
import com.nextdigital.oncall.dto.BillingReportResponse;
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
class BillingIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        private UUID personAId;
        private UUID personBId;
        private UUID groupId;

        private final OffsetDateTime JUN_01 = OffsetDateTime.parse("2026-06-01T00:00:00Z");
        private final OffsetDateTime JUN_02 = OffsetDateTime.parse("2026-06-02T00:00:00Z");
        private final OffsetDateTime JUN_08 = OffsetDateTime.parse("2026-06-08T00:00:00Z");
        private final OffsetDateTime JUN_08_12 = OffsetDateTime.parse("2026-06-08T12:00:00Z");
        private final OffsetDateTime JUN_15 = OffsetDateTime.parse("2026-06-15T00:00:00Z");
        private final OffsetDateTime JUN_30 = OffsetDateTime.parse("2026-06-30T23:59:59Z");

        @BeforeEach
        void setUp() throws Exception {
                // Create an application (needed for group creation)
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
                var appId = appResponse.data().id();

                // Create Person A (Alice)
                var personAResult = mockMvc.perform(post("/api/v1/people")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new PersonRequest("Alice", "alice@example.com", null, true)))
                                .with(jwt()))
                                .andExpect(status().isCreated())
                                .andReturn();
                var personAResponse = objectMapper.readValue(
                                personAResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<PersonResponse>>() {
                                });
                personAId = personAResponse.data().id();

                // Create Person B (Bob)
                var personBResult = mockMvc.perform(post("/api/v1/people")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new PersonRequest("Bob", "bob@example.com", null, true)))
                                .with(jwt()))
                                .andExpect(status().isCreated())
                                .andReturn();
                var personBResponse = objectMapper.readValue(
                                personBResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<PersonResponse>>() {
                                });
                personBId = personBResponse.data().id();

                // Create an on-call group with price 100/day
                var groupResult = mockMvc.perform(post("/api/v1/on-call-groups")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new OnCallGroupRequest("SRE OnCall", new java.math.BigDecimal("100.00"),
                                                                null, null, null, null, null)))
                                .with(jwt()))
                                .andExpect(status().isCreated())
                                .andReturn();
                var groupResponse = objectMapper.readValue(
                                groupResult.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<OnCallGroupResponse>>() {
                                });
                groupId = groupResponse.data().id();
        }

        @Test
        void calculateBilling_withTwoPeople_returnsCorrectTotals() throws Exception {
                // Alice: 24h shift Jun 1 → Jun 2
                mockMvc.perform(post("/api/v1/shifts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new ShiftRequest(groupId, personAId, JUN_01, JUN_02)))
                                .with(jwt()))
                                .andExpect(status().isCreated());

                // Bob: 12h shift Jun 8 00:00 → Jun 8 12:00
                mockMvc.perform(post("/api/v1/shifts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new ShiftRequest(groupId, personBId, JUN_08, JUN_08_12)))
                                .with(jwt()))
                                .andExpect(status().isCreated());

                // Calculate billing for June
                var result = mockMvc.perform(get("/api/v1/billing")
                                .param("groupId", groupId.toString())
                                .param("from", JUN_01.toString())
                                .param("to", JUN_30.toString())
                                .with(jwt()))
                                .andExpect(status().isOk())
                                .andReturn();

                var response = objectMapper.readValue(
                                result.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<BillingReportResponse>>() {
                                });

                assertThat(response.data()).isNotNull();
                assertThat(response.errors()).isNull();
                assertThat(response.data().groupId()).isEqualTo(groupId);
                assertThat(response.data().groupName()).isEqualTo("SRE OnCall");
                assertThat(response.data().grossPricePerDay()).isEqualByComparingTo(new java.math.BigDecimal("100.00"));

                // Alice: 24h = 1 day × 100 = 100
                // Bob: 12h = 0.5 days × 100 = 50
                // Total: 36h, 150
                assertThat(response.data().details()).hasSize(2);

                var aliceDetail = response.data().details().stream()
                                .filter(d -> d.personId().equals(personAId))
                                .findFirst().orElseThrow();
                assertThat(aliceDetail.personName()).isEqualTo("Alice");
                assertThat(aliceDetail.totalHours()).isEqualByComparingTo(new java.math.BigDecimal("24.0000"));
                assertThat(aliceDetail.totalDays()).isEqualByComparingTo(new java.math.BigDecimal("1.0000"));
                assertThat(aliceDetail.totalBilling()).isEqualByComparingTo(new java.math.BigDecimal("100.00"));

                var bobDetail = response.data().details().stream()
                                .filter(d -> d.personId().equals(personBId))
                                .findFirst().orElseThrow();
                assertThat(bobDetail.personName()).isEqualTo("Bob");
                assertThat(bobDetail.totalHours()).isEqualByComparingTo(new java.math.BigDecimal("12.0000"));
                assertThat(bobDetail.totalDays()).isEqualByComparingTo(new java.math.BigDecimal("0.5000"));
                assertThat(bobDetail.totalBilling()).isEqualByComparingTo(new java.math.BigDecimal("50.00"));

                assertThat(response.data().totalHours()).isEqualByComparingTo(new java.math.BigDecimal("36.0000"));
                assertThat(response.data().totalBilling()).isEqualByComparingTo(new java.math.BigDecimal("150.00"));
        }

        @Test
        void calculateBilling_withPersonFilter_returnsOnlyThatPerson() throws Exception {
                // Alice: 24h shift Jun 1 → Jun 2
                mockMvc.perform(post("/api/v1/shifts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new ShiftRequest(groupId, personAId, JUN_01, JUN_02)))
                                .with(jwt()))
                                .andExpect(status().isCreated());

                // Bob: 12h shift Jun 8 → Jun 8 12:00
                mockMvc.perform(post("/api/v1/shifts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                                new ShiftRequest(groupId, personBId, JUN_08, JUN_08_12)))
                                .with(jwt()))
                                .andExpect(status().isCreated());

                // Filter by Alice only
                var result = mockMvc.perform(get("/api/v1/billing")
                                .param("groupId", groupId.toString())
                                .param("personId", personAId.toString())
                                .param("from", JUN_01.toString())
                                .param("to", JUN_30.toString())
                                .with(jwt()))
                                .andExpect(status().isOk())
                                .andReturn();

                var response = objectMapper.readValue(
                                result.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<BillingReportResponse>>() {
                                });

                assertThat(response.data().details()).hasSize(1);
                assertThat(response.data().details().get(0).personId()).isEqualTo(personAId);
                assertThat(response.data().details().get(0).totalBilling())
                                .isEqualByComparingTo(new java.math.BigDecimal("100.00"));
                assertThat(response.data().totalHours()).isEqualByComparingTo(new java.math.BigDecimal("24.0000"));
                assertThat(response.data().totalBilling()).isEqualByComparingTo(new java.math.BigDecimal("100.00"));
        }

        @Test
        void calculateBilling_withEmptyPeriod_returnsEmptyReport() throws Exception {
                // No shifts in July
                var jul_01 = OffsetDateTime.parse("2026-07-01T00:00:00Z");
                var jul_31 = OffsetDateTime.parse("2026-07-31T23:59:59Z");

                var result = mockMvc.perform(get("/api/v1/billing")
                                .param("groupId", groupId.toString())
                                .param("from", jul_01.toString())
                                .param("to", jul_31.toString())
                                .with(jwt()))
                                .andExpect(status().isOk())
                                .andReturn();

                var response = objectMapper.readValue(
                                result.getResponse().getContentAsString(),
                                new TypeReference<ApiResponse<BillingReportResponse>>() {
                                });

                assertThat(response.data().details()).isEmpty();
                assertThat(response.data().totalHours())
                                .isEqualByComparingTo(
                                                java.math.BigDecimal.ZERO.setScale(4, java.math.RoundingMode.HALF_UP));
                assertThat(response.data().totalBilling())
                                .isEqualByComparingTo(
                                                java.math.BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP));
        }

        @Test
        void calculateBilling_withNonExistentGroup_returns404() throws Exception {
                var fakeId = UUID.randomUUID();

                mockMvc.perform(get("/api/v1/billing")
                                .param("groupId", fakeId.toString())
                                .param("from", JUN_01.toString())
                                .param("to", JUN_30.toString())
                                .with(jwt()))
                                .andExpect(status().isNotFound());
        }

        @Test
        void calculateBilling_withoutGroupId_returns400() throws Exception {
                mockMvc.perform(get("/api/v1/billing")
                                .param("from", JUN_01.toString())
                                .param("to", JUN_30.toString())
                                .with(jwt()))
                                .andExpect(status().isBadRequest());
        }
}
