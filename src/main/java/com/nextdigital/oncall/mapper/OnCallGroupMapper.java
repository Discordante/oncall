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

                return new OnCallGroupResponse(
                                entity.getId(),
                                entity.getName(),
                                entity.getGrossPricePerDay(),
                                entity.getTelegramGroupChatId(),
                                entity.getTelegramBotEnabled() != null && entity.getTelegramBotEnabled(),
                                entity.getTelegramGroupInviteLink(),
                                applications,
                                people,
                                entity.getCreatedAt(),
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

                if (request.telegramBotToken() != null) {
                        group.setTelegramBotToken(request.telegramBotToken());
                }
                if (request.telegramGroupChatId() != null) {
                        group.setTelegramGroupChatId(request.telegramGroupChatId());
                }
                if (request.telegramBotEnabled() != null) {
                        group.setTelegramBotEnabled(request.telegramBotEnabled());
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

                if (request.telegramBotToken() != null) {
                        entity.setTelegramBotToken(request.telegramBotToken());
                }
                if (request.telegramGroupChatId() != null) {
                        entity.setTelegramGroupChatId(request.telegramGroupChatId());
                }
                if (request.telegramBotEnabled() != null) {
                        entity.setTelegramBotEnabled(request.telegramBotEnabled());
                }
        }
}
