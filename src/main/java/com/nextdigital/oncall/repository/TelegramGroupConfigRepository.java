package com.nextdigital.oncall.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nextdigital.oncall.entity.TelegramGroupConfig;

public interface TelegramGroupConfigRepository extends JpaRepository<TelegramGroupConfig, UUID> {
}
