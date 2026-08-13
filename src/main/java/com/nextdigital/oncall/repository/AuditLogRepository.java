package com.nextdigital.oncall.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nextdigital.oncall.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    /** Única vía de borrado de auditoría: la purga por retención. */
    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.occurredAt < :cutoff")
    int deleteByOccurredAtBefore(@Param("cutoff") OffsetDateTime cutoff);

    /** Valores distintos de entity_type para los desplegables de filtros. */
    @Query("SELECT DISTINCT a.entityType FROM AuditLog a ORDER BY a.entityType")
    List<String> findDistinctEntityTypes();
}
