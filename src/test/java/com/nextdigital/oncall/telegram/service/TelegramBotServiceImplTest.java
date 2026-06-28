package com.nextdigital.oncall.telegram.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextdigital.oncall.entity.OnCallGroup;
import com.nextdigital.oncall.entity.Person;
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

        group = OnCallGroup.builder()
                .id(groupId)
                .name("Test Group")
                .telegramBotToken("test:token")
                .telegramGroupChatId("-1001234567890")
                .telegramBotEnabled(true)
                .build();

        person = Person.builder()
                .id(personId)
                .name("Alice")
                .email("alice@test.com")
                .isActive(true)
                .build();

        pendingLink = TelegramPendingLink.builder()
                .id(UUID.randomUUID())
                .chatId("123456789")
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
        when(personRepository.findByTelegramChatId(pendingLink.getChatId())).thenReturn(Optional.empty());

        var result = service.linkForPerson(personId, validCode);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("vinculada");
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
    void linkForPerson_whenChatAlreadyLinked_unlinksPrevious() {
        var previousPerson = Person.builder()
                .id(UUID.randomUUID())
                .name("Bob")
                .email("bob@test.com")
                .telegramChatId("123456789")
                .build();

        when(pendingLinkRepository.findByLinkCode(validCode)).thenReturn(Optional.of(pendingLink));
        when(personRepository.findById(personId)).thenReturn(Optional.of(person));
        when(personRepository.findByTelegramChatId(pendingLink.getChatId())).thenReturn(Optional.of(previousPerson));

        var result = service.linkForPerson(personId, validCode);

        assertThat(result.success()).isTrue();
        assertThat(previousPerson.getTelegramChatId()).isNull();
        assertThat(person.getTelegramChatId()).isEqualTo("123456789");
        verify(personRepository).save(previousPerson);
        verify(personRepository).save(person);
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
        group.setTelegramBotEnabled(false);
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        service.registerGroupBot(groupId);

        assertThat(service.getActiveBotGroupIds()).doesNotContain(groupId);
    }

    @Test
    void registerGroupBot_withMissingToken_doesNotRegister() {
        group.setTelegramBotToken(null);
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
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(apiClient.verifyBotInGroup(group.getTelegramBotToken(), group.getTelegramGroupChatId()))
                .thenReturn(true);
        when(apiClient.createChatInviteLink(group.getTelegramBotToken(), group.getTelegramGroupChatId()))
                .thenReturn("https://t.me/+AbCdEf1234");
        when(onCallGroupRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.generateGroupInviteLink(groupId);

        assertThat(result).isEqualTo("https://t.me/+AbCdEf1234");
        verify(onCallGroupRepository).save(group);
        assertThat(group.getTelegramGroupInviteLink()).isEqualTo("https://t.me/+AbCdEf1234");
    }

    @Test
    void generateGroupInviteLink_withUnknownGroup_returnsNull() {
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.empty());

        var result = service.generateGroupInviteLink(groupId);

        assertThat(result).isNull();
    }

    @Test
    void generateGroupInviteLink_withMissingConfig_returnsNull() {
        group.setTelegramBotToken(null);
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));

        var result = service.generateGroupInviteLink(groupId);

        assertThat(result).isNull();
    }

    @Test
    void generateGroupInviteLink_whenBotNotInGroup_returnsNull() {
        when(onCallGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        when(apiClient.verifyBotInGroup(group.getTelegramBotToken(), group.getTelegramGroupChatId()))
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
}
