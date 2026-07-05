package com.nextdigital.oncall.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nextdigital.oncall.entity.OnCallGroup;

public interface OnCallGroupRepository extends JpaRepository<OnCallGroup, UUID> {

    @Query("SELECT g FROM OnCallGroup g JOIN g.telegramGroupConfig tc WHERE tc.botEnabled = true")
    List<OnCallGroup> findByTelegramBotEnabledTrue();
}
