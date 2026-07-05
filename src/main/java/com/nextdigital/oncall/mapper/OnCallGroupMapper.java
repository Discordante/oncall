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
                                entity.getZadarmaSipId(),
                                entity.getZadarmaRedirectionType(),
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

                if (request.zadarmaSipId() != null) {
                        group.setZadarmaSipId(request.zadarmaSipId());
                }
                if (request.zadarmaRedirectionType() != null) {
                        group.setZadarmaRedirectionType(request.zadarmaRedirectionType());
                }
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

                if (request.zadarmaSipId() != null) {
                        entity.setZadarmaSipId(request.zadarmaSipId());
                }
                if (request.zadarmaRedirectionType() != null) {
                        entity.setZadarmaRedirectionType(request.zadarmaRedirectionType());
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
}
