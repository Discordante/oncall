package com.nextdigital.oncall.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextdigital.oncall.dto.BillingPersonDetail;
import com.nextdigital.oncall.dto.BillingReportResponse;
import com.nextdigital.oncall.entity.OnCallGroup;
import com.nextdigital.oncall.entity.Shift;
import com.nextdigital.oncall.exception.ResourceNotFoundException;
import com.nextdigital.oncall.repository.OnCallGroupPriceHistoryRepository;
import com.nextdigital.oncall.repository.OnCallGroupRepository;
import com.nextdigital.oncall.repository.ShiftRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {

    private static final int HOURS_SCALE = 4;
    private static final int DAYS_SCALE = 4;
    private static final int BILLING_SCALE = 2;
    private static final BigDecimal HOURS_PER_DAY = new BigDecimal("24");

    private final OnCallGroupRepository onCallGroupRepository;
    private final ShiftRepository shiftRepository;
    private final OnCallGroupPriceHistoryRepository priceHistoryRepository;

    @Override
    @Transactional(readOnly = true)
    public BillingReportResponse calculateReport(UUID groupId, UUID personId,
            OffsetDateTime from, OffsetDateTime to) {

        // 1. Validate group exists
        var group = onCallGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("OnCallGroup", groupId));

        // 2. Default date range to current month if not provided
        if (from == null) {
            from = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        }
        if (to == null) {
            to = YearMonth.now(ZoneOffset.UTC).atEndOfMonth()
                    .atTime(23, 59, 59, 999_999_000)
                    .atOffset(ZoneOffset.UTC);
        }

        log.debug("Calculating billing for group={} person={} from={} to={}", groupId, personId, from, to);

        // 3. Query shifts overlapping the date range
        // Uses overlap logic (startTime < to AND endTime > from) to capture shifts
        // that cross the period boundaries and prorate only the hours inside the range.
        List<Shift> shifts;
        if (personId != null) {
            shifts = shiftRepository.findOverlappingByGroupAndPerson(
                    groupId, personId, from, to);
        } else {
            shifts = shiftRepository.findOverlappingByGroup(
                    groupId, from, to);
        }

        if (shifts.isEmpty()) {
            log.info("No shifts found for group={} in period {} to {}", groupId, from, to);
            return buildEmptyReport(group, from, to);
        }

        // 4. Group shifts by person
        Map<UUID, List<Shift>> shiftsByPerson = new LinkedHashMap<>();
        for (Shift shift : shifts) {
            shiftsByPerson.computeIfAbsent(shift.getPerson().getId(), k -> new ArrayList<>()).add(shift);
        }

        // 5. Calculate per-person billing
        BigDecimal reportTotalHours = BigDecimal.ZERO;
        BigDecimal reportTotalBilling = BigDecimal.ZERO;
        List<BillingPersonDetail> details = new ArrayList<>();

        for (var entry : shiftsByPerson.entrySet()) {
            var pId = entry.getKey();
            var personShifts = entry.getValue();
            var person = personShifts.getFirst().getPerson();

            BigDecimal personTotalHours = BigDecimal.ZERO;
            BigDecimal personTotalBilling = BigDecimal.ZERO;

            for (Shift shift : personShifts) {
                // Calculate only the hours that fall within the billing period
                // (prorates shifts that cross the period boundaries)
                BigDecimal shiftHours = calculateOverlapHours(shift, from, to);
                personTotalHours = personTotalHours.add(shiftHours);

                // Look up effective price at the time of the shift
                BigDecimal effectivePrice = resolveEffectivePrice(group, shift.getStartTime());

                // Calculate billing for this shift: (hours / 24) * price
                BigDecimal shiftDays = shiftHours.divide(HOURS_PER_DAY, DAYS_SCALE, RoundingMode.HALF_UP);
                BigDecimal shiftAmount = shiftDays.multiply(effectivePrice)
                        .setScale(BILLING_SCALE, RoundingMode.HALF_UP);
                personTotalBilling = personTotalBilling.add(shiftAmount);
            }

            personTotalHours = personTotalHours.setScale(HOURS_SCALE, RoundingMode.HALF_UP);
            BigDecimal personTotalDays = personTotalHours.divide(HOURS_PER_DAY, DAYS_SCALE, RoundingMode.HALF_UP);

            details.add(new BillingPersonDetail(
                    pId,
                    person.getName(),
                    person.getEmail(),
                    personTotalHours,
                    personTotalDays,
                    group.getGrossPricePerDay() != null ? group.getGrossPricePerDay() : BigDecimal.ZERO,
                    personTotalBilling.setScale(BILLING_SCALE, RoundingMode.HALF_UP)));

            reportTotalHours = reportTotalHours.add(personTotalHours);
            reportTotalBilling = reportTotalBilling.add(personTotalBilling);
        }

        log.info("Billing report generated for group={}: {} people, {} total hours, {} total billing",
                groupId, details.size(), reportTotalHours, reportTotalBilling);

        return new BillingReportResponse(
                groupId,
                group.getName(),
                group.getGrossPricePerDay() != null ? group.getGrossPricePerDay() : BigDecimal.ZERO,
                from,
                to,
                reportTotalHours.setScale(HOURS_SCALE, RoundingMode.HALF_UP),
                reportTotalBilling.setScale(BILLING_SCALE, RoundingMode.HALF_UP),
                details);
    }

    /**
     * Calculates only the hours of a shift that overlap with the given date range.
     * This ensures shifts that cross the period boundaries are prorated correctly,
     * billing only for the time actually worked within the period.
     */
    private BigDecimal calculateOverlapHours(Shift shift, OffsetDateTime rangeFrom, OffsetDateTime rangeTo) {
        OffsetDateTime effectiveStart = shift.getStartTime().isBefore(rangeFrom) ? rangeFrom : shift.getStartTime();
        OffsetDateTime effectiveEnd = shift.getEndTime().isAfter(rangeTo) ? rangeTo : shift.getEndTime();

        long millis = Duration.between(effectiveStart, effectiveEnd).toMillis();
        if (millis <= 0) {
            return BigDecimal.ZERO.setScale(HOURS_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(millis)
                .divide(BigDecimal.valueOf(3_600_000), HOURS_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Calculates the full duration of a shift in hours with fractional precision.
     * Used for non-billing purposes where the complete shift length is needed.
     */
    private BigDecimal calculateHours(Shift shift) {
        long millis = Duration.between(shift.getStartTime(), shift.getEndTime()).toMillis();
        return BigDecimal.valueOf(millis)
                .divide(BigDecimal.valueOf(3_600_000), HOURS_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Resolves the effective price for a shift at its start time.
     * First looks up the price history table; if no entry exists, falls back to
     * the group's current grossPricePerDay. If both are null, returns ZERO.
     */
    private BigDecimal resolveEffectivePrice(OnCallGroup group, OffsetDateTime shiftStartTime) {
        var historyOpt = priceHistoryRepository.findEffectivePrice(group.getId(), shiftStartTime);
        if (historyOpt.isPresent()) {
            return historyOpt.get().getPrice();
        }
        // Fallback to current group price
        if (group.getGrossPricePerDay() != null) {
            return group.getGrossPricePerDay();
        }
        log.warn("No price configured for group={} at {}. Using 0.", group.getId(), shiftStartTime);
        return BigDecimal.ZERO;
    }

    /**
     * Builds an empty report when no shifts are found.
     */
    private BillingReportResponse buildEmptyReport(OnCallGroup group, OffsetDateTime from, OffsetDateTime to) {
        return new BillingReportResponse(
                group.getId(),
                group.getName(),
                group.getGrossPricePerDay() != null ? group.getGrossPricePerDay() : BigDecimal.ZERO,
                from,
                to,
                BigDecimal.ZERO.setScale(HOURS_SCALE, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(BILLING_SCALE, RoundingMode.HALF_UP),
                List.of());
    }
}
