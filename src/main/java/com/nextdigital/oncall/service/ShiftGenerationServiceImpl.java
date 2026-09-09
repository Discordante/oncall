package com.nextdigital.oncall.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextdigital.oncall.dto.GenerateShiftsResponse;
import com.nextdigital.oncall.dto.ShiftRequest;
import com.nextdigital.oncall.dto.ShiftResponse;
import com.nextdigital.oncall.entity.Person;
import com.nextdigital.oncall.entity.Shift;
import com.nextdigital.oncall.exception.ResourceNotFoundException;
import com.nextdigital.oncall.repository.OnCallGroupRepository;
import com.nextdigital.oncall.repository.ShiftRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftGenerationServiceImpl implements ShiftGenerationService {

    /**
     * Hora local (en la zona configurada) a la que cambian las guardias generadas
     */
    private static final LocalTime SHIFT_CHANGE_TIME = LocalTime.of(8, 0);

    private final ShiftService shiftService;
    private final ShiftRepository shiftRepository;
    private final OnCallGroupRepository onCallGroupRepository;

    @Value("${oncall.shift-generation.margin-weeks:2}")
    private int marginWeeks;

    @Value("${oncall.shift-generation.coverage-weeks:4}")
    private int coverageWeeks;

    @Value("${oncall.shift-generation.timezone:Europe/Madrid}")
    private String timezone;

    @Value("${oncall.shift-generation.lookback-days:90}")
    private int lookbackDays;

    @Override
    @Transactional
    public synchronized GenerateShiftsResponse generateForGroup(UUID onCallGroupId) {
        var zoneId = ZoneId.of(timezone);
        var now = OffsetDateTime.now(zoneId);

        var group = onCallGroupRepository.findById(onCallGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("OnCallGroup", onCallGroupId));

        var eligiblePeople = group.getPeople().stream()
                .filter(Person::getIsActive)
                .toList();

        if (eligiblePeople.isEmpty()) {
            return new GenerateShiftsResponse(
                    group.getId(), group.getName(), null, null,
                    0, List.of(),
                    List.of("El grupo no tiene personas activas asignadas"));
        }

        var farthestShift = shiftRepository
                .findTopByOnCallGroupIdOrderByEndTimeDesc(onCallGroupId);

        OffsetDateTime from;
        if (farthestShift.isPresent()) {
            from = farthestShift.get().getEndTime();
            if (from.isBefore(now)) {
                from = now;
            }
        } else {
            from = now.plusWeeks(marginWeeks);
        }

        var coverageStart = alignToNextMonday(from, zoneId);
        var coverageEnd = coverageStart.plusWeeks(coverageWeeks);

        var weekBlocks = buildWeekBlocks(coverageStart, coverageEnd);

        var lookbackStart = coverageStart.minusDays(lookbackDays);
        var allRelevantShifts = shiftRepository
                .findByOnCallGroupIdAndEndTimeGreaterThanOrderByStartTimeAsc(
                        onCallGroupId, lookbackStart);

        var guardTime = calculateGuardTime(eligiblePeople, allRelevantShifts, lookbackStart);
        var lastShiftEnd = calculateLastShiftEnd(eligiblePeople, allRelevantShifts);

        var createdShifts = new ArrayList<ShiftResponse>();
        var runningGuardTime = new HashMap<>(guardTime);
        var runningLastShiftEnd = new HashMap<>(lastShiftEnd);

        for (var block : weekBlocks) {
            var bestPerson = pickLeastBurdened(eligiblePeople, runningGuardTime, runningLastShiftEnd);

            var request = new ShiftRequest(
                    onCallGroupId, bestPerson.getId(),
                    block.start, block.end);

            try {
                var created = shiftService.create(request);
                createdShifts.add(created);

                var blockDuration = Duration.between(block.start, block.end);
                runningGuardTime.merge(bestPerson.getId(), blockDuration, Duration::plus);
                runningLastShiftEnd.put(bestPerson.getId(), block.end);
            } catch (Exception e) {
                log.warn("Failed to create auto-generated shift for group '{}' person '{}' [{} - {}]: {}",
                        group.getName(), bestPerson.getName(), block.start, block.end, e.getMessage());
            }
        }

        log.info("Shift generation for group '{}': {} shifts created (window {} - {})",
                group.getName(), createdShifts.size(), coverageStart, coverageEnd);

        return new GenerateShiftsResponse(
                group.getId(), group.getName(),
                coverageStart, coverageEnd,
                createdShifts.size(), createdShifts,
                List.of());
    }

    @Override
    @Transactional
    public synchronized List<GenerateShiftsResponse> generateForAllGroups() {
        var zoneId = ZoneId.of(timezone);
        var now = OffsetDateTime.now(zoneId);
        var marginThreshold = now.plusWeeks(marginWeeks);

        var groups = onCallGroupRepository.findAll();
        var results = new ArrayList<GenerateShiftsResponse>();
        int skipped = 0;

        for (var group : groups) {
            var farthestShift = shiftRepository
                    .findTopByOnCallGroupIdOrderByEndTimeDesc(group.getId());

            boolean shouldGenerate;
            if (farthestShift.isEmpty()) {
                shouldGenerate = true;
            } else {
                shouldGenerate = !farthestShift.get().getEndTime().isAfter(marginThreshold);
            }

            if (shouldGenerate) {
                results.add(generateForGroup(group.getId()));
            } else {
                skipped++;
            }
        }

        log.info("Shift generation complete: {} groups processed, {} generated, {} skipped (coverage sufficient)",
                groups.size(), results.size(), skipped);
        return results;
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    private OffsetDateTime alignToNextMonday(OffsetDateTime from, ZoneId zoneId) {
        var zdt = from.atZoneSameInstant(zoneId);
        if (zdt.getDayOfWeek() == DayOfWeek.MONDAY
                && zdt.toLocalTime().equals(SHIFT_CHANGE_TIME)) {
            return from;
        }
        var nextMonday = zdt.with(DayOfWeek.MONDAY)
                .with(SHIFT_CHANGE_TIME)
                .plusWeeks(1);
        return nextMonday.toOffsetDateTime();
    }

    private List<WeekBlock> buildWeekBlocks(OffsetDateTime start, OffsetDateTime end) {
        var blocks = new ArrayList<WeekBlock>();
        var current = start;
        while (current.isBefore(end)) {
            var weekEnd = current.plusWeeks(1);
            if (weekEnd.isAfter(end)) {
                weekEnd = end;
            }
            blocks.add(new WeekBlock(current, weekEnd));
            current = weekEnd;
        }
        return blocks;
    }

    private Map<UUID, Duration> calculateGuardTime(
            List<Person> eligiblePeople,
            List<Shift> shifts,
            OffsetDateTime lookbackStart) {

        var map = new HashMap<UUID, Duration>();
        for (var person : eligiblePeople) {
            var total = Duration.ZERO;
            for (var shift : shifts) {
                if (shift.getPerson().getId().equals(person.getId())
                        && shift.getEndTime().isAfter(lookbackStart)) {
                    total = total.plus(Duration.between(
                            shift.getStartTime(), shift.getEndTime()));
                }
            }
            map.put(person.getId(), total);
        }
        return map;
    }

    private Map<UUID, OffsetDateTime> calculateLastShiftEnd(
            List<Person> eligiblePeople,
            List<Shift> shifts) {

        var map = new HashMap<UUID, OffsetDateTime>();
        for (var person : eligiblePeople) {
            var last = shifts.stream()
                    .filter(s -> s.getPerson().getId().equals(person.getId()))
                    .map(Shift::getEndTime)
                    .max(OffsetDateTime::compareTo)
                    .orElse(OffsetDateTime.MIN);
            map.put(person.getId(), last);
        }
        return map;
    }

    private Person pickLeastBurdened(
            List<Person> eligiblePeople,
            Map<UUID, Duration> guardTime,
            Map<UUID, OffsetDateTime> lastShiftEnd) {

        return eligiblePeople.stream()
                .min(Comparator
                        .<Person, Duration>comparing(
                                p -> guardTime.getOrDefault(p.getId(), Duration.ZERO))
                        .thenComparing(p -> lastShiftEnd.getOrDefault(p.getId(),
                                OffsetDateTime.MIN)))
                .orElseThrow(() -> new IllegalStateException(
                        "No eligible people to assign"));
    }

    private record WeekBlock(OffsetDateTime start, OffsetDateTime end) {
    }
}
