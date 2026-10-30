package com.nextdigital.oncall.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nextdigital.oncall.entity.ZadarmaCall;

public interface ZadarmaCallRepository extends JpaRepository<ZadarmaCall, UUID> {

    boolean existsByExternalCallId(String externalCallId);

    List<ZadarmaCall> findByCallStartedAtBetweenAndSipId(OffsetDateTime start, OffsetDateTime end, String sipId);

    List<ZadarmaCall> findByCallStartedAtBetween(OffsetDateTime start, OffsetDateTime end);
}
