package com.nextdigital.oncall.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.nextdigital.oncall.dto.AuditLogFilter;
import com.nextdigital.oncall.dto.AuditLogResponse;

public interface AuditLogService {

    /** Búsqueda paginada con filtros combinables para la pantalla de auditoría. */
    Page<AuditLogResponse> search(AuditLogFilter filter, Pageable pageable);

    /** Detalle de un registro de auditoría (con su diff completo). */
    AuditLogResponse findById(UUID id);

    /**
     * Tipos de entidad presentes en la auditoría (para desplegables de filtros).
     */
    List<String> entityTypes();
}
