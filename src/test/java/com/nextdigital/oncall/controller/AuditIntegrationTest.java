package com.nextdigital.oncall.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.nextdigital.oncall.config.TestcontainersConfig;
import com.nextdigital.oncall.dto.ApiResponse;
import com.nextdigital.oncall.dto.AuditLogPageResponse;
import com.nextdigital.oncall.dto.OnCallGroupRequest;
import com.nextdigital.oncall.dto.PersonRequest;
import com.nextdigital.oncall.dto.PersonResponse;
import com.nextdigital.oncall.repository.AuditLogRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifica el registro de auditoría extremo a extremo: cada escritura HTTP
 * autenticada debe generar su fila en audit_logs con el actor del JWT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class AuditIntegrationTest {

    private static final String ACTOR_EMAIL = "audit.test@example.com";
    private static final String ACTOR_NAME = "Audit User";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void createUpdateDelete_areRecordedWithJwtActor() throws Exception {
        // --- CREATE ---
        var request = new PersonRequest("Audit User", ACTOR_EMAIL, "+34 600 000 999", true);
        var createResult = mockMvc.perform(post("/api/v1/people")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(j -> j.claim("email", ACTOR_EMAIL).claim("name", ACTOR_NAME))))
                .andExpect(status().isCreated())
                .andReturn();

        var created = objectMapper.readValue(createResult.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<PersonResponse>>() {
                }).data();

        var createLogs = auditLogRepository.findAll().stream()
                .filter(l -> "CREATE".equals(l.getAction())
                        && "people".equals(l.getEntityType())
                        && created.id().toString().equals(l.getEntityId()))
                .toList();

        assertThat(createLogs).hasSize(1);
        var createLog = createLogs.get(0);
        assertThat(createLog.getActorEmail()).isEqualTo(ACTOR_EMAIL);
        assertThat(createLog.getActorName()).isEqualTo(ACTOR_NAME);
        assertThat(createLog.getPersonId()).isNotNull();
        assertThat(createLog.getChanges()).contains("Audit User").contains("\"email\"");
        assertThat(createLog.getHttpMethod()).isEqualTo("POST");
        assertThat(createLog.getRequestPath()).startsWith("/api/v1/people");
        assertThat(createLog.getOccurredAt()).isNotNull();

        // --- UPDATE (diff solo con los campos cambiados) ---
        var updateRequest = new PersonRequest("Audit User Updated", ACTOR_EMAIL, "+34 600 000 999", true);
        mockMvc.perform(put("/api/v1/people/{id}", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest))
                .with(jwt().jwt(j -> j.claim("email", ACTOR_EMAIL).claim("name", ACTOR_NAME))))
                .andExpect(status().isOk());

        var updateLogs = auditLogRepository.findAll().stream()
                .filter(l -> "UPDATE".equals(l.getAction())
                        && "people".equals(l.getEntityType())
                        && created.id().toString().equals(l.getEntityId())
                        && l.getChanges() != null && l.getChanges().contains("\"name\""))
                .toList();

        assertThat(updateLogs).hasSize(1);
        assertThat(updateLogs.get(0).getChanges())
                .contains("Audit User")
                .contains("Audit User Updated")
                .contains("\"old\"")
                .contains("\"new\"");

        // --- SOFT DELETE (UPDATE de is_active) ---
        mockMvc.perform(delete("/api/v1/people/{id}", created.id())
                .with(jwt().jwt(j -> j.claim("email", ACTOR_EMAIL).claim("name", ACTOR_NAME))))
                .andExpect(status().isNoContent());

        var deleteLogs = auditLogRepository.findAll().stream()
                .filter(l -> "UPDATE".equals(l.getAction())
                        && "people".equals(l.getEntityType())
                        && created.id().toString().equals(l.getEntityId())
                        && l.getChanges() != null && l.getChanges().contains("\"isActive\""))
                .toList();

        assertThat(deleteLogs).isNotEmpty();
    }

    @Test
    void groupMembershipChange_isRecordedAsCollectionUpdate() throws Exception {
        var personRequest = new PersonRequest("Member One", "member.audit@example.com", "+34 600 000 998", true);
        var personResult = mockMvc.perform(post("/api/v1/people")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(personRequest))
                .with(jwt().jwt(j -> j.claim("email", ACTOR_EMAIL).claim("name", ACTOR_NAME))))
                .andExpect(status().isCreated())
                .andReturn();
        var person = objectMapper.readValue(personResult.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<PersonResponse>>() {
                }).data();

        var groupRequest = new OnCallGroupRequest("Audit Group", null, null, null, false,
                Set.of(), Set.of(person.id()), null, null, null);
        mockMvc.perform(post("/api/v1/on-call-groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(groupRequest))
                .with(jwt().jwt(j -> j.claim("email", ACTOR_EMAIL).claim("name", ACTOR_NAME))))
                .andExpect(status().isCreated());

        var membershipLogs = auditLogRepository.findAll().stream()
                .filter(l -> "on_call_groups".equals(l.getEntityType())
                        && l.getChanges() != null
                        && l.getChanges().contains("\"people\"")
                        && l.getChanges().contains(person.id().toString()))
                .toList();

        assertThat(membershipLogs).isNotEmpty();
    }

    @Test
    void searchEndpoint_returnsPaginatedFilteredResults() throws Exception {
        // Datos de partida: una creación auditable
        var request = new PersonRequest("Search Me", "search.audit@example.com", "+34 600 000 997", true);
        var createResult = mockMvc.perform(post("/api/v1/people")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(jwt().jwt(j -> j.claim("email", ACTOR_EMAIL).claim("name", ACTOR_NAME))))
                .andExpect(status().isCreated())
                .andReturn();
        var created = objectMapper.readValue(createResult.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<PersonResponse>>() {
                }).data();

        // --- Búsqueda paginada con filtros ---
        var searchResult = mockMvc.perform(get("/api/v1/audit-logs")
                .param("action", "CREATE")
                .param("entityType", "people")
                .param("page", "0")
                .param("size", "10")
                .with(jwt().jwt(j -> j.claim("email", ACTOR_EMAIL).claim("name", ACTOR_NAME))))
                .andExpect(status().isOk())
                .andReturn();

        var pageResponse = objectMapper.readValue(searchResult.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<AuditLogPageResponse>>() {
                });

        assertThat(pageResponse.errors()).isNull();
        assertThat(pageResponse.data()).isNotNull();
        assertThat(pageResponse.data().page()).isNotNull();
        assertThat(pageResponse.data().items()).isNotEmpty();
        assertThat(pageResponse.data().items()).anyMatch(item -> created.id().toString().equals(item.entityId())
                && "CREATE".equals(item.action().name())
                && ACTOR_EMAIL.equals(item.actorEmail()));

        // --- Acción inválida → 400 ---
        mockMvc.perform(get("/api/v1/audit-logs")
                .param("action", "BOGUS")
                .with(jwt().jwt(j -> j.claim("email", ACTOR_EMAIL).claim("name", ACTOR_NAME))))
                .andExpect(status().isBadRequest());

        // --- Detalle por id ---
        var auditId = pageResponse.data().items().stream()
                .filter(i -> created.id().toString().equals(i.entityId()))
                .findFirst()
                .orElseThrow()
                .id();

        mockMvc.perform(get("/api/v1/audit-logs/{id}", auditId)
                .with(jwt().jwt(j -> j.claim("email", ACTOR_EMAIL).claim("name", ACTOR_NAME))))
                .andExpect(status().isOk());

        // --- Tipos de entidad para desplegables ---
        var typesResult = mockMvc.perform(get("/api/v1/audit-logs/entity-types")
                .with(jwt().jwt(j -> j.claim("email", ACTOR_EMAIL).claim("name", ACTOR_NAME))))
                .andExpect(status().isOk())
                .andReturn();

        var typesResponse = objectMapper.readValue(typesResult.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<List<String>>>() {
                });
        assertThat(typesResponse.data()).contains("people");
    }
}
