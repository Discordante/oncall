package com.nextdigital.oncall.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.nextdigital.oncall.entity.ZadarmaCall;

public interface ZadarmaCallRepository extends JpaRepository<ZadarmaCall, UUID> {

    boolean existsByExternalCallId(String externalCallId);

    @EntityGraph(attributePaths = { "onCallGroup", "incidents", "incidents.application" })
    List<ZadarmaCall> findByCallStartedAtBetweenAndSipId(OffsetDateTime start, OffsetDateTime end, String sipId);

    @EntityGraph(attributePaths = { "onCallGroup", "incidents", "incidents.application" })
    List<ZadarmaCall> findByCallStartedAtBetween(OffsetDateTime start, OffsetDateTime end);
}
