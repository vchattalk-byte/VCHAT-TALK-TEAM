package org.example.VChatTalk.command.implement_command;

import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
import org.example.VChatTalk.command.MessageConstants;
import org.example.VChatTalk.util.SessionRegistry;
import org.example.VChatTalk.util.SystemResponseSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListCommandTest {

    @Mock private SessionRegistry sessionRegistry;
    @Mock private SystemResponseSender responder;
    @Mock private WebSocketSession session;
    @Mock private WebSocketSession otherSession;

    private ListCommand listCommand;

    @BeforeEach
    void setUp() {
        listCommand = new ListCommand(sessionRegistry, responder);
    }

    @Test
    void testGetType() {
        assertEquals(CommandType.LIST, listCommand.getType());
    }

    @Test
    @DisplayName("Should return 'No users online' if registry is empty")
    void testExecute_EmptyList() throws IOException {
        when(sessionRegistry.getAllSessions()).thenReturn(Collections.emptyList());

        listCommand.execute(session, new CommandResult(CommandType.LIST, null, null));

        verify(responder).sendSystem(session, MessageConstants.MSG_NO_USERS);
    }

    @Test
    @DisplayName("Should return list of registered users only")
    void testExecute_WithUsers() throws IOException {
        // Simulate two sessions: one logged in (Alice), one not logged in (Anonymous)
        when(sessionRegistry.getAllSessions()).thenReturn(Arrays.asList(session, otherSession));
        when(session.getId()).thenReturn("sess-1");
        when(otherSession.getId()).thenReturn("sess-2");

        when(sessionRegistry.getUsername("sess-1")).thenReturn("Alice");
        when(sessionRegistry.getUsername("sess-2")).thenReturn("Anonymous"); // Sẽ bị lọc bỏ

        listCommand.execute(session, new CommandResult(CommandType.LIST, null, null));

        // Verify: Just show Alice
        String expectedMsg = String.format(MessageConstants.MSG_ONLINE_USERS, 1, "Alice");
        verify(responder).sendSystem(session, expectedMsg);
    }
}