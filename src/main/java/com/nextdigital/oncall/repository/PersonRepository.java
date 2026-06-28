package com.nextdigital.oncall.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nextdigital.oncall.entity.Person;

public interface PersonRepository extends JpaRepository<Person, UUID> {

    List<Person> findByIsActiveTrue();
}
