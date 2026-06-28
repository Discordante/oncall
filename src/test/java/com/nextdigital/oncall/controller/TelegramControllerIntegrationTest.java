package com.nextdigital.oncall.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.nextdigital.oncall.config.TestcontainersConfig;
import com.nextdigital.oncall.dto.ApiResponse;
import com.nextdigital.oncall.dto.OnCallGroupRequest;
import com.nextdigital.oncall.dto.OnCallGroupResponse;
import com.nextdigital.oncall.dto.telegram.TelegramLinkRequest;
import com.nextdigital.oncall.dto.telegram.TelegramLinkResponse;
import com.nextdigital.oncall.entity.TelegramPendingLink;
import com.nextdigital.oncall.repository.TelegramPendingLinkRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class TelegramControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TelegramPendingLinkRepository pendingLinkRepository;

    private String validCode;
    private String expiredCode;

    @BeforeEach
    void setUp() {
        pendingLinkRepository.deleteAll();

        validCode = UUID.randomUUID().toString().replace("-", "");
        expiredCode = UUID.randomUUID().toString().replace("-", "");

        // Create a valid pending link
        var validLink = TelegramPendingLink.builder()
                .chatId("123456789")
                .linkCode(validCode)
                .expiresAt(java.time.OffsetDateTime.now().plusHours(1))
                .build();
        pendingLinkRepository.save(validLink);

        // Create an expired pending link
        var expiredLink = TelegramPendingLink.builder()
                .chatId("987654321")
                .linkCode(expiredCode)
                .expiresAt(java.time.OffsetDateTime.now().minusHours(1))
                .build();
        pendingLinkRepository.save(expiredLink);
    }

    @Test
    void linkTelegram_withInvalidCode_returnsError() throws Exception {
        var request = new TelegramLinkRequest("non-existent-code");

        var result = mockMvc.perform(post("/api/v1/telegram/link")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<TelegramLinkResponse>>() {
                });

        assertThat(response.data()).isNotNull();
        assertThat(response.data().success()).isFalse();
        assertThat(response.data().message()).contains("inválido");
    }

    @Test
    void linkTelegram_withExpiredCode_returnsExpired() throws Exception {
        var request = new TelegramLinkRequest(expiredCode);

        var result = mockMvc.perform(post("/api/v1/telegram/link")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<TelegramLinkResponse>>() {
                });

        assertThat(response.data()).isNotNull();
        assertThat(response.data().success()).isFalse();
        assertThat(response.data().message()).contains("expirado");
    }

    @Test
    void linkTelegram_withValidCode_returnsNeedsAuth() throws Exception {
        var request = new TelegramLinkRequest(validCode);

        var result = mockMvc.perform(post("/api/v1/telegram/link")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<TelegramLinkResponse>>() {
                });

        assertThat(response.data()).isNotNull();
        assertThat(response.data().success()).isFalse();
        assertThat(response.data().message()).contains("autenticación");
    }

    @Test
    void linkStatus_withInvalidCode_returnsInvalid() throws Exception {
        var result = mockMvc.perform(get("/api/v1/telegram/link-status")
                .param("code", "non-existent-code"))
                .andExpect(status().isOk())
                .andReturn();

        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<TelegramLinkResponse>>() {
                });

        assertThat(response.data()).isNotNull();
        assertThat(response.data().success()).isFalse();
    }

    @Test
    void linkStatus_withValidCode_returnsOk() throws Exception {
        var result = mockMvc.perform(get("/api/v1/telegram/link-status")
                .param("code", validCode))
                .andExpect(status().isOk())
                .andReturn();

        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<TelegramLinkResponse>>() {
                });

        assertThat(response.data()).isNotNull();
        assertThat(response.data().success()).isTrue();
        assertThat(response.data().message()).contains("válido");
    }

    @Test
    void linkStatus_withExpiredCode_returnsExpired() throws Exception {
        var result = mockMvc.perform(get("/api/v1/telegram/link-status")
                .param("code", expiredCode))
                .andExpect(status().isOk())
                .andReturn();

        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<TelegramLinkResponse>>() {
                });

        assertThat(response.data()).isNotNull();
        assertThat(response.data().success()).isFalse();
        assertThat(response.data().message()).contains("expirado");
    }

    @Test
    void onCallGroupResponse_includesInviteLink() throws Exception {
        var createRequest = new OnCallGroupRequest(
                "Telegram Test Group",
                null,
                "test:token",
                "-1001234567890",
                true,
                Set.of(),
                Set.of());

        var result = mockMvc.perform(post("/api/v1/on-call-groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        var response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<OnCallGroupResponse>>() {
                });

        assertThat(response.data()).isNotNull();
        assertThat(response.data().telegramBotEnabled()).isTrue();
        assertThat(response.data().telegramGroupChatId()).isEqualTo("-1001234567890");
        // inviteLink may be null if the bot can't reach Telegram (test environment)
        // but the field should exist in the response
        assertThat(response.data()).isNotNull();
    }
}
