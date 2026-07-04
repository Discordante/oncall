package com.nextdigital.oncall.telegram.command;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class InfoCommandTest {

    private final InfoCommand command = new InfoCommand();

    @Test
    void execute_returnsHelpText() {
        var result = command.execute(new String[] {}, UUID.randomUUID(), "chatId", "fromId");

        assertThat(result).isNotNull();
        assertThat(result).contains("/info");
        assertThat(result).contains("/turnos");
        assertThat(result).contains("/start");
        assertThat(result).contains("Comandos disponibles");
    }

    @Test
    void execute_withArgs_ignoresArgs() {
        var resultWithArgs = command.execute(new String[] { "arg1", "arg2" }, UUID.randomUUID(), "chatId", "fromId");
        var resultWithoutArgs = command.execute(new String[] {}, UUID.randomUUID(), "chatId", "fromId");

        assertThat(resultWithArgs).isEqualTo(resultWithoutArgs);
    }

    @Test
    void execute_withNullGroupId_stillWorks() {
        var result = command.execute(new String[] {}, null, "chatId", "fromId");

        assertThat(result).isNotNull();
        assertThat(result).contains("/info");
    }
}
