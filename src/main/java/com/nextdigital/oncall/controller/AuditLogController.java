package com.nextdigital.oncall.controller;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nextdigital.oncall.dto.ApiResponse;
import com.nextdigital.oncall.dto.AuditAction;
import com.nextdigital.oncall.dto.AuditLogFilter;
import com.nextdigital.oncall.dto.AuditLogPageResponse;
import com.nextdigital.oncall.dto.AuditLogResponse;
import com.nextdigital.oncall.service.AuditLogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Auditoría", description = "Consulta del registro de auditoría (solo lectura)")
public class AuditLogController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogService auditLogService;

    @GetMapping
    @Operation(summary = "Listar registros de auditoría", description = """
            Consulta paginada del registro de auditoría con filtros combinables:
            action (CREATE|UPDATE|DELETE), entityType, entityId, personId, rango de
            fechas (from/to, ISO 8601) y búsqueda libre (q) sobre actor, entidad y ruta.
            Ordenado por fecha descendente.
            """)
    public ResponseEntity<ApiResponse<AuditLogPageResponse>> search(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) UUID personId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var filter = new AuditLogFilter(AuditAction.from(action), entityType, entityId, personId, from, to, q);
        var pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Order.desc("occurredAt"), Order.desc("id")));

        var result = auditLogService.search(filter, pageable);
        var meta = new AuditLogPageResponse.PageMeta(
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());

        return ResponseEntity.ok(ApiResponse.success(new AuditLogPageResponse(result.getContent(), meta)));
    }

    @GetMapping("/entity-types")
    @Operation(summary = "Tipos de entidad auditados", description = """
            Valores distintos de entityType presentes en la auditoría, para los
            desplegables de filtros de la pantalla.
            """)
    public ResponseEntity<ApiResponse<List<String>>> entityTypes() {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.entityTypes()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un registro de auditoría")
    public ResponseEntity<ApiResponse<AuditLogResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.findById(id)));
    }
}
