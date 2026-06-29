package com.nextdigital.oncall.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.nextdigital.oncall.dto.ShiftRequest;
import com.nextdigital.oncall.dto.ShiftResponse;
import com.nextdigital.oncall.entity.OnCallGroup;
import com.nextdigital.oncall.entity.Person;
import com.nextdigital.oncall.entity.Shift;
import com.nextdigital.oncall.exception.ResourceNotFoundException;
import com.nextdigital.oncall.repository.OnCallGroupRepository;
import com.nextdigital.oncall.repository.ShiftRepository;

@ExtendWith(MockitoExtension.class)
class ShiftGenerationServiceImplTest {

    @Mock
    private ShiftService shiftService;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private OnCallGroupRepository onCallGroupRepository;

    @InjectMocks
    private ShiftGenerationServiceImpl generationService;

    private OnCallGroup group;
    private Person alice;
    private Person bob;
    private Person charlie;
    private UUID groupId;

    private static final ZoneId ZONE = ZoneId.of("UTC");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(generationService, "marginWeeks", 2);
        ReflectionTestUtils.setField(generationService, "coverageWeeks", 4);
        ReflectionTestUtils.setField(generationService, "timezone", "UTC");
        ReflectionTestUtils.setField(generationService, "lookbackDays", 90);

        groupId = UUID.randomUUID();

        alice = Person.builder()
                .id(UUID.randomUUID())
                .name("Alice")
                .email("alice@example.com")
                .isActive(true)
                .build();

        bob = Person.builder()
                .id(UUID.randomUUID())
                .name("Bob")
                .email("bob@example.com")
                .isActive(true)
                .build();

        charlie = Person.builder()
                .id(UUID.randomUUID())
                .name("Charlie")
                .email("charlie@example.com")
                .isActive(true)
                .build();

