package com.nextdigital.oncall.repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.nextdigital.oncall.entity.Incident;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * Dynamic filters for incident searches. All filters are optional: a
 * {@code null} value simply omits that predicate.
 */
public final class IncidentSpecifications {

    private IncidentSpecifications() {
    }

    /**
     * @param applicationId optional affected application filter
     * @param onCallGroupId optional owner group filter (incident isolation)
     * @param startedFrom   optional lower bound for the incident start date
     * @param startedTo     optional upper bound for the incident start date
     */
    public static Specification<Incident> withFilters(UUID applicationId, UUID onCallGroupId,
            OffsetDateTime startedFrom, OffsetDateTime startedTo) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            if (applicationId != null) {
                predicates.add(cb.equal(root.get("application").get("id"), applicationId));
            }
            if (onCallGroupId != null) {
                predicates.add(cb.equal(root.get("onCallGroup").get("id"), onCallGroupId));
            }

            // Incidents without a start date fall back to their creation date, so
            // they are never hidden by the date filter.
            Expression<OffsetDateTime> startedAt = cb.coalesce(
                    root.<OffsetDateTime>get("startedAt"),
                    root.<OffsetDateTime>get("createdAt"));

            if (startedFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(startedAt, startedFrom));
            }
            if (startedTo != null) {
                predicates.add(cb.lessThanOrEqualTo(startedAt, startedTo));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
