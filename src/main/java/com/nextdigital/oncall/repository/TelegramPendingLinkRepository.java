package com.nextdigital.oncall.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nextdigital.oncall.entity.TelegramPendingLink;

public interface TelegramPendingLinkRepository extends JpaRepository<TelegramPendingLink, UUID> {

    Optional<TelegramPendingLink> findByLinkCode(String linkCode);

    Optional<TelegramPendingLink> findByChatId(String chatId);

    Optional<TelegramPendingLink> findByUserId(String userId);

    void deleteByLinkCode(String linkCode);
}
