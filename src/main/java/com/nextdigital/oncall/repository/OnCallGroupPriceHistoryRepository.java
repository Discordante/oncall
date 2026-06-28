package com.nextdigital.oncall.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nextdigital.oncall.entity.OnCallGroupPriceHistory;

public interface OnCallGroupPriceHistoryRepository extends JpaRepository<OnCallGroupPriceHistory, UUID> {

    /**
     * Finds the price history entry for a group that was effective at the given
     * date.
     * This is the entry where effective_from <= date AND (effective_to IS NULL OR
     * effective_to > date),
     * ordered by effective_from descending so the most recent applicable price is
     * returned.
     */
    @Query("SELECT p FROM OnCallGroupPriceHistory p "
            + "WHERE p.onCallGroup.id = :groupId "
            + "AND p.effectiveFrom <= :date "
            + "AND (p.effectiveTo IS NULL OR p.effectiveTo > :date) "
            + "ORDER BY p.effectiveFrom DESC "
            + "LIMIT 1")
    Optional<OnCallGroupPriceHistory> findEffectivePrice(
            @Param("groupId") UUID groupId,
            @Param("date") OffsetDateTime date);
}
