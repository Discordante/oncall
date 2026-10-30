package com.nextdigital.oncall.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.nextdigital.oncall.entity.Incident;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    Page<Incident> findByApplicationId(UUID applicationId, Pageable pageable);
}
