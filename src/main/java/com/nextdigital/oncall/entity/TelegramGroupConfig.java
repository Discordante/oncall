package com.nextdigital.oncall.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "telegram_group_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "onCallGroup")
@EqualsAndHashCode(exclude = { "onCallGroup", "createdAt", "updatedAt" })
public class TelegramGroupConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bot_token")
    private String botToken;

    @Column(name = "group_chat_id")
    private String groupChatId;

    @Column(name = "bot_enabled", nullable = false)
    @Builder.Default
    private Boolean botEnabled = false;

    @Column(name = "group_invite_link")
    private String groupInviteLink;

    @OneToOne(mappedBy = "telegramGroupConfig")
    private OnCallGroup onCallGroup;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
