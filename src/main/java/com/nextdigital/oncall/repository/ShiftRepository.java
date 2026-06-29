package com.nextdigital.oncall.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nextdigital.oncall.entity.Shift;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

        List<Shift> findByPersonIdAndStartTimeBetweenOrderByStartTimeAsc(UUID personId, OffsetDateTime from,
                        OffsetDateTime to);

        List<Shift> findByOnCallGroupId(UUID groupId);

        @Query("SELECT s FROM Shift s WHERE s.person.id = :personId " +
                        "AND s.startTime < :endTime AND s.endTime > :startTime")
        List<Shift> findOverlappingByPerson(
                        @Param("personId") UUID personId,
                        @Param("startTime") OffsetDateTime startTime,
                        @Param("endTime") OffsetDateTime endTime);

        @Query("SELECT s FROM Shift s WHERE s.person.id <> :personId " +
                        "AND s.startTime < :endTime AND s.endTime > :startTime")
        List<Shift> findOverlappingByOtherPersons(
                        @Param("personId") UUID personId,
                        @Param("startTime") OffsetDateTime startTime,
                        @Param("endTime") OffsetDateTime endTime);

        @Query("SELECT s FROM Shift s WHERE s.person.id = :personId " +
                        "AND s.id <> :shiftId AND s.startTime < :endTime AND s.endTime > :startTime")
        List<Shift> findOverlappingByPersonExcludingSelf(
                        @Param("personId") UUID personId,
                        @Param("shiftId") UUID shiftId,
                        @Param("startTime") OffsetDateTime startTime,
                        @Param("endTime") OffsetDateTime endTime);

        @Query("SELECT s FROM Shift s WHERE s.person.id <> :personId " +
                        "AND s.id <> :shiftId AND s.startTime < :endTime AND s.endTime > :startTime")
        List<Shift> findOverlappingByOtherPersonsExcludingSelf(
                        @Param("personId") UUID personId,
                        @Param("shiftId") UUID shiftId,
                        @Param("startTime") OffsetDateTime startTime,
                        @Param("endTime") OffsetDateTime endTime);

        List<Shift> findByPersonIdOrderByStartTimeAsc(UUID personId);

        List<Shift> findByOnCallGroupIdAndStartTimeBetweenOrderByStartTimeAsc(
                        UUID onCallGroupId, OffsetDateTime startTime, OffsetDateTime endTime);

        List<Shift> findByOnCallGroupIdAndPersonIdAndStartTimeBetweenOrderByStartTimeAsc(
                        UUID onCallGroupId, UUID personId, OffsetDateTime startTime, OffsetDateTime endTime);

        @Query("SELECT s FROM Shift s JOIN FETCH s.onCallGroup JOIN FETCH s.person " +
                        "WHERE s.startTime <= :now AND s.endTime > :now")
        List<Shift> findCurrentlyActive(@Param("now") OffsetDateTime now);

        @Query("SELECT s FROM Shift s JOIN FETCH s.onCallGroup JOIN FETCH s.person " +
                        "WHERE s.endTime > :now")
        List<Shift> findAllActiveAndFuture(@Param("now") OffsetDateTime now);

        @Query("SELECT s FROM Shift s JOIN FETCH s.onCallGroup JOIN FETCH s.person " +
                        "WHERE s.onCallGroup.id = :groupId " +
                        "AND s.startTime >= :after " +
                        "ORDER BY s.startTime ASC")
        List<Shift> findNextShiftsForGroup(
                        @Param("groupId") UUID groupId,
                        @Param("after") OffsetDateTime after);

        List<Shift> findByOnCallGroupIdAndEndTimeGreaterThanOrderByStartTimeAsc(
                        UUID onCallGroupId, OffsetDateTime now);

        List<Shift> findByRedirectStatus(String redirectStatus);
}
