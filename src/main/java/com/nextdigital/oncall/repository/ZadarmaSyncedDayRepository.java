package com.nextdigital.oncall.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nextdigital.oncall.entity.ZadarmaSyncedDay;
import com.nextdigital.oncall.entity.ZadarmaSyncedDay.ZadarmaSyncedDayId;

public interface ZadarmaSyncedDayRepository extends JpaRepository<ZadarmaSyncedDay, ZadarmaSyncedDayId> {

    @Query("SELECT COUNT(d) FROM ZadarmaSyncedDay d WHERE d.id.sipId = :sipId AND d.id.dayDate BETWEEN :start AND :end")
    long countCoveredDays(@Param("sipId") String sipId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);
}
