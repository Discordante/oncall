package com.nextdigital.oncall.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextdigital.oncall.entity.OnCallGroup;
import com.nextdigital.oncall.entity.OnCallGroupPriceHistory;
import com.nextdigital.oncall.entity.Person;
import com.nextdigital.oncall.entity.Shift;
import com.nextdigital.oncall.exception.ResourceNotFoundException;
import com.nextdigital.oncall.repository.OnCallGroupPriceHistoryRepository;
import com.nextdigital.oncall.repository.OnCallGroupRepository;
import com.nextdigital.oncall.repository.ShiftRepository;

@ExtendWith(MockitoExtension.class)
class BillingServiceImplTest {

    @Mock
    private OnCallGroupRepository onCallGroupRepository;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private OnCallGroupPriceHistoryRepository priceHistoryRepository;

    @InjectMocks
    private BillingServiceImpl service;

    private OnCallGroup group;
    private Person personA;
    private Person personB;
    private UUID groupId;
    private UUID personAId;
    private UUID personBId;

    private final OffsetDateTime JUN_01 = OffsetDateTime.parse("2026-06-01T00:00:00Z");
    private final OffsetDateTime JUN_08 = OffsetDateTime.parse("2026-06-08T00:00:00Z");
    private final OffsetDateTime JUN_15 = OffsetDateTime.parse("2026-06-15T00:00:00Z");
    private final OffsetDateTime JUN_22 = OffsetDateTime.parse("2026-06-22T00:00:00Z");
    private final OffsetDateTime JUN_30 = OffsetDateTime.parse("2026-06-30T23:59:59Z");

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        personAId = UUID.randomUUID();
        personBId = UUID.randomUUID();

        group = OnCallGroup.builder()
                .id(groupId)
                .name("SRE OnCall")
                .grossPricePerDay(new BigDecimal("100.00"))
                .build();

        personA = Person.builder()
                .id(personAId)
                .name("Alice")
                .email("alice@example.com")
                .isActive(true)
                .build();

