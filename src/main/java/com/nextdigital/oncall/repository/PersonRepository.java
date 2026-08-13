package com.nextdigital.oncall.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nextdigital.oncall.entity.Person;

public interface PersonRepository extends JpaRepository<Person, UUID> {

    List<Person> findByIsActiveTrue();

    Optional<Person> findByEmail(String email);

    Optional<Person> findByGoogleSub(String googleSub);

    Optional<Person> findByTelegramChatId(String telegramChatId);

    Optional<Person> findByTelegramUserId(String telegramUserId);

    Optional<Person> findByTelegramLinkCode(String telegramLinkCode);
}
