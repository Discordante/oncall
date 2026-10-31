package com.nextdigital.oncall.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nextdigital.oncall.entity.Person;

public interface PersonRepository extends JpaRepository<Person, UUID> {

    List<Person> findByIsActiveTrue();

    /**
     * Phone numbers of every person registered in the application. Used to
     * detect "check" calls (calls originated from a registered user's phone).
     */
    @Query("select p.phoneNumber from Person p where p.phoneNumber is not null and p.phoneNumber <> ''")
    List<String> findAllPhoneNumbers();

    Optional<Person> findByEmail(String email);

    Optional<Person> findByGoogleSub(String googleSub);

    Optional<Person> findByTelegramChatId(String telegramChatId);

    Optional<Person> findByTelegramUserId(String telegramUserId);

    Optional<Person> findByTelegramLinkCode(String telegramLinkCode);
}
