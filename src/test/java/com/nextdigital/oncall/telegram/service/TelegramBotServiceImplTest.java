package com.nextdigital.oncall.telegram.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextdigital.oncall.entity.OnCallGroup;
import com.nextdigital.oncall.entity.Person;
import com.nextdigital.oncall.entity.TelegramGroupConfig;
import com.nextdigital.oncall.entity.TelegramPendingLink;
import com.nextdigital.oncall.repository.OnCallGroupRepository;
import com.nextdigital.oncall.repository.PersonRepository;
import com.nextdigital.oncall.repository.TelegramPendingLinkRepository;
import com.nextdigital.oncall.telegram.bot.CommandRouter;
import com.nextdigital.oncall.telegram.client.TelegramApiClient;
import com.nextdigital.oncall.telegram.config.TelegramBotConfig;

@ExtendWith(MockitoExtension.class)
class TelegramBotServiceImplTest {

    @Mock
    private TelegramPendingLinkRepository pendingLinkRepository;

    @Mock
    private PersonRepository personRepository;

    @Mock
    private OnCallGroupRepository onCallGroupRepository;

    @Mock
    private TelegramApiClient apiClient;

    @Mock
    private TelegramBotConfig botConfig;

    @Mock
    private CommandRouter commandRouter;

    @InjectMocks
    private TelegramBotServiceImpl service;

    private UUID groupId;
    private UUID personId;
    private OnCallGroup group;
    private Person person;
    private TelegramPendingLink pendingLink;
    private String validCode;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        personId = UUID.randomUUID();
        validCode = UUID.randomUUID().toString().replace("-", "");

        var config = TelegramGroupConfig.builder()
                .botToken("test:token")
                .groupChatId("-1001234567890")
                .botEnabled(true)
                .build();

        group = OnCallGroup.builder()
                .id(groupId)
                .name("Test Group")
                .telegramGroupConfig(config)
                .build();

        person = Person.builder()
                .id(personId)
                .name("Alice")
                .email("alice@test.com")
                .isActive(true)
                .build();

