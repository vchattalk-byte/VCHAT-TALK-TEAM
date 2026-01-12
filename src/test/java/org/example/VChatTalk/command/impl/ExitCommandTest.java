package org.example.VChatTalk.command.impl;

import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
import org.example.VChatTalk.command.MessageConstants;
import org.example.VChatTalk.util.SystemResponseSender;
import org.example.VChatTalk.util.UserRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExitCommandTest {

    @Mock private UserRegistry userRegistry;
    @Mock private SystemResponseSender responder;
    @Mock private WebSocketSession session;

    private ExitCommand exitCommand;

    @BeforeEach
    void setUp() {
        exitCommand = new ExitCommand(userRegistry, responder);
    }

    @Test
    void testGetType() {
        assertEquals(CommandType.EXIT, exitCommand.getType());
    }

    @Test
    @DisplayName("Send goodbye if registered")
    void testExecute_RegisteredUser() throws IOException {
        when(session.getId()).thenReturn("s1");
        when(userRegistry.getUsername("s1")).thenReturn("Alice");

        exitCommand.execute(session, new CommandResult(CommandType.EXIT, null, null));

        verify(responder).sendSystem(session, String.format(MessageConstants.MSG_GOODBYE, "Alice"));
        verify(session).close(CloseStatus.NORMAL);
    }

    @Test
    @DisplayName("Do NOT send goodbye if username is 'Anonymous'")
    void testExecute_AnonymousUser() throws IOException {
        when(session.getId()).thenReturn("s1");
        when(userRegistry.getUsername("s1")).thenReturn("Anonymous");

        exitCommand.execute(session, new CommandResult(CommandType.EXIT, null, null));

        verify(responder, never()).sendSystem(any(), anyString());
        verify(session).close(CloseStatus.NORMAL);
    }
}