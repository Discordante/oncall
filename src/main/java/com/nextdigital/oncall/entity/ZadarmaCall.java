package com.nextdigital.oncall.entity;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "zadarma_call_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = { "onCallGroup", "incidents" })
@EqualsAndHashCode(exclude = { "onCallGroup", "incidents", "createdAt", "updatedAt" })
public class ZadarmaCall {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "external_call_id", nullable = false, unique = true)
    private String externalCallId;

    @Column(name = "sip_id", nullable = false)
    private String sipId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "on_call_group_id")
    private OnCallGroup onCallGroup;

    @Column(name = "caller_number")
    private String callerNumber;

    @Column(name = "destination_number")
    private String destinationNumber;

    @Column(name = "call_started_at", nullable = false)
    private OffsetDateTime callStartedAt;

    @Column(name = "bill_seconds", nullable = false)
    private int billSeconds;

    @Column(name = "disposition", nullable = false)
    private String disposition;

    @Column(name = "description", length = 500)
    private String description;

    // back-reference populated by Incident.calls
    @ManyToMany(mappedBy = "calls")
    @Builder.Default
    private Set<Incident> incidents = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
