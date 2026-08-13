package com.nextdigital.oncall.telegram.bot;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextdigital.oncall.entity.Person;
import com.nextdigital.oncall.repository.PersonRepository;
import com.nextdigital.oncall.telegram.command.InfoCommand;
import com.nextdigital.oncall.telegram.command.ScheduleCommand;

@ExtendWith(MockitoExtension.class)
class CommandRouterTest {

    @Mock
    private InfoCommand infoCommand;

    @Mock
    private ScheduleCommand scheduleCommand;

    @Mock
    private PersonRepository personRepository;

    @InjectMocks
    private CommandRouter router;

    private UUID groupId;
    private String linkedChatId;
    private String unlinkedChatId;
    private String fromId;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        linkedChatId = "999888777";
        unlinkedChatId = "111222333";
        fromId = "12345";

        router.registerHandlers();
    }

    @Test
    void route_nullText_returnsNull() {
        var result = router.route(null, groupId, linkedChatId, fromId);
        assertThat(result).isNull();
    }

    @Test
    void route_blankText_returnsNull() {
        var result = router.route("   ", groupId, linkedChatId, fromId);
        assertThat(result).isNull();
    }

    @Test
    void route_nonCommand_returnsNull() {
        var result = router.route("hola", groupId, linkedChatId, fromId);
        assertThat(result).isNull();
    }

    @Test
    void route_infoCommand_returnsHelp() {
        when(personRepository.findByTelegramUserId(fromId))
                .thenReturn(Optional.of(Person.builder().id(UUID.randomUUID()).telegramUserId(fromId).build()));
        when(infoCommand.execute(new String[] {}, groupId, linkedChatId, fromId))
                .thenReturn("🤖 *Comandos disponibles:*");

        var result = router.route("/info", groupId, linkedChatId, fromId);

        assertThat(result).isNotNull();
        assertThat(result).contains("Comandos disponibles");
    }

    @Test
    void route_turnosCommand_returnsSchedule() {
        when(personRepository.findByTelegramUserId(fromId))
                .thenReturn(Optional.of(Person.builder().id(UUID.randomUUID()).telegramUserId(fromId).build()));
        when(scheduleCommand.execute(new String[] {}, groupId, linkedChatId, fromId))
                .thenReturn("📅 *Guardias*");

        var result = router.route("/turnos", groupId, linkedChatId, fromId);

        assertThat(result).isNotNull();
        assertThat(result).contains("Guardias");
    }

    @Test
    void route_unknownCommand_returnsError() {
        when(personRepository.findByTelegramUserId(fromId))
                .thenReturn(Optional.of(Person.builder().id(UUID.randomUUID()).telegramUserId(fromId).build()));

        var result = router.route("/unknown", groupId, linkedChatId, fromId);

        assertThat(result).isNotNull();
        assertThat(result).contains("no reconocido");
    }

    @Test
    void route_unlinkedUser_cannotUseInfo() {
        var unlinkedFromId = "999999999";
        when(personRepository.findByTelegramUserId(unlinkedFromId)).thenReturn(Optional.empty());

        var result = router.route("/info", groupId, unlinkedChatId, unlinkedFromId);

        assertThat(result).isNotNull();
        assertThat(result).contains("No tienes tu cuenta vinculada");
    }

    @Test
    void route_unlinkedUser_cannotUseTurnos() {
        var unlinkedFromId = "999999999";
        when(personRepository.findByTelegramUserId(unlinkedFromId)).thenReturn(Optional.empty());

        var result = router.route("/turnos", groupId, unlinkedChatId, unlinkedFromId);

        assertThat(result).isNotNull();
        assertThat(result).contains("No tienes tu cuenta vinculada");
    }

    @Test
    void route_turnosWithArgs_passesArguments() {
        when(personRepository.findByTelegramUserId(fromId))
                .thenReturn(Optional.of(Person.builder().id(UUID.randomUUID()).telegramUserId(fromId).build()));
        when(scheduleCommand.execute(new String[] { "arg1" }, groupId, linkedChatId, fromId))
                .thenReturn("📅 Result");

        var result = router.route("/turnos arg1", groupId, linkedChatId, fromId);

        assertThat(result).isEqualTo("📅 Result");
    }
}
