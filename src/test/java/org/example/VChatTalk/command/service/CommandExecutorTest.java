package org.example.VChatTalk.command.service;

import org.example.VChatTalk.command.CommandExecutor;
import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
import org.example.VChatTalk.command.IChatCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommandExecutorTest {

    @Mock
    private WebSocketSession session;

    @Mock
    private IChatCommand joinCommand;

    @Mock
    private IChatCommand unknownCommand;

    private CommandExecutor commandExecutor;

    @BeforeEach
    void setUp() {
        when(joinCommand.getType()).thenReturn(CommandType.JOIN);
        when(unknownCommand.getType()).thenReturn(CommandType.UNKNOWN);

        commandExecutor = new CommandExecutor(
                List.of(joinCommand, unknownCommand)
        );
    }

    @Test
    @DisplayName("Executes command when type exists")
    void execute_ValidCommand() throws IOException {
        CommandResult result = new CommandResult(
                CommandType.JOIN,
                "room1",
                null
        );

        commandExecutor.execute(session, result);

        verify(joinCommand, times(1)).execute(session, result);
        verify(unknownCommand, never()).execute(any(), any());
    }

    @Test
    @DisplayName("Executes UNKNOWN command when type is missing")
    void execute_UnknownCommand() throws IOException {
        CommandResult result = new CommandResult(
                CommandType.EXIT,
                null,
                null
        );

        commandExecutor.execute(session, result);

        verify(unknownCommand, times(1)).execute(session, result);
        verify(joinCommand, never()).execute(any(), any());
    }

    @Test
    @DisplayName("Returns correct command for given type")
    void getCommand_ExistingType() {
        IChatCommand command = commandExecutor.getCommand(CommandType.JOIN);

        assertEquals(joinCommand, command);
    }
}
