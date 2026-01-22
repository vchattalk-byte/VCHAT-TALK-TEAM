package org.example.VChatTalk.command.impl;

import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
import org.example.VChatTalk.command.MessageConstants;
import org.example.VChatTalk.model.ChatContext;
import org.example.VChatTalk.model.UserSession;
import org.example.VChatTalk.util.PrivateChatRegistry;
import org.example.VChatTalk.util.SystemResponseSender;
import org.example.VChatTalk.util.UserRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SelectCommandTest {

    @Mock private UserRegistry userRegistry;
    @Mock private PrivateChatRegistry privateChatRegistry;
    @Mock private SystemResponseSender responder;
    @Mock private WebSocketSession session;

    private SelectCommand selectCommand;
    private final String SESSION_ID = "sess-1";

    @BeforeEach
    void setUp() {
        selectCommand = new SelectCommand(responder, userRegistry, privateChatRegistry);
        lenient().when(session.getId()).thenReturn(SESSION_ID);
    }

    @Test
    @DisplayName("Should return SELECT type")
    void testGetType() {
        assertEquals(CommandType.SELECT, selectCommand.getType());
    }

    @Test
    @DisplayName("Fail if user is not logged in")
    void testExecute_NotLoggedIn() throws IOException {
        // Mock session trả về null hoặc Anonymous
        when(userRegistry.getSession(SESSION_ID)).thenReturn(null);

        CommandResult result = new CommandResult(CommandType.SELECT, "Alice", null);
        selectCommand.execute(session, result);

        verify(responder).sendError(session, MessageConstants.ERR_NOT_LOGGED_IN);
        verify(privateChatRegistry, never()).setTarget(anyString(), anyString());
    }

    @Test
    @DisplayName("Success: Switch to private chat and Update Context")
    void testExecute_Success() throws IOException {
        // Arrange
        UserSession initialSession = UserSession.builder()
                .sessionId(SESSION_ID)
                .username("Alice")
                .context(ChatContext.GLOBAL)
                .build();

        when(userRegistry.getSession(SESSION_ID)).thenReturn(initialSession);
        when(userRegistry.isUserOnline("Bob")).thenReturn(true);

        // Act
        CommandResult result = new CommandResult(CommandType.SELECT, "Bob", null);
        selectCommand.execute(session, result);

        // Assert 1: Registry updated
        verify(privateChatRegistry).setTarget(SESSION_ID, "Bob");

        // Assert 2: User Context updated to PRIVATE
        ArgumentCaptor<UserSession> captor = ArgumentCaptor.forClass(UserSession.class);
        verify(userRegistry).updateUser(captor.capture());
        assertEquals(ChatContext.PRIVATE, captor.getValue().getContext());

        // Assert 3: Notification
        verify(responder).sendSystem(session, String.format(MessageConstants.MSG_PRIVATE_CHAT_START, "Bob"));
    }
}