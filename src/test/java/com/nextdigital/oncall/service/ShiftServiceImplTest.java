package com.nextdigital.oncall.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextdigital.oncall.dto.ShiftRequest;
import com.nextdigital.oncall.entity.OnCallGroup;
import com.nextdigital.oncall.entity.Person;
import com.nextdigital.oncall.entity.Shift;
import com.nextdigital.oncall.exception.ResourceNotFoundException;
import com.nextdigital.oncall.exception.ShiftOverlapException;
import com.nextdigital.oncall.repository.OnCallGroupRepository;
import com.nextdigital.oncall.repository.PersonRepository;
import com.nextdigital.oncall.repository.ShiftRepository;
import com.nextdigital.oncall.zadarma.scheduler.CallRedirectionScheduler;

import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ShiftServiceImplTest {

        @Mock
        private ShiftRepository repository;

        @Mock
        private OnCallGroupRepository onCallGroupRepository;

        @Mock
        private PersonRepository personRepository;

        @Mock
        private ApplicationEventPublisher eventPublisher;

        @Mock
        private CallRedirectionScheduler redirectionScheduler;

        @InjectMocks
        private ShiftServiceImpl service;

        private OnCallGroup group;
        private Person personA;
        private Person personB;
        private Shift existingShift;
        private UUID shiftId;

        private final OffsetDateTime DAY10_00 = OffsetDateTime.parse("2026-07-10T00:00:00Z");
        private final OffsetDateTime DAY12_00 = OffsetDateTime.parse("2026-07-12T00:00:00Z");
        private final OffsetDateTime DAY14_00 = OffsetDateTime.parse("2026-07-14T00:00:00Z");
        private final OffsetDateTime DAY15_12 = OffsetDateTime.parse("2026-07-15T12:00:00Z");
        private final OffsetDateTime DAY15_21 = OffsetDateTime.parse("2026-07-15T21:30:00Z");
        private final OffsetDateTime DAY17_08 = OffsetDateTime.parse("2026-07-17T08:00:00Z");

        @BeforeEach
        void setUp() {
                shiftId = UUID.randomUUID();

                group = OnCallGroup.builder()
                                .id(UUID.randomUUID())
                                .name("Weekend OnCall")
                                .build();

                personA = Person.builder()
                                .id(UUID.randomUUID())
                                .name("Alice")
                                .email("alice@example.com")
                                .isActive(true)
                                .build();

                personB = Person.builder()
                                .id(UUID.randomUUID())
                                .name("Bob")
                                .email("bob@example.com")
                                .isActive(true)
                                .build();

                existingShift = Shift.builder()
                                .id(shiftId)
                                .onCallGroup(group)
                                .person(personA)
                                .startTime(DAY10_00)
                                .endTime(DAY17_08)
                                .build();
        }

        // ── findAll ──────────────────────────────────────

        @Test
        void findAll_withoutFilters_returnsAll() {
                when(repository.findAll()).thenReturn(List.of(existingShift));

                var result = service.findAll(null, null, null, null);

                assertThat(result).hasSize(1);
                assertThat(result.get(0).personName()).isEqualTo("Alice");
        }

        @Test
        void findAll_withPersonAndDateFilters_returnsFiltered() {
                when(repository.findByPersonIdAndStartTimeBetweenOrderByStartTimeAsc(
                                personA.getId(), DAY10_00, DAY17_08))
                                .thenReturn(List.of(existingShift));

                var result = service.findAll(personA.getId(), null, DAY10_00, DAY17_08);

                assertThat(result).hasSize(1);
                assertThat(result.get(0).personId()).isEqualTo(personA.getId());
        }

        @Test
        void findAll_withFilters_returnsEmpty() {
                when(repository.findByPersonIdAndStartTimeBetweenOrderByStartTimeAsc(
                                personB.getId(), DAY10_00, DAY17_08))
                                .thenReturn(List.of());

                var result = service.findAll(personB.getId(), null, DAY10_00, DAY17_08);

                assertThat(result).isEmpty();
        }

        // ── findById ─────────────────────────────────────

        @Test
        void findById_returnsShift() {
                when(repository.findById(shiftId)).thenReturn(Optional.of(existingShift));

                var result = service.findById(shiftId);

                assertThat(result.personName()).isEqualTo("Alice");
                assertThat(result.onCallGroupName()).isEqualTo("Weekend OnCall");
        }

        @Test
        void findById_throwsWhenNotFound() {
                var id = UUID.randomUUID();
                when(repository.findById(id)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.findById(id))
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("Shift");
        }

        // ── create — basic ───────────────────────────────

        @Test
        void create_withoutOverlap_createsShift() {
                var request = new ShiftRequest(group.getId(), personA.getId(), DAY10_00, DAY17_08);

                when(onCallGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
                when(personRepository.findById(personA.getId())).thenReturn(Optional.of(personA));
                when(repository.findOverlappingByPerson(personA.getId(), DAY10_00, DAY17_08))
                                .thenReturn(List.of());
                when(repository.findOverlappingByOtherPersons(group.getId(), personA.getId(), DAY10_00, DAY17_08))
                                .thenReturn(List.of());
                when(repository.save(any())).thenAnswer(inv -> {
                        var saved = inv.getArgument(0, Shift.class);
                        var idField = Shift.class.getDeclaredField("id");
                        idField.setAccessible(true);
                        idField.set(saved, UUID.randomUUID());
                        return saved;
                });

                var result = service.create(request);

                assertThat(result.personName()).isEqualTo("Alice");
                assertThat(result.startTime()).isEqualTo(DAY10_00);
                assertThat(result.endTime()).isEqualTo(DAY17_08);
                verify(repository).save(any());
        }

        @Test
        void create_withSamePersonOverlap_throws409() {
                var request = new ShiftRequest(group.getId(), personA.getId(), DAY10_00, DAY17_08);

                when(onCallGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
                when(personRepository.findById(personA.getId())).thenReturn(Optional.of(personA));
                when(repository.findOverlappingByPerson(personA.getId(), DAY10_00, DAY17_08))
                                .thenReturn(List.of(existingShift));

                assertThatThrownBy(() -> service.create(request))
                                .isInstanceOf(ShiftOverlapException.class);
                verify(repository, never()).save(any());
        }

        @Test
        void create_withInvalidTimeRange_throwsIllegalArgument() {
                var request = new ShiftRequest(group.getId(), personA.getId(), DAY17_08, DAY10_00);

                assertThatThrownBy(() -> service.create(request))
                                .isInstanceOf(IllegalArgumentException.class);
        }

        // ── create — split logic ─────────────────────────

        @Test
        void create_splitsOtherPersonShift_middleCut() {
                // Person A has DAY10_00 → DAY17_08
                // Person B takes DAY15_12 → DAY15_21
                // Expected: A trimmed to DAY10_00→DAY15_12, new trailing A: DAY15_21→DAY17_08
                var request = new ShiftRequest(group.getId(), personB.getId(), DAY15_12, DAY15_21);

                when(onCallGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
                when(personRepository.findById(personB.getId())).thenReturn(Optional.of(personB));
                when(repository.findOverlappingByPerson(personB.getId(), DAY15_12, DAY15_21))
                                .thenReturn(List.of());
                when(repository.findOverlappingByOtherPersons(group.getId(), personB.getId(), DAY15_12, DAY15_21))
                                .thenReturn(List.of(existingShift));
                when(repository.save(any())).thenAnswer(inv -> {
                        var saved = inv.getArgument(0, Shift.class);
                        var idField = Shift.class.getDeclaredField("id");
                        idField.setAccessible(true);
                        idField.set(saved, UUID.randomUUID());
                        return saved;
                });

                var result = service.create(request);

                assertThat(result.personName()).isEqualTo("Bob");
                assertThat(result.startTime()).isEqualTo(DAY15_12);
                assertThat(result.endTime()).isEqualTo(DAY15_21);
                // Original shift trimmed
                assertThat(existingShift.getEndTime()).isEqualTo(DAY15_12);
                // save called 3 times: 1-trimmed A, 2-trailing A, 3-new B
                verify(repository, times(3)).save(any());
        }

        @Test
        void create_splitsOtherPersonShift_leadingCut() {
                // Person A has DAY10_00 → DAY14_00
                // Person B takes DAY10_00 → DAY12_00 (same start)
                // Expected: A deleted, new trailing A: DAY12_00→DAY14_00
                var aShift = Shift.builder()
                                .id(UUID.randomUUID())
                                .onCallGroup(group)
                                .person(personA)
                                .startTime(DAY10_00)
                                .endTime(DAY14_00)
                                .build();

                var request = new ShiftRequest(group.getId(), personB.getId(), DAY10_00, DAY12_00);

                when(onCallGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
                when(personRepository.findById(personB.getId())).thenReturn(Optional.of(personB));
                when(repository.findOverlappingByPerson(personB.getId(), DAY10_00, DAY12_00))
                                .thenReturn(List.of());
                when(repository.findOverlappingByOtherPersons(group.getId(), personB.getId(), DAY10_00, DAY12_00))
                                .thenReturn(List.of(aShift));
                when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                var result = service.create(request);

                assertThat(result.personName()).isEqualTo("Bob");
                // Original A deleted
                verify(repository).delete(aShift);
        }

        @Test
        void create_splitsOtherPersonShift_trailingCut() {
                // Person A has DAY14_00 → DAY17_08
                // Person B takes DAY15_12 → DAY17_08 (same end)
                // Expected: A trimmed to DAY14_00→DAY15_12
                var aShift = Shift.builder()
                                .id(UUID.randomUUID())
                                .onCallGroup(group)
                                .person(personA)
                                .startTime(DAY14_00)
                                .endTime(DAY17_08)
                                .build();

                var request = new ShiftRequest(group.getId(), personB.getId(), DAY15_12, DAY17_08);

                when(onCallGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
                when(personRepository.findById(personB.getId())).thenReturn(Optional.of(personB));
                when(repository.findOverlappingByPerson(personB.getId(), DAY15_12, DAY17_08))
                                .thenReturn(List.of());
                when(repository.findOverlappingByOtherPersons(group.getId(), personB.getId(), DAY15_12, DAY17_08))
                                .thenReturn(List.of(aShift));
                when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                var result = service.create(request);

                assertThat(result.personName()).isEqualTo("Bob");
                // A trimmed
                assertThat(aShift.getEndTime()).isEqualTo(DAY15_12);
                verify(repository).save(aShift);
        }

        @Test
        void create_splitsOtherPersonShift_totalOverlap() {
                // Person A has DAY10_00 → DAY14_00
                // Person B takes DAY09_00 → DAY15_00 (totally engulfs A)
                var aShift = Shift.builder()
                                .id(UUID.randomUUID())
                                .onCallGroup(group)
                                .person(personA)
                                .startTime(DAY10_00)
                                .endTime(DAY14_00)
                                .build();

                var bStart = OffsetDateTime.parse("2026-07-09T00:00:00Z");
                var bEnd = OffsetDateTime.parse("2026-07-15T00:00:00Z");

                var request = new ShiftRequest(group.getId(), personB.getId(), bStart, bEnd);

                when(onCallGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
                when(personRepository.findById(personB.getId())).thenReturn(Optional.of(personB));
                when(repository.findOverlappingByPerson(personB.getId(), bStart, bEnd))
                                .thenReturn(List.of());
                when(repository.findOverlappingByOtherPersons(group.getId(), personB.getId(), bStart, bEnd))
                                .thenReturn(List.of(aShift));
                when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                var result = service.create(request);

                assertThat(result.personName()).isEqualTo("Bob");
                verify(repository).delete(aShift);
        }

        // ── update — adjacent adjustment ─────────────────

        @Test
        void update_extendingEndTime_adjustsNextShift() {
                // existingShift: DAY10_00→DAY14_00 (override from @BeforeEach)
                existingShift.setEndTime(DAY14_00);
                // Next shift B (same person): DAY14_00→DAY17_08
                var nextShift = Shift.builder()
                                .id(UUID.randomUUID())
                                .onCallGroup(group)
                                .person(personA)
                                .startTime(DAY14_00)
                                .endTime(DAY17_08)
                                .build();

                var newEnd = OffsetDateTime.parse("2026-07-15T00:00:00Z");
                var request = new ShiftRequest(group.getId(), personA.getId(), DAY10_00, newEnd);

                when(repository.findById(shiftId)).thenReturn(Optional.of(existingShift));
                when(onCallGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
                when(personRepository.findById(personA.getId())).thenReturn(Optional.of(personA));
                when(repository.findOverlappingByOtherPersonsExcludingSelf(
                                group.getId(), personA.getId(), shiftId, DAY10_00, newEnd))
                                .thenReturn(List.of());
                when(repository.findByPersonIdOrderByStartTimeAsc(personA.getId()))
                                .thenReturn(List.of(existingShift, nextShift));
                when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                var result = service.update(shiftId, request);

                assertThat(result.endTime()).isEqualTo(newEnd);
                assertThat(nextShift.getStartTime()).isEqualTo(newEnd);
                verify(repository).save(nextShift);
        }

        @Test
        void update_shorteningEndTime_extendsNextShiftBackward() {
                // Shift A: DAY10_00→DAY17_08
                // Next shift B: DAY17_08→DAY20_00
                // Shorten A to DAY14_00 → B's startTime becomes DAY14_00 (fills gap)
                var nextShift = Shift.builder()
                                .id(UUID.randomUUID())
                                .onCallGroup(group)
                                .person(personA)
                                .startTime(DAY17_08)
                                .endTime(OffsetDateTime.parse("2026-07-20T00:00:00Z"))
                                .build();

                var request = new ShiftRequest(group.getId(), personA.getId(), DAY10_00, DAY14_00);

                when(repository.findById(shiftId)).thenReturn(Optional.of(existingShift));
                when(onCallGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
                when(personRepository.findById(personA.getId())).thenReturn(Optional.of(personA));
                when(repository.findOverlappingByOtherPersonsExcludingSelf(
                                group.getId(), personA.getId(), shiftId, DAY10_00, DAY14_00))
                                .thenReturn(List.of());
                when(repository.findByPersonIdOrderByStartTimeAsc(personA.getId()))
                                .thenReturn(List.of(existingShift, nextShift));
                when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                var result = service.update(shiftId, request);

                assertThat(result.endTime()).isEqualTo(DAY14_00);
                assertThat(nextShift.getStartTime()).isEqualTo(DAY14_00);
                verify(repository).save(nextShift);
        }

        @Test
        void update_extendingStartTime_adjustsPreviousShift() {
                // Previous shift P: DAY08_00→DAY10_00
                // Shift A: DAY10_00→DAY17_08
                // Extend A start to DAY09_00 → P's endTime becomes DAY09_00
                var prevStart = OffsetDateTime.parse("2026-07-08T00:00:00Z");
                var prevShift = Shift.builder()
                                .id(UUID.randomUUID())
                                .onCallGroup(group)
                                .person(personA)
                                .startTime(prevStart)
                                .endTime(DAY10_00)
                                .build();

                var newStart = OffsetDateTime.parse("2026-07-09T00:00:00Z");
                var request = new ShiftRequest(group.getId(), personA.getId(), newStart, DAY17_08);

                when(repository.findById(shiftId)).thenReturn(Optional.of(existingShift));
                when(onCallGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
                when(personRepository.findById(personA.getId())).thenReturn(Optional.of(personA));
                when(repository.findOverlappingByOtherPersonsExcludingSelf(
                                group.getId(), personA.getId(), shiftId, newStart, DAY17_08))
                                .thenReturn(List.of());
                when(repository.findByPersonIdOrderByStartTimeAsc(personA.getId()))
                                .thenReturn(List.of(prevShift, existingShift));
                when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                var result = service.update(shiftId, request);

                assertThat(result.startTime()).isEqualTo(newStart);
                assertThat(prevShift.getEndTime()).isEqualTo(newStart);
                verify(repository).save(prevShift);
        }

        @Test
        void update_extendingEndTime_throwsWhenAdjacentShiftInvalid() {
                // existingShift: DAY10_00→DAY14_00
                existingShift.setEndTime(DAY14_00);
                // Next shift B: DAY14_00→DAY14_00 (zero-length, already invalid as start==end)
                var nextShift = Shift.builder()
                                .id(UUID.randomUUID())
                                .onCallGroup(group)
                                .person(personA)
                                .startTime(DAY14_00)
                                .endTime(DAY14_00)
                                .build();

                // Extend existingShift to DAY15_00 → nextShift would become
                // DAY14_00→DAY15_00... wait,
                // actually nextShift endTime is DAY14_00, so if we extend existing to DAY14_00,
                // nextShift start becomes DAY14_00 = its end → invalid.
                var newEnd = OffsetDateTime.parse("2026-07-15T00:00:00Z");
                var request = new ShiftRequest(group.getId(), personA.getId(), DAY10_00, newEnd);

                when(repository.findById(shiftId)).thenReturn(Optional.of(existingShift));
                when(onCallGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
                when(personRepository.findById(personA.getId())).thenReturn(Optional.of(personA));
                when(repository.findOverlappingByOtherPersonsExcludingSelf(
                                group.getId(), personA.getId(), shiftId, DAY10_00, newEnd))
                                .thenReturn(List.of());
                when(repository.findByPersonIdOrderByStartTimeAsc(personA.getId()))
                                .thenReturn(List.of(existingShift, nextShift));

                assertThatThrownBy(() -> service.update(shiftId, request))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("inválido");
        }

        @Test
        void update_extendingStartTime_throwsWhenAdjacentShiftInvalid() {
                // Previous shift P: DAY10_00→DAY10_00 (zero-length)
                var prevShift = Shift.builder()
                                .id(UUID.randomUUID())
                                .onCallGroup(group)
                                .person(personA)
                                .startTime(DAY10_00)
                                .endTime(DAY10_00)
                                .build();

                // existingShift: DAY10_00→DAY17_08
                // Extend start back to DAY09_00 → prevShift end becomes DAY09_00 which is
                // before its start
                var newStart = OffsetDateTime.parse("2026-07-09T00:00:00Z");
                var request = new ShiftRequest(group.getId(), personA.getId(), newStart, DAY17_08);

                when(repository.findById(shiftId)).thenReturn(Optional.of(existingShift));
                when(onCallGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
                when(personRepository.findById(personA.getId())).thenReturn(Optional.of(personA));
                when(repository.findOverlappingByOtherPersonsExcludingSelf(
                                group.getId(), personA.getId(), shiftId, newStart, DAY17_08))
                                .thenReturn(List.of());
                when(repository.findByPersonIdOrderByStartTimeAsc(personA.getId()))
                                .thenReturn(List.of(prevShift, existingShift));

                assertThatThrownBy(() -> service.update(shiftId, request))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("inválido");
        }

        // ── delete ───────────────────────────────────────

        @Test
        void delete_removesShift() {
                when(repository.findById(shiftId)).thenReturn(Optional.of(existingShift));

                service.delete(shiftId);

                verify(repository).delete(existingShift);
        }

        @Test
        void delete_throwsWhenNotFound() {
                var id = UUID.randomUUID();
                when(repository.findById(id)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.delete(id))
                                .isInstanceOf(ResourceNotFoundException.class);
        }
}
