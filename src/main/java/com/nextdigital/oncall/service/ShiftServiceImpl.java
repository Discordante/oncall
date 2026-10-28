package com.nextdigital.oncall.service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextdigital.oncall.dto.ShiftRequest;
import com.nextdigital.oncall.dto.ShiftResponse;
import com.nextdigital.oncall.entity.OnCallGroup;
import com.nextdigital.oncall.entity.Person;
import com.nextdigital.oncall.entity.Shift;
import com.nextdigital.oncall.exception.ResourceNotFoundException;
import com.nextdigital.oncall.exception.ShiftOverlapException;
import com.nextdigital.oncall.mapper.ShiftMapper;
import com.nextdigital.oncall.repository.OnCallGroupRepository;
import com.nextdigital.oncall.repository.PersonRepository;
import com.nextdigital.oncall.repository.ShiftRepository;
import com.nextdigital.oncall.telegram.event.ShiftCreatedEvent;
import com.nextdigital.oncall.telegram.event.ShiftDeletedEvent;
import com.nextdigital.oncall.telegram.event.ShiftUpdatedEvent;
import com.nextdigital.oncall.zadarma.scheduler.CallRedirectionScheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftServiceImpl implements ShiftService {

    private final ShiftRepository repository;
    private final OnCallGroupRepository onCallGroupRepository;
    private final PersonRepository personRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CallRedirectionScheduler redirectionScheduler;

    @Override
    @Transactional(readOnly = true)
    public List<ShiftResponse> findAll(UUID personId, UUID onCallGroupId,
            OffsetDateTime from, OffsetDateTime to) {
        List<Shift> shifts;

        if (personId != null && from != null && to != null) {
            shifts = repository.findByPersonIdAndStartTimeBetweenOrderByStartTimeAsc(personId, from, to);
        } else if (personId != null) {
            shifts = repository.findByPersonIdOrderByStartTimeAsc(personId);
        } else if (onCallGroupId != null) {
            shifts = repository.findByOnCallGroupId(onCallGroupId);
        } else {
            shifts = repository.findAll();
        }

        // Apply additional in-memory filtering for partial date range
        if (from != null && personId == null) {
            shifts = shifts.stream().filter(s -> s.getEndTime().isAfter(from)).toList();
        }
        if (to != null && personId == null) {
            shifts = shifts.stream().filter(s -> s.getStartTime().isBefore(to)).toList();
        }
        if (from != null && personId != null) {
            // Already filtered by the query, no-op
        }
        if (to != null && personId != null) {
            shifts = shifts.stream().filter(s -> s.getStartTime().isBefore(to)).toList();
        }

        return shifts.stream()
                .map(ShiftMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ShiftResponse findById(UUID id) {
        return ShiftMapper.toResponse(findEntity(id));
    }

    @Override
    @Transactional
    public ShiftResponse create(ShiftRequest request) {
        validateTimeRange(request.startTime(), request.endTime());

        var group = findGroup(request.onCallGroupId());
        var person = findPerson(request.personId());

        // 1. Validate same-person overlap → 409
        var samePersonOverlaps = repository.findOverlappingByPerson(
                request.personId(), request.startTime(), request.endTime());
        if (!samePersonOverlaps.isEmpty()) {
            throw new ShiftOverlapException(
                    "La persona ya tiene un turno en el rango especificado");
        }

        // 2. Split other persons' shifts that overlap with this range
        splitOtherPersonShifts(request.onCallGroupId(), request.personId(), request.startTime(), request.endTime());

        // 3. Create the new shift
        var entity = ShiftMapper.toEntity(request, group, person);
        var saved = repository.save(entity);
        log.info("Shift created: {} for person {} in group {}",
                saved.getId(), request.personId(), request.onCallGroupId());

        // 4. Schedule Zadarma call redirection transitions
        redirectionScheduler.scheduleShiftTransitions(saved);

        // 5. Publish event for Telegram notification
        eventPublisher.publishEvent(new ShiftCreatedEvent(
                saved.getId(), saved.getOnCallGroup().getId(), saved.getPerson().getId(),
                saved.getPerson().getName(), saved.getOnCallGroup().getName(),
                saved.getStartTime(), saved.getEndTime()));

        return ShiftMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ShiftResponse update(UUID id, ShiftRequest request) {
        validateTimeRange(request.startTime(), request.endTime());

        var entity = findEntity(id);
        var group = findGroup(request.onCallGroupId());
        var person = findPerson(request.personId());

        var oldStartTime = entity.getStartTime();
        var oldEndTime = entity.getEndTime();

        // 1. Split other persons' shifts that overlap with the new range
        splitOtherPersonShiftsExcludingSelf(
                request.onCallGroupId(), request.personId(), id, request.startTime(), request.endTime());

        // 2. Adjust adjacent shifts for the same person
        adjustAdjacentShifts(id, request.personId(),
                oldStartTime, oldEndTime,
                request.startTime(), request.endTime());

        // 3. Update entity
        ShiftMapper.updateEntity(request, entity, group, person);
        var saved = repository.save(entity);
        log.info("Shift updated: {} for person {}",
                saved.getId(), request.personId());

        // 4. Reschedule Zadarma call redirection transitions
        redirectionScheduler.cancelShiftTransitions(saved.getId());
        redirectionScheduler.scheduleShiftTransitions(saved);

        // 5. Publish event for Telegram notification
        eventPublisher.publishEvent(new ShiftUpdatedEvent(
                saved.getId(), saved.getOnCallGroup().getId(), saved.getPerson().getId(),
                saved.getPerson().getName(), saved.getOnCallGroup().getName(),
                oldStartTime, oldEndTime,
                saved.getStartTime(), saved.getEndTime()));

        return ShiftMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        var entity = findEntity(id);

        var groupId = entity.getOnCallGroup().getId();
        var personId = entity.getPerson().getId();
        var personName = entity.getPerson().getName();
        var groupName = entity.getOnCallGroup().getName();
        var startTime = entity.getStartTime();
        var endTime = entity.getEndTime();

        var now = java.time.OffsetDateTime.now();
        boolean wasActive = !startTime.isAfter(now) && endTime.isAfter(now);

        repository.delete(entity);
        log.info("Shift deleted: {}", id);

        // Cancel any scheduled Zadarma transitions
        redirectionScheduler.cancelShiftTransitions(id);
        if (wasActive) {
            redirectionScheduler.handleActiveShiftRemoved(groupId);
        }

        eventPublisher.publishEvent(new ShiftDeletedEvent(
                id, groupId, personId, personName, groupName,
                startTime, endTime));
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    private void validateTimeRange(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null) {
            return;
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException(
                    "La fecha de inicio debe ser anterior a la fecha de fin");
        }
    }

    /**
     * Split shifts belonging to OTHER persons that overlap with [newStart, newEnd].
     * Used during create().
     */
    private void splitOtherPersonShifts(UUID groupId, UUID newPersonId,
            OffsetDateTime newStart,
            OffsetDateTime newEnd) {
        var overlapping = repository.findOverlappingByOtherPersons(groupId, newPersonId, newStart, newEnd);
        for (var existing : overlapping) {
            splitShift(existing, newStart, newEnd);
        }
    }

    /**
     * Split shifts belonging to OTHER persons that overlap with [newStart, newEnd],
     * excluding a specific shift ID. Used during update().
     */
    private void splitOtherPersonShiftsExcludingSelf(UUID groupId, UUID newPersonId, UUID shiftId,
            OffsetDateTime newStart,
            OffsetDateTime newEnd) {
        var overlapping = repository.findOverlappingByOtherPersonsExcludingSelf(
                groupId, newPersonId, shiftId, newStart, newEnd);
        for (var existing : overlapping) {
            splitShift(existing, newStart, newEnd);
        }
    }

    /**
     * Split a single shift given a range [splitStart, splitEnd] that overlaps it.
     *
     * Cases:
     * - Total overlap: [splitStart <= existing.start AND splitEnd >= existing.end]
     * → delete existing
     * - Leading cut: splitStart > existing.start → trim existing.endTime =
     * splitStart
     * - Trailing cut: splitEnd < existing.end → create new shift from splitEnd to
     * existing.end
     * - Middle cut: both leading and trailing → trim existing + create new trailing
     * shift
     */
    private void splitShift(Shift existing, OffsetDateTime splitStart, OffsetDateTime splitEnd) {
        var existingStart = existing.getStartTime();
        var existingEnd = existing.getEndTime();

        boolean hasLeading = existingStart.isBefore(splitStart);
        boolean hasTrailing = existingEnd.isAfter(splitEnd);

        if (hasLeading && hasTrailing) {
            // Middle cut: trim existing end, create trailing shift
            existing.setEndTime(splitStart);
            repository.save(existing);

            var trailingShift = Shift.builder()
                    .onCallGroup(existing.getOnCallGroup())
                    .person(existing.getPerson())
                    .startTime(splitEnd)
                    .endTime(existingEnd)
                    .build();
            repository.save(trailingShift);
            log.info("Shift split: {} → trailing shift created for person {}",
                    existing.getId(), existing.getPerson().getId());

        } else if (hasLeading) {
            // Leading cut: only trim existing end
            existing.setEndTime(splitStart);
            repository.save(existing);
            log.info("Shift trimmed (end): {} for person {}",
                    existing.getId(), existing.getPerson().getId());

        } else if (hasTrailing) {
            // Trailing cut: only create trailing shift, delete existing
            repository.delete(existing);

            var trailingShift = Shift.builder()
                    .onCallGroup(existing.getOnCallGroup())
                    .person(existing.getPerson())
                    .startTime(splitEnd)
                    .endTime(existingEnd)
                    .build();
            repository.save(trailingShift);
            log.info("Shift trimmed (start): {} → new trailing shift for person {}",
                    existing.getId(), existing.getPerson().getId());

        } else {
            // Total overlap: delete existing
            repository.delete(existing);
            log.info("Shift deleted (total overlap): {} for person {}",
                    existing.getId(), existing.getPerson().getId());
        }
    }

    /**
     * When updating a shift's time range, adjust the immediately adjacent shifts
     * of the SAME person to fill gaps or resolve overlaps.
     */
    private void adjustAdjacentShifts(UUID shiftId, UUID personId,
            OffsetDateTime oldStart, OffsetDateTime oldEnd,
            OffsetDateTime newStart, OffsetDateTime newEnd) {
        var allPersonShifts = repository.findByPersonIdOrderByStartTimeAsc(personId);

        // Track changes: if endTime moved forward, adjust the next shift
        if (newEnd.isAfter(oldEnd)) {
            // Find the next shift (smallest startTime >= oldEnd)
            findNextShift(allPersonShifts, shiftId, oldEnd).ifPresent(next -> {
                if (next.getStartTime().isBefore(newEnd)) {
                    next.setStartTime(newEnd);
                    if (!next.getStartTime().isBefore(next.getEndTime())) {
                        throw new IllegalArgumentException(
                                "La modificación del turno " + shiftId
                                        + " dejaría inválido el turno adyacente " + next.getId()
                                        + " (fecha de inicio igual o posterior a la de fin)");
                    }
                    repository.save(next);
                    log.info("Adjacent shift start adjusted: {} → {}", next.getId(), newEnd);
                }
            });
        }

        // If endTime moved backward, extend the next shift to fill gap
        if (newEnd.isBefore(oldEnd)) {
            findNextShift(allPersonShifts, shiftId, oldEnd).ifPresent(next -> {
                if (next.getStartTime().isAfter(newEnd)) {
                    next.setStartTime(newEnd);
                    repository.save(next);
                    log.info("Adjacent shift start extended backward: {} → {}", next.getId(), newEnd);
                }
            });
        }

        // If startTime moved backward, adjust the previous shift
        if (newStart.isBefore(oldStart)) {
            findPreviousShift(allPersonShifts, shiftId, oldStart).ifPresent(prev -> {
                if (prev.getEndTime().isAfter(newStart)) {
                    prev.setEndTime(newStart);
                    if (!prev.getStartTime().isBefore(prev.getEndTime())) {
                        throw new IllegalArgumentException(
                                "La modificación del turno " + shiftId
                                        + " dejaría inválido el turno adyacente " + prev.getId()
                                        + " (fecha de inicio igual o posterior a la de fin)");
                    }
                    repository.save(prev);
                    log.info("Adjacent shift end adjusted: {} → {}", prev.getId(), newStart);
                }
            });
        }

        // If startTime moved forward, extend the previous shift to fill gap
        if (newStart.isAfter(oldStart)) {
            findPreviousShift(allPersonShifts, shiftId, oldStart).ifPresent(prev -> {
                if (prev.getEndTime().isBefore(newStart)) {
                    prev.setEndTime(newStart);
                    repository.save(prev);
                    log.info("Adjacent shift end extended forward: {} → {}", prev.getId(), newStart);
                }
            });
        }
    }

    /**
     * Find the immediately next shift for the same person (closest startTime >=
     * referenceEnd).
     */
    private Optional<Shift> findNextShift(List<Shift> shifts, UUID excludeId,
            OffsetDateTime referenceEnd) {
        return shifts.stream()
                .filter(s -> !s.getId().equals(excludeId))
                .filter(s -> !s.getStartTime().isBefore(referenceEnd))
                .min(Comparator.comparing(Shift::getStartTime));
    }

    /**
     * Find the immediately previous shift for the same person (closest endTime <=
     * referenceStart).
     */
    private Optional<Shift> findPreviousShift(List<Shift> shifts, UUID excludeId,
            OffsetDateTime referenceStart) {
        return shifts.stream()
                .filter(s -> !s.getId().equals(excludeId))
                .filter(s -> !s.getEndTime().isAfter(referenceStart))
                .max(Comparator.comparing(Shift::getEndTime));
    }

    private Shift findEntity(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shift", id));
    }

    private OnCallGroup findGroup(UUID id) {
        return onCallGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OnCallGroup", id));
    }

    private Person findPerson(UUID id) {
        return personRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Person", id));
    }
}
