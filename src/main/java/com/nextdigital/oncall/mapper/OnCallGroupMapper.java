package com.nextdigital.oncall.mapper;

import java.util.Set;
import java.util.stream.Collectors;

import com.nextdigital.oncall.dto.ApplicationResponse;
import com.nextdigital.oncall.dto.OnCallGroupRequest;
import com.nextdigital.oncall.dto.OnCallGroupResponse;
import com.nextdigital.oncall.dto.PersonResponse;
import com.nextdigital.oncall.entity.Application;
import com.nextdigital.oncall.entity.OnCallGroup;
import com.nextdigital.oncall.entity.Person;
import com.nextdigital.oncall.entity.TelegramGroupConfig;
import com.nextdigital.oncall.entity.ZadarmaGroupConfig;

public final class OnCallGroupMapper {

        private OnCallGroupMapper() {
        }

        public static OnCallGroupResponse toResponse(OnCallGroup entity) {
                Set<ApplicationResponse> applications = entity.getApplications().stream()
                                .map(ApplicationMapper::toResponse)
                                .collect(Collectors.toSet());

                Set<PersonResponse> people = entity.getPeople().stream()
                                .map(PersonMapper::toResponse)
                                .collect(Collectors.toSet());

                var config = entity.getTelegramGroupConfig();
                var zadarma = entity.getZadarmaConfig();

                return new OnCallGroupResponse(
                                entity.getId(),
                                entity.getName(),
                                entity.getGrossPricePerDay(),
                                config != null ? config.getGroupChatId() : null,
                                config != null ? config.getBotToken() : null,
                                config != null && config.getBotEnabled() != null && config.getBotEnabled(),
                                config != null ? config.getGroupInviteLink() : null,
                                applications,
                                people,
                                entity.getCreatedAt(),
                                zadarma != null ? zadarma.getSipId() : null,
                                zadarma != null ? zadarma.getRedirectionType() : null,
                                zadarma != null ? zadarma.getActive() : null,
                                entity.getUpdatedAt());
        }

        public static OnCallGroup toEntity(OnCallGroupRequest request,
                        Set<Application> applications,
                        Set<Person> people) {
                var group = OnCallGroup.builder()
                                .name(request.name())
                                .grossPricePerDay(request.grossPricePerDay())
                                .applications(applications)
                                .people(people)
                                .build();

                group.setTelegramGroupConfig(buildTelegramConfig(request));
                group.setZadarmaConfig(buildZadarmaConfig(request));

                return group;
        }

        public static void updateEntity(OnCallGroupRequest request,
                        OnCallGroup entity,
                        Set<Application> applications,
                        Set<Person> people) {
                entity.setName(request.name());
                entity.setGrossPricePerDay(request.grossPricePerDay());
                entity.setApplications(applications);
                entity.setPeople(people);

                var newConfig = buildTelegramConfig(request);
                if (newConfig != null) {
                        var existingConfig = entity.getTelegramGroupConfig();
                        if (existingConfig != null) {
                                existingConfig.setBotToken(newConfig.getBotToken());
                                existingConfig.setGroupChatId(newConfig.getGroupChatId());
                                existingConfig.setBotEnabled(newConfig.getBotEnabled());
                                existingConfig.setGroupInviteLink(null); // preserve existing invite link
                        } else {
                                entity.setTelegramGroupConfig(newConfig);
                        }
                } else {
                        // All telegram fields are null in request -> remove config
                        entity.setTelegramGroupConfig(null);
                }

                var newZadarmaConfig = buildZadarmaConfig(request);
                if (newZadarmaConfig != null) {
                        var existingZadarma = entity.getZadarmaConfig();
                        if (existingZadarma != null) {
                                existingZadarma.setSipId(newZadarmaConfig.getSipId());
                                if (newZadarmaConfig.getRedirectionType() != null) {
                                        existingZadarma.setRedirectionType(newZadarmaConfig.getRedirectionType());
                                }
                                if (request.zadarmaActive() != null) {
                                        existingZadarma.setActive(request.zadarmaActive());
                                }
                        } else {
                                entity.setZadarmaConfig(newZadarmaConfig);
                        }
                } else {
                        // zadarmaSipId is null in request -> remove config (orphanRemoval handles DB
                        // delete)
                        entity.setZadarmaConfig(null);
                }
        }

        /**
         * Builds a TelegramGroupConfig from request fields, or returns null
         * if no telegram fields are set.
         */
        private static TelegramGroupConfig buildTelegramConfig(OnCallGroupRequest request) {
                boolean hasTelegram = request.telegramBotToken() != null
                                || request.telegramGroupChatId() != null
                                || request.telegramBotEnabled() != null;

                if (!hasTelegram) {
                        return null;
                }

                return TelegramGroupConfig.builder()
                                .botToken(request.telegramBotToken())
                                .groupChatId(request.telegramGroupChatId())
                                .botEnabled(request.telegramBotEnabled() != null ? request.telegramBotEnabled() : false)
                                .build();
        }

        /**
         * Builds a ZadarmaGroupConfig from request fields, or returns null
         * if zadarmaSipId is not provided.
         */
        private static ZadarmaGroupConfig buildZadarmaConfig(OnCallGroupRequest request) {
                if (request.zadarmaSipId() == null) {
                        return null;
                }

                return ZadarmaGroupConfig.builder()
                                .sipId(request.zadarmaSipId())
                                .redirectionType(request.zadarmaRedirectionType() != null
                                                ? request.zadarmaRedirectionType()
                                                : "sip")
                                .active(request.zadarmaActive() != null ? request.zadarmaActive() : true)
                                .build();
        }
}
