package com.nextdigital.oncall.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import com.nextdigital.oncall.dto.AuditAction;
import com.nextdigital.oncall.dto.AuditLogFilter;
import com.nextdigital.oncall.entity.AuditLog;
import com.nextdigital.oncall.exception.ResourceNotFoundException;
import com.nextdigital.oncall.repository.AuditLogRepository;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceImplTest {

    @Mock
    private AuditLogRepository repository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuditLogServiceImpl service;

    @Test
    void search_appliesFilterAndMapsPage() {
        var pageable = PageRequest.of(0, 20);
        var filter = new AuditLogFilter(AuditAction.UPDATE, "people", null, null, null, null, null);
        var entity = AuditLog.builder()
                .id(UUID.randomUUID())
                .occurredAt(OffsetDateTime.now())
                .action("UPDATE")
                .entityType("people")
                .entityId(UUID.randomUUID().toString())
                .actorEmail("alice@example.com")
                .build();
        when(repository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(entity), pageable, 1));

        var result = service.search(filter, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).action()).isEqualTo(AuditAction.UPDATE);
        assertThat(result.getContent().get(0).changes()).isNull();
        assertThat(result.getContent().get(0).actorEmail()).isEqualTo("alice@example.com");
        verify(repository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void auditAction_fromInvalidValueThrows() {
        assertThatThrownBy(() -> AuditAction.from("BOGUS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BOGUS");
    }

    @Test
    void auditAction_fromNullReturnsNull() {
        assertThat(AuditAction.from(null)).isNull();
    }

    @Test
    void findById_returnsMappedResponse() {
        var id = UUID.randomUUID();
        var entity = AuditLog.builder()
                .id(id)
                .occurredAt(OffsetDateTime.now())
                .action("CREATE")
                .entityType("shifts")
                .entityId("shift-id")
                .build();
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        var response = service.findById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.action()).isEqualTo(AuditAction.CREATE);
        assertThat(response.entityType()).isEqualTo("shifts");
    }

    @Test
    void findById_notFoundThrows() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void entityTypes_delegatesToRepository() {
        when(repository.findDistinctEntityTypes()).thenReturn(List.of("people", "shifts"));

        assertThat(service.entityTypes()).containsExactly("people", "shifts");
    }
}
