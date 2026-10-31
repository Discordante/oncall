package com.nextdigital.oncall.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nextdigital.oncall.entity.ZadarmaCall;

public interface ZadarmaCallRepository extends JpaRepository<ZadarmaCall, UUID> {

    boolean existsByExternalCallId(String externalCallId);

    /** Newest calls first. */
    @EntityGraph(attributePaths = { "onCallGroup", "incidents", "incidents.application" })
    @Query("""
            select c from ZadarmaCall c
            where c.callStartedAt between :start and :end
              and c.sipId = :sipId
            order by c.callStartedAt desc
            """)
    List<ZadarmaCall> findByCallStartedAtBetweenAndSipId(@Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end, @Param("sipId") String sipId);

    /** Newest calls first. */
    @EntityGraph(attributePaths = { "onCallGroup", "incidents", "incidents.application" })
    @Query("""
            select c from ZadarmaCall c
            where c.callStartedAt between :start and :end
            order by c.callStartedAt desc
            """)
    List<ZadarmaCall> findByCallStartedAtBetween(@Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);
}
