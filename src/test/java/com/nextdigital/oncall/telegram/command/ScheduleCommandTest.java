package com.nextdigital.oncall.telegram.command;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextdigital.oncall.dto.ShiftResponse;
import com.nextdigital.oncall.service.ShiftService;

@ExtendWith(MockitoExtension.class)
class ScheduleCommandTest {

    @Mock
    private ShiftService shiftService;

    @InjectMocks
    private ScheduleCommand command;

    private UUID groupId;
    private String chatId;
    private String fromId;

    @BeforeEach
    void setUp() {
        groupId = UUID.randomUUID();
        chatId = "123456789";
        fromId = "987654321";
    }

    @Test
    void execute_withNullGroupId_returnsError() {
        var result = command.execute(new String[] {}, null, chatId, fromId);

        assertThat(result).isNotNull();
        assertThat(result).contains("No se pudo determinar");
    }

    @Test
    void execute_withNoShifts_returnsEmptyMessage() {
        when(shiftService.findAll(any(), any(), any(), any()))
                .thenReturn(List.of());

        var result = command.execute(new String[] {}, groupId, chatId, fromId);

        assertThat(result).isNotNull();
        assertThat(result).contains("No hay guardias");
    }

    @Test
    void execute_withShifts_returnsSchedule() {
        var now = OffsetDateTime.now();
        var shift = new ShiftResponse(
                UUID.randomUUID(), groupId, "Test Group",
                UUID.randomUUID(), "Alice", "alice@test.com",
                now, now.plusDays(7),
                now, null, now);

        when(shiftService.findAll(any(), any(), any(), any()))
                .thenReturn(List.of(shift));

        var result = command.execute(new String[] {}, groupId, chatId, fromId);

        assertThat(result).isNotNull();
        assertThat(result).contains("Alice");
        assertThat(result).contains("Guardias");
    }

    @Test
    void execute_withMultipleShifts_listsAll() {
        var now = OffsetDateTime.now();
        var shift1 = new ShiftResponse(
                UUID.randomUUID(), groupId, "Test Group",
                UUID.randomUUID(), "Alice", "alice@test.com",
                now, now.plusDays(3),
                now, null, now);
        var shift2 = new ShiftResponse(
                UUID.randomUUID(), groupId, "Test Group",
                UUID.randomUUID(), "Bob", "bob@test.com",
                now.plusDays(3), now.plusDays(7),
                now, null, now);

        when(shiftService.findAll(any(), any(), any(), any()))
                .thenReturn(List.of(shift1, shift2));

        var result = command.execute(new String[] {}, groupId, chatId, fromId);

        assertThat(result).isNotNull();
        assertThat(result).contains("Alice");
        assertThat(result).contains("Bob");
    }
}