        pendingLink = TelegramPendingLink.builder()
                .id(UUID.randomUUID())
                .userId("555666777")
                .chatId("123456789")
                .chatType("private")
                .linkCode(validCode)
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .build();
    }

    // ========================================================================
    // linkTelegramAccount
    // ========================================================================

    @Test
    void linkTelegramAccount_withInvalidCode_returnsError() {
        when(pendingLinkRepository.findByLinkCode(validCode)).thenReturn(Optional.empty());

        var result = service.linkTelegramAccount(validCode);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("inválido");
    }

    @Test
    void linkTelegramAccount_withExpiredCode_returnsError() {
        var expiredLink = TelegramPendingLink.builder()
                .id(UUID.randomUUID())
                .chatId("123456789")
                .linkCode(validCode)
                .expiresAt(OffsetDateTime.now().minusHours(1))
                .build();
        when(pendingLinkRepository.findByLinkCode(validCode)).thenReturn(Optional.of(expiredLink));

        var result = service.linkTelegramAccount(validCode);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("expirado");
        verify(pendingLinkRepository).delete(expiredLink);
    }

    @Test
    void linkTelegramAccount_withValidCode_returnsNeedsAuth() {
        when(pendingLinkRepository.findByLinkCode(validCode)).thenReturn(Optional.of(pendingLink));

        var result = service.linkTelegramAccount(validCode);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("autenticación");
    }

    // ========================================================================
    // getLinkStatus
    // ========================================================================

    @Test
    void getLinkStatus_withInvalidCode_returnsError() {
        when(pendingLinkRepository.findByLinkCode(validCode)).thenReturn(Optional.empty());

        var result = service.getLinkStatus(validCode);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("inválido");
    }

    @Test
    void getLinkStatus_withExpiredCode_returnsExpired() {
        var expiredLink = TelegramPendingLink.builder()
                .id(UUID.randomUUID())
                .chatId("123456789")
                .linkCode(validCode)
                .expiresAt(OffsetDateTime.now().minusHours(1))
                .build();
        when(pendingLinkRepository.findByLinkCode(validCode)).thenReturn(Optional.of(expiredLink));

        var result = service.getLinkStatus(validCode);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("expirado");
    }

    @Test
    void getLinkStatus_withValidCode_returnsOk() {
        when(pendingLinkRepository.findByLinkCode(validCode)).thenReturn(Optional.of(pendingLink));

        var result = service.getLinkStatus(validCode);

        assertThat(result.success()).isTrue();
    }

    // ========================================================================
    // linkForPerson
    // ========================================================================

    @Test
    void linkForPerson_withValidData_linksAccount() {
        when(pendingLinkRepository.findByLinkCode(validCode)).thenReturn(Optional.of(pendingLink));
        when(personRepository.findById(personId)).thenReturn(Optional.of(person));
        when(personRepository.findByTelegramUserId(pendingLink.getUserId())).thenReturn(Optional.empty());
        when(personRepository.findByTelegramChatId(pendingLink.getChatId())).thenReturn(Optional.empty());

        var result = service.linkForPerson(personId, validCode);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("vinculada");
        assertThat(person.getTelegramUserId()).isEqualTo("555666777");
        assertThat(person.getTelegramChatId()).isEqualTo("123456789");
        assertThat(person.getTelegramLinkedAt()).isNotNull();
        verify(personRepository).save(person);
        verify(pendingLinkRepository).delete(pendingLink);
    }

    @Test
    void linkForPerson_withInvalidCode_returnsError() {
        when(pendingLinkRepository.findByLinkCode(validCode)).thenReturn(Optional.empty());

        var result = service.linkForPerson(personId, validCode);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("inválido");
    }

    @Test
    void linkForPerson_withExpiredCode_returnsError() {
        var expiredLink = TelegramPendingLink.builder()
                .id(UUID.randomUUID())
                .chatId("123456789")
                .linkCode(validCode)
                .expiresAt(OffsetDateTime.now().minusHours(1))
                .build();
        when(pendingLinkRepository.findByLinkCode(validCode)).thenReturn(Optional.of(expiredLink));

        var result = service.linkForPerson(personId, validCode);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("expirado");
        verify(pendingLinkRepository).delete(expiredLink);
    }

    @Test
    void linkForPerson_withUnknownPerson_returnsError() {
        when(pendingLinkRepository.findByLinkCode(validCode)).thenReturn(Optional.of(pendingLink));
        when(personRepository.findById(personId)).thenReturn(Optional.empty());

        var result = service.linkForPerson(personId, validCode);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("no encontrado");
    }

    @Test
    void linkForPerson_whenUserAlreadyLinked_unlinksPrevious() {
        var previousPerson = Person.builder()
                .id(UUID.randomUUID())
                .name("Bob")
                .email("bob@test.com")
                .telegramUserId("555666777")
                .build();

        when(pendingLinkRepository.findByLinkCode(validCode)).thenReturn(Optional.of(pendingLink));
        when(personRepository.findById(personId)).thenReturn(Optional.of(person));
        when(personRepository.findByTelegramUserId(pendingLink.getUserId()))
                .thenReturn(Optional.of(previousPerson));
        when(personRepository.findByTelegramChatId(pendingLink.getChatId())).thenReturn(Optional.empty());

        var result = service.linkForPerson(personId, validCode);

        assertThat(result.success()).isTrue();
        assertThat(previousPerson.getTelegramUserId()).isNull();
        assertThat(previousPerson.getTelegramChatId()).isNull();
        assertThat(previousPerson.getTelegramLinkedAt()).isNull();
        assertThat(person.getTelegramUserId()).isEqualTo("555666777");
        assertThat(person.getTelegramChatId()).isEqualTo("123456789");
        verify(personRepository).save(previousPerson);
        verify(personRepository).save(person);
    }

    @Test
    void linkForPerson_whenStartWasInGroup_doesNotStoreGroupChatId() {
        var groupLink = TelegramPendingLink.builder()
                .id(UUID.randomUUID())
                .userId("555666777")
                .chatId("-1001234567890")
                .chatType("group")
                .linkCode(validCode)
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .build();

        when(pendingLinkRepository.findByLinkCode(validCode)).thenReturn(Optional.of(groupLink));
        when(personRepository.findById(personId)).thenReturn(Optional.of(person));
        when(personRepository.findByTelegramUserId(groupLink.getUserId())).thenReturn(Optional.empty());

        var result = service.linkForPerson(personId, validCode);

        assertThat(result.success()).isTrue();
        assertThat(person.getTelegramUserId()).isEqualTo("555666777");
        assertThat(person.getTelegramChatId()).isNull();
        verify(personRepository, never()).findByTelegramChatId(any());
        verify(pendingLinkRepository).delete(groupLink);
    }

    // ========================================================================
    // registerGroupBot / unregisterGroupBot / initializeAllBots
    // ========================================================================

    @Test
    void registerGroupBot_withValidGroup_registersBot() {
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        service.registerGroupBot(groupId);

        assertThat(service.getActiveBotGroupIds()).contains(groupId);
    }

    @Test
    void registerGroupBot_withDisabledGroup_doesNotRegister() {
        group.getTelegramGroupConfig().setBotEnabled(false);
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        service.registerGroupBot(groupId);

        assertThat(service.getActiveBotGroupIds()).doesNotContain(groupId);
    }

    @Test
    void registerGroupBot_withMissingToken_doesNotRegister() {
        group.getTelegramGroupConfig().setBotToken(null);
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        service.registerGroupBot(groupId);

        assertThat(service.getActiveBotGroupIds()).doesNotContain(groupId);
    }

    @Test
    void unregisterGroupBot_removesBot() {
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        service.registerGroupBot(groupId);
        assertThat(service.getActiveBotGroupIds()).contains(groupId);

        service.unregisterGroupBot(groupId);

        assertThat(service.getActiveBotGroupIds()).doesNotContain(groupId);
    }

    @Test
    void initializeAllBots_registersAllEnabledGroups() {
        when(onCallGroupRepository.findByTelegramBotEnabledTrue()).thenReturn(List.of(group));
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        service.initializeAllBots();

        assertThat(service.getActiveBotGroupIds()).contains(groupId);
    }

    // ========================================================================
    // generateGroupInviteLink
    // ========================================================================

    @Test
    void generateGroupInviteLink_withValidGroup_generatesLink() {
        var config = group.getTelegramGroupConfig();
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(apiClient.verifyBotInGroup(config.getBotToken(), config.getGroupChatId()))
                .thenReturn(true);
        when(apiClient.createChatInviteLink(config.getBotToken(), config.getGroupChatId()))
                .thenReturn("https://t.me/+AbCdEf1234");
        when(onCallGroupRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.generateGroupInviteLink(groupId);

        assertThat(result).isEqualTo("https://t.me/+AbCdEf1234");
        verify(onCallGroupRepository).save(group);
        assertThat(config.getGroupInviteLink()).isEqualTo("https://t.me/+AbCdEf1234");
    }

    @Test
    void generateGroupInviteLink_withUnknownGroup_returnsNull() {
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.empty());

        var result = service.generateGroupInviteLink(groupId);

        assertThat(result).isNull();
    }

    @Test
    void generateGroupInviteLink_withMissingConfig_returnsNull() {
        group.getTelegramGroupConfig().setBotToken(null);
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        var result = service.generateGroupInviteLink(groupId);

        assertThat(result).isNull();
    }

    @Test
    void generateGroupInviteLink_whenBotNotInGroup_returnsNull() {
        var config = group.getTelegramGroupConfig();
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(apiClient.verifyBotInGroup(config.getBotToken(), config.getGroupChatId()))
                .thenReturn(false);

        var result = service.generateGroupInviteLink(groupId);

        assertThat(result).isNull();
        verify(apiClient, never()).createChatInviteLink(any(), any());
    }

    // ========================================================================
    // notifyGroup / notifyPerson
    // ========================================================================

    @Test
    void notifyGroup_withActiveBot_sendsMessage() {
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        service.registerGroupBot(groupId);

        service.notifyGroup(groupId, "Test message");

        verify(apiClient).sendMessage("test:token", "-1001234567890", "Test message");
    }

    @Test
    void notifyGroup_withoutActiveBot_doesNothing() {
        service.notifyGroup(groupId, "Test message");

        verify(apiClient, never()).sendMessage(any(), any(), any());
    }

    @Test
    void notifyPerson_withLinkedPerson_sendsMessage() {
        var chatId = "123456789";
        person.setTelegramChatId(chatId);

        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        service.registerGroupBot(groupId);

        when(personRepository.findById(personId)).thenReturn(Optional.of(person));

        service.notifyPerson(personId, "Personal message");

        verify(apiClient).sendMessage("test:token", chatId, "Personal message");
    }

    @Test
    void notifyPerson_withoutLinkedChat_doesNothing() {
        when(personRepository.findById(personId)).thenReturn(Optional.of(person));

        service.notifyPerson(personId, "Personal message");

        verify(apiClient, never()).sendMessage(any(), any(), any());
    }

    // ========================================================================
    // /start handling via pollUpdates
    // ========================================================================

    @Test
    void pollUpdates_startAlreadyLinked_resolvesByUserId() {
        when(botConfig.isPollingEnabled()).thenReturn(true);
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        service.registerGroupBot(groupId);

        var fromId = "555666777";
        var groupChatId = "-1001234567890";
        var update = updateWith("/start", groupChatId, "group", fromId);
        when(apiClient.getUpdates("test:token", 0L, 2)).thenReturn(List.of(update));
        when(personRepository.findByTelegramUserId(fromId)).thenReturn(Optional.of(person));

        service.pollUpdates();

        verify(apiClient).sendMessage(eq("test:token"), eq(groupChatId), argThat(m -> m.contains("Alice")));
        verify(pendingLinkRepository, never()).save(any());
    }

    @Test
    void pollUpdates_startNotLinked_createsPendingLinkWithUserIdAndChatType() {
        when(botConfig.isPollingEnabled()).thenReturn(true);
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        service.registerGroupBot(groupId);

        var fromId = "555666777";
        var groupChatId = "-1001234567890";
        var update = updateWith("/start", groupChatId, "group", fromId);
        when(apiClient.getUpdates("test:token", 0L, 2)).thenReturn(List.of(update));
        when(personRepository.findByTelegramUserId(fromId)).thenReturn(Optional.empty());
        when(pendingLinkRepository.findByUserId(fromId)).thenReturn(Optional.empty());

        service.pollUpdates();

        var captor = ArgumentCaptor.forClass(TelegramPendingLink.class);
        verify(pendingLinkRepository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(fromId);
        assertThat(saved.getChatType()).isEqualTo("group");
        assertThat(saved.getChatId()).isEqualTo(groupChatId);
        assertThat(saved.getLinkCode()).isNotBlank();
    }

    @Test
    void pollUpdates_startAlreadyLinkedInPrivateChat_healsChatId() {
        when(botConfig.isPollingEnabled()).thenReturn(true);
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        service.registerGroupBot(groupId);

        var fromId = "555666777";
        var privateChatId = "999000111";
        person.setTelegramUserId(fromId);
        person.setTelegramChatId(null);

        var update = updateWith("/start", privateChatId, "private", fromId);
        when(apiClient.getUpdates("test:token", 0L, 2)).thenReturn(List.of(update));
        when(personRepository.findByTelegramUserId(fromId)).thenReturn(Optional.of(person));

        service.pollUpdates();

        assertThat(person.getTelegramChatId()).isEqualTo(privateChatId);
        verify(personRepository).save(person);
    }

    private TelegramApiClient.TelegramUpdate updateWith(String text, String chatId, String chatType, String fromId) {
        return new TelegramApiClient.TelegramUpdate(
                1L,
                new TelegramApiClient.TelegramMessage(
                        1L,
                        new TelegramApiClient.TelegramChat(Long.parseLong(chatId), chatType, "Chat", null, null),
                        new TelegramApiClient.TelegramFrom(Long.parseLong(fromId), false, "Sender", null, null, null),
                        text,
                        new TelegramApiClient.TelegramEntity[] {
                                new TelegramApiClient.TelegramEntity(0, 6, "bot_command")
                        }));
    }
}
