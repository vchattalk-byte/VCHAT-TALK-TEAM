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
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListCommandTest {

    @Mock private UserRegistry userRegistry;
    @Mock private SystemResponseSender responder;
    @Mock private WebSocketSession session;
    @Mock private WebSocketSession otherSession;

    private ListCommand listCommand;

    @BeforeEach
    void setUp() {
        listCommand = new ListCommand(userRegistry, responder);
    }

    @Test
    void testGetType() {
        assertEquals(CommandType.LIST, listCommand.getType());
    }

    @Test
    @DisplayName("Should return 'No users online' if when empty")
    void testExecute_EmptyList() throws IOException {
        // Arrange: User logged in, but no other users online
        when(session.getId()).thenReturn("sess-1");
        when(userRegistry.isUserRegistered("sess-1")).thenReturn(true);  // ✅ THÊM DÒNG NÀY
        when(userRegistry.getAllOnlineUsers()).thenReturn(Collections.emptyList());

        // Act
        listCommand.execute(session, new CommandResult(CommandType.LIST, null, null));

        // Assert
        verify(responder).sendSystem(session, MessageConstants.MSG_NO_USERS);
    }

    @Test
    @DisplayName("Should return list of registered users only")
    void testExecute_WithUsers() throws IOException {
        // Arrange: 2 registered users
        when(session.getId()).thenReturn("sess-1");
        when(userRegistry.isUserRegistered("sess-1")).thenReturn(true);
        when(userRegistry.getAllOnlineUsers()).thenReturn(Arrays.asList("Alice", "Bob"));

        // Act
        listCommand.execute(session, new CommandResult(CommandType.LIST, null, null));

        // Assert: Show both users
        String expectedMsg = String.format(MessageConstants.MSG_ONLINE_USERS, 2, "Alice, Bob");
        verify(responder).sendSystem(session, expectedMsg);
    }
}