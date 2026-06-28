package com.nextdigital.oncall.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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

        @Column(name = "telegram_bot_token")
        private String telegramBotToken;

        @Column(name = "telegram_group_chat_id")
        private String telegramGroupChatId;

        @Column(name = "telegram_bot_enabled", nullable = false)
        @Builder.Default
        private Boolean telegramBotEnabled = false;

        @Column(name = "telegram_group_invite_link")
        private String telegramGroupInviteLink;

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
