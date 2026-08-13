package com.nextdigital.oncall.entity;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "people")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = { "onCallGroups", "shifts" })
@EqualsAndHashCode(exclude = { "onCallGroups", "shifts", "createdAt", "updatedAt" })
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Telegram user id (message.from.id). Unique identity of the Telegram
     * account linked to this person. Unlike the chat id, it is the same for
     * a user in every chat (private or group).
     */
    @Column(name = "telegram_user_id", unique = true)
    private String telegramUserId;

    /**
     * Private chat id used to deliver direct messages (DM). Only set when
     * the person linked the account from a private chat with the bot.
     */
    @Column(name = "telegram_chat_id", unique = true)
    private String telegramChatId;

    @Column(name = "google_sub", unique = true)
    private String googleSub;

    @Column(name = "telegram_link_code", unique = true)
    private String telegramLinkCode;

    @Column(name = "telegram_linked_at")
    private OffsetDateTime telegramLinkedAt;

    @ManyToMany(mappedBy = "people")
    @Builder.Default
    private Set<OnCallGroup> onCallGroups = new HashSet<>();

    @OneToMany(mappedBy = "person")
    @Builder.Default
    private Set<Shift> shifts = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