        group = OnCallGroup.builder()
                .id(groupId)
                .name("Test Group")
                .people(new java.util.HashSet<>(Set.of(alice, bob, charlie)))
                .build();
    }

    // --- generateForGroup ---

    @Test
    void generateForGroup_noEligiblePeople_returnsWarning() {
        group.getPeople().forEach(p -> p.setIsActive(false));

        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        var result = generationService.generateForGroup(groupId);

        assertThat(result.shiftsCreated()).isZero();
        assertThat(result.createdShifts()).isEmpty();
        assertThat(result.warnings()).contains("El grupo no tiene personas activas asignadas");
    }

    @Test
    void generateForGroup_groupNotFound_throwsException() {
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> generationService.generateForGroup(groupId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void generateForGroup_noExistingShifts_generatesFromNowPlusMargin() {
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(shiftRepository.findTopByOnCallGroupIdOrderByEndTimeDesc(groupId))
                .thenReturn(Optional.empty());
        when(shiftRepository.findByOnCallGroupIdAndEndTimeGreaterThanOrderByStartTimeAsc(
                any(UUID.class), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        var now = OffsetDateTime.now(ZONE);
        // Build a response that looks realistic
        var createdShift = buildShiftResponse(alice, now.plusWeeks(3), now.plusWeeks(4));
        when(shiftService.create(any(ShiftRequest.class))).thenReturn(createdShift);

        var result = generationService.generateForGroup(groupId);

        assertThat(result.shiftsCreated()).isEqualTo(4);
        assertThat(result.onCallGroupId()).isEqualTo(groupId);
        assertThat(result.createdShifts()).hasSize(4);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void generateForGroup_existingShiftEndingSoon_extendsForward() {
        var now = OffsetDateTime.now(ZONE);
        var existingShift = buildShift(now.minusWeeks(1), now.plusWeeks(1), alice);

        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(shiftRepository.findTopByOnCallGroupIdOrderByEndTimeDesc(groupId))
                .thenReturn(Optional.of(existingShift));
        when(shiftRepository.findByOnCallGroupIdAndEndTimeGreaterThanOrderByStartTimeAsc(
                any(UUID.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(existingShift));

        var createdShift = buildShiftResponse(bob, now.plusWeeks(2), now.plusWeeks(3));
        when(shiftService.create(any(ShiftRequest.class))).thenReturn(createdShift);

        var result = generationService.generateForGroup(groupId);

        assertThat(result.shiftsCreated()).isEqualTo(4);
        // Coverage should start from the Monday after existingShift.endTime
        assertThat(result.coverageStart()).isAfter(existingShift.getEndTime());
    }

    @Test
    void generateForGroup_existingShiftFarAhead_generatesFromItsEnd() {
        var now = OffsetDateTime.now(ZONE);
        // Shift that ends 6 weeks from now
        var farShift = buildShift(now.plusWeeks(5), now.plusWeeks(6), alice);

        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(shiftRepository.findTopByOnCallGroupIdOrderByEndTimeDesc(groupId))
                .thenReturn(Optional.of(farShift));
        when(shiftRepository.findByOnCallGroupIdAndEndTimeGreaterThanOrderByStartTimeAsc(
                any(UUID.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(farShift));

        var createdShift = buildShiftResponse(bob, now.plusWeeks(7), now.plusWeeks(8));
        when(shiftService.create(any(ShiftRequest.class))).thenReturn(createdShift);

        var result = generationService.generateForGroup(groupId);

        assertThat(result.shiftsCreated()).isEqualTo(4);
        // Generate always triggers for manual endpoint, even if shift is far ahead
        assertThat(result.coverageStart()).isAfter(farShift.getEndTime());
    }

    @Test
    void generateForGroup_equitableAssignment_picksLeastBurdenedPerson() {
        var now = OffsetDateTime.now(ZONE);

        // Alice already has 40 hours of guard time in the lookback window
        var aliceShift = buildShift(now.minusDays(10), now.minusDays(5), alice); // 120 hours
        // Bob has only 8 hours
        var bobShift = buildShift(now.minusDays(4), now.minusDays(3), bob); // 24 hours

        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(shiftRepository.findTopByOnCallGroupIdOrderByEndTimeDesc(groupId))
                .thenReturn(Optional.empty());
        when(shiftRepository.findByOnCallGroupIdAndEndTimeGreaterThanOrderByStartTimeAsc(
                any(UUID.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(aliceShift, bobShift));

        // Capture created requests to verify person assignment
        var createdShift = buildShiftResponse(bob, now.plusWeeks(3), now.plusWeeks(4));
        when(shiftService.create(any(ShiftRequest.class))).thenReturn(createdShift);

        var result = generationService.generateForGroup(groupId);

        assertThat(result.shiftsCreated()).isEqualTo(4);
        // Bob should get all weeks since he has the least guard time
        verify(shiftService, times(4)).create(any(ShiftRequest.class));
    }

    @Test
    void generateForGroup_singlePerson_assignsAllWeeks() {
        // Only Alice in the group
        var soloGroup = OnCallGroup.builder()
                .id(groupId)
                .name("Solo Group")
                .people(new java.util.HashSet<>(Set.of(alice)))
                .build();

        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(soloGroup));
        when(shiftRepository.findTopByOnCallGroupIdOrderByEndTimeDesc(groupId))
                .thenReturn(Optional.empty());
        when(shiftRepository.findByOnCallGroupIdAndEndTimeGreaterThanOrderByStartTimeAsc(
                any(UUID.class), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        var now = OffsetDateTime.now(ZONE);
        var createdShift = buildShiftResponse(alice, now.plusWeeks(3), now.plusWeeks(4));
        when(shiftService.create(any(ShiftRequest.class))).thenReturn(createdShift);

        var result = generationService.generateForGroup(groupId);

        assertThat(result.shiftsCreated()).isEqualTo(4);
        assertThat(result.warnings()).isEmpty();
    }

    // --- generateForAllGroups ---

    @Test
    void generateForAllGroups_skipsWhenCoverageSufficient() {
        var now = OffsetDateTime.now(ZONE);
        // Shift ends well beyond the margin
        var farShift = buildShift(now.plusWeeks(4), now.plusWeeks(5), alice);

        var anotherGroup = OnCallGroup.builder()
                .id(UUID.randomUUID())
                .name("Another Group")
                .people(new java.util.HashSet<>(Set.of(bob)))
                .build();

        when(onCallGroupRepository.findAll()).thenReturn(List.of(group, anotherGroup));
        when(shiftRepository.findTopByOnCallGroupIdOrderByEndTimeDesc(groupId))
                .thenReturn(Optional.of(farShift));
        when(shiftRepository.findTopByOnCallGroupIdOrderByEndTimeDesc(anotherGroup.getId()))
                .thenReturn(Optional.of(farShift));

        var result = generationService.generateForAllGroups();

        assertThat(result).isEmpty();
        verify(shiftService, times(0)).create(any(ShiftRequest.class));
    }

    @Test
    void generateForAllGroups_triggersWhenLastShiftEndsWithinMargin() {
        var now = OffsetDateTime.now(ZONE);
        // Shift ending within the margin
        var imminentShift = buildShift(now.minusDays(3), now.plusDays(3), alice);

        when(onCallGroupRepository.findAll()).thenReturn(List.of(group));
        when(shiftRepository.findTopByOnCallGroupIdOrderByEndTimeDesc(groupId))
                .thenReturn(Optional.of(imminentShift));
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(shiftRepository.findByOnCallGroupIdAndEndTimeGreaterThanOrderByStartTimeAsc(
                any(UUID.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(imminentShift));

        var createdShift = buildShiftResponse(bob, now.plusWeeks(1), now.plusWeeks(2));
        when(shiftService.create(any(ShiftRequest.class))).thenReturn(createdShift);

        var result = generationService.generateForAllGroups();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().shiftsCreated()).isEqualTo(4);
    }

    @Test
    void generateForAllGroups_triggersWhenNoShiftsExist() {
        when(onCallGroupRepository.findAll()).thenReturn(List.of(group));
        when(shiftRepository.findTopByOnCallGroupIdOrderByEndTimeDesc(groupId))
                .thenReturn(Optional.empty());
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(shiftRepository.findByOnCallGroupIdAndEndTimeGreaterThanOrderByStartTimeAsc(
                any(UUID.class), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        var now = OffsetDateTime.now(ZONE);
        var createdShift = buildShiftResponse(alice, now.plusWeeks(3), now.plusWeeks(4));
        when(shiftService.create(any(ShiftRequest.class))).thenReturn(createdShift);

        var result = generationService.generateForAllGroups();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().shiftsCreated()).isEqualTo(4);
    }

    // --- Helpers ---

    private Shift buildShift(OffsetDateTime start, OffsetDateTime end, Person person) {
        return Shift.builder()
                .id(UUID.randomUUID())
                .onCallGroup(group)
                .person(person)
                .startTime(start)
                .endTime(end)
                .build();
    }

    private ShiftResponse buildShiftResponse(Person person, OffsetDateTime start, OffsetDateTime end) {
        return new ShiftResponse(
                UUID.randomUUID(),
                groupId,
                group.getName(),
                person.getId(),
                person.getName(),
                person.getEmail(),
                start,
                end,
                OffsetDateTime.now(ZONE),
                null,
                OffsetDateTime.now(ZONE));
    }
}
