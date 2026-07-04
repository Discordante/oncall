package com.nextdigital.oncall.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nextdigital.oncall.entity.Application;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {
}
