package com.nextdigital.oncall.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "on_call_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = { "applications", "people", "shifts" })
@EqualsAndHashCode(exclude = { "applications", "people", "shifts", "createdAt", "updatedAt" })
public class OnCallGroup {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        private UUID id;

        @Column(nullable = false)
        private String name;

        @Column(name = "gross_price_per_day", precision = 10, scale = 2)
        private BigDecimal grossPricePerDay;

        @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
        @JoinColumn(name = "telegram_group_config_id", referencedColumnName = "id")
        private TelegramGroupConfig telegramGroupConfig;

        @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
        @JoinColumn(name = "zadarma_config_id", referencedColumnName = "id")
        private ZadarmaGroupConfig zadarmaConfig;

        @ManyToMany
        @JoinTable(name = "on_call_group_apps", joinColumns = @JoinColumn(name = "on_call_group_id"), inverseJoinColumns = @JoinColumn(name = "application_id"))
        @Builder.Default
        private Set<Application> applications = new HashSet<>();

        @ManyToMany
        @JoinTable(name = "on_call_group_people", joinColumns = @JoinColumn(name = "on_call_group_id"), inverseJoinColumns = @JoinColumn(name = "person_id"))
        @Builder.Default
        private Set<Person> people = new HashSet<>();

        @OneToMany(mappedBy = "onCallGroup")
        @Builder.Default
        private Set<Shift> shifts = new HashSet<>();

        @CreationTimestamp
        @Column(name = "created_at", nullable = false, updatable = false)
        private OffsetDateTime createdAt;

        @UpdateTimestamp
        @Column(name = "updated_at", nullable = false)
        private OffsetDateTime updatedAt;
}