        personB = Person.builder()
                .id(personBId)
                .name("Bob")
                .email("bob@example.com")
                .isActive(true)
                .build();
    }

    @Test
    void calculateReport_withSinglePersonAndExplicitDates_returnsCorrectBilling() {
        // Alice has one 24h shift (Jun 1 → Jun 2) → 24h
        var shift = Shift.builder()
                .id(UUID.randomUUID())
                .onCallGroup(group)
                .person(personA)
                .startTime(JUN_01)
                .endTime(JUN_01.plusHours(24))
                .build();

        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(shiftRepository.findByOnCallGroupIdAndPersonIdAndStartTimeBetweenOrderByStartTimeAsc(
                groupId, personAId, JUN_01, JUN_30))
                .thenReturn(List.of(shift));
        when(priceHistoryRepository.findEffectivePrice(groupId, JUN_01))
                .thenReturn(Optional.empty()); // fallback to group price

        var report = service.calculateReport(groupId, personAId, JUN_01, JUN_30);

        assertThat(report.groupId()).isEqualTo(groupId);
        assertThat(report.groupName()).isEqualTo("SRE OnCall");
        assertThat(report.from()).isEqualTo(JUN_01);
        assertThat(report.to()).isEqualTo(JUN_30);

        // 24h = 1 day × 100 = 100.00
        assertThat(report.details()).hasSize(1);

        var detail = report.details().get(0);
        assertThat(detail.personId()).isEqualTo(personAId);
        assertThat(detail.personName()).isEqualTo("Alice");
        assertThat(detail.totalHours()).isEqualByComparingTo(new BigDecimal("24.0000"));
        assertThat(detail.totalDays()).isEqualByComparingTo(new BigDecimal("1.0000"));
        assertThat(detail.totalBilling()).isEqualByComparingTo(new BigDecimal("100.00"));

        assertThat(report.totalHours()).isEqualByComparingTo(new BigDecimal("24.0000"));
        assertThat(report.totalBilling()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void calculateReport_withMultiplePeople_returnsAggregatedBilling() {
        // Alice: 24h shift → 1 day × 100 = 100
        // Bob: 12h shift → 0.5 days × 100 = 50
        var aliceShift = Shift.builder()
                .id(UUID.randomUUID())
                .onCallGroup(group)
                .person(personA)
                .startTime(JUN_01)
                .endTime(JUN_01.plusHours(24))
                .build();

        var bobShift = Shift.builder()
                .id(UUID.randomUUID())
                .onCallGroup(group)
                .person(personB)
                .startTime(JUN_08)
                .endTime(JUN_08.plusHours(12))
                .build();

        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(shiftRepository.findByOnCallGroupIdAndStartTimeBetweenOrderByStartTimeAsc(
                groupId, JUN_01, JUN_30))
                .thenReturn(List.of(aliceShift, bobShift));
        when(priceHistoryRepository.findEffectivePrice(groupId, JUN_01))
                .thenReturn(Optional.empty());
        when(priceHistoryRepository.findEffectivePrice(groupId, JUN_08))
                .thenReturn(Optional.empty());

        var report = service.calculateReport(groupId, null, JUN_01, JUN_30);

        assertThat(report.details()).hasSize(2);

        // Total: 24 + 12 = 36h
        assertThat(report.totalHours()).isEqualByComparingTo(new BigDecimal("36.0000"));
        // Total billing: 100 + 50 = 150
        assertThat(report.totalBilling()).isEqualByComparingTo(new BigDecimal("150.00"));

        var aliceDetail = report.details().stream()
                .filter(d -> d.personId().equals(personAId))
                .findFirst().orElseThrow();
        assertThat(aliceDetail.totalBilling()).isEqualByComparingTo(new BigDecimal("100.00"));

        var bobDetail = report.details().stream()
                .filter(d -> d.personId().equals(personBId))
                .findFirst().orElseThrow();
        assertThat(bobDetail.totalBilling()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void calculateReport_withPriceHistory_usesHistoricalPrice() {
        // Price was 80 before June 15, then changed to 100
        // Alice has 24h on Jun 1 (price=80) and 24h on Jun 22 (price=100)
        var earlyShift = Shift.builder()
                .id(UUID.randomUUID())
                .onCallGroup(group)
                .person(personA)
                .startTime(JUN_01)
                .endTime(JUN_01.plusHours(24))
                .build();

        var lateShift = Shift.builder()
                .id(UUID.randomUUID())
                .onCallGroup(group)
                .person(personA)
                .startTime(JUN_22)
                .endTime(JUN_22.plusHours(24))
                .build();

        var oldPrice = OnCallGroupPriceHistory.builder()
                .id(UUID.randomUUID())
                .price(new BigDecimal("80.00"))
                .effectiveFrom(OffsetDateTime.parse("2026-01-01T00:00:00Z"))
                .effectiveTo(OffsetDateTime.parse("2026-06-15T00:00:00Z"))
                .build();

        var newPrice = OnCallGroupPriceHistory.builder()
                .id(UUID.randomUUID())
                .price(new BigDecimal("100.00"))
                .effectiveFrom(OffsetDateTime.parse("2026-06-15T00:00:00Z"))
                .effectiveTo(null)
                .build();

        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(shiftRepository.findByOnCallGroupIdAndPersonIdAndStartTimeBetweenOrderByStartTimeAsc(
                groupId, personAId, JUN_01, JUN_30))
                .thenReturn(List.of(earlyShift, lateShift));
        when(priceHistoryRepository.findEffectivePrice(groupId, JUN_01))
                .thenReturn(Optional.of(oldPrice));
        when(priceHistoryRepository.findEffectivePrice(groupId, JUN_22))
                .thenReturn(Optional.of(newPrice));

        var report = service.calculateReport(groupId, personAId, JUN_01, JUN_30);

        // 1 day at 80 + 1 day at 100 = 180
        assertThat(report.details()).hasSize(1);
        assertThat(report.details().get(0).totalBilling()).isEqualByComparingTo(new BigDecimal("180.00"));
        assertThat(report.totalBilling()).isEqualByComparingTo(new BigDecimal("180.00"));
        assertThat(report.totalHours()).isEqualByComparingTo(new BigDecimal("48.0000"));
    }

    @Test
    void calculateReport_withPersonId_returnsOnlyThatPerson() {
        var aliceShift = Shift.builder()
                .id(UUID.randomUUID())
                .onCallGroup(group)
                .person(personA)
                .startTime(JUN_01)
                .endTime(JUN_01.plusHours(24))
                .build();

        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(shiftRepository.findByOnCallGroupIdAndPersonIdAndStartTimeBetweenOrderByStartTimeAsc(
                groupId, personAId, JUN_01, JUN_30))
                .thenReturn(List.of(aliceShift));
        when(priceHistoryRepository.findEffectivePrice(groupId, JUN_01))
                .thenReturn(Optional.empty());

        var report = service.calculateReport(groupId, personAId, JUN_01, JUN_30);

        assertThat(report.details()).hasSize(1);
        assertThat(report.details().get(0).personId()).isEqualTo(personAId);
    }

    @Test
    void calculateReport_withDefaultDates_usesCurrentMonth() {
        // Just verify it doesn't throw when from/to are null
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(shiftRepository.findByOnCallGroupIdAndStartTimeBetweenOrderByStartTimeAsc(
                eq(groupId), any(), any()))
                .thenReturn(List.of());

        var report = service.calculateReport(groupId, null, null, null);

        assertThat(report.details()).isEmpty();
        assertThat(report.totalHours()).isEqualByComparingTo(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
    }

    @Test
    void calculateReport_withNonExistentGroup_throwsResourceNotFound() {
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.calculateReport(groupId, null, JUN_01, JUN_30))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("OnCallGroup");
    }

    @Test
    void calculateReport_withFractionalShift_calculatesCorrectly() {
        // Alice has a 6h shift (Jun 1 00:00 → Jun 1 06:00)
        var shift = Shift.builder()
                .id(UUID.randomUUID())
                .onCallGroup(group)
                .person(personA)
                .startTime(JUN_01)
                .endTime(JUN_01.plusHours(6))
                .build();

        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(shiftRepository.findByOnCallGroupIdAndPersonIdAndStartTimeBetweenOrderByStartTimeAsc(
                groupId, personAId, JUN_01, JUN_30))
                .thenReturn(List.of(shift));
        when(priceHistoryRepository.findEffectivePrice(groupId, JUN_01))
                .thenReturn(Optional.empty());

        var report = service.calculateReport(groupId, personAId, JUN_01, JUN_30);

        // 6h = 0.25 days × 100 = 25.00
        var detail = report.details().get(0);
        assertThat(detail.totalHours()).isEqualByComparingTo(new BigDecimal("6.0000"));
        assertThat(detail.totalDays()).isEqualByComparingTo(new BigDecimal("0.2500"));
        assertThat(detail.totalBilling()).isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    void calculateReport_withNoShifts_returnsEmptyReport() {
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(shiftRepository.findByOnCallGroupIdAndStartTimeBetweenOrderByStartTimeAsc(
                groupId, JUN_01, JUN_30))
                .thenReturn(List.of());

        var report = service.calculateReport(groupId, null, JUN_01, JUN_30);

        assertThat(report.details()).isEmpty();
        assertThat(report.totalHours()).isEqualByComparingTo(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP));
        assertThat(report.totalBilling()).isEqualByComparingTo(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        assertThat(report.grossPricePerDay()).isEqualByComparingTo(new BigDecimal("100.00"));
    }
}
