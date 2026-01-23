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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SelectCommandTest {

    @Mock private SystemResponseSender responder;
    @Mock private UserRegistry userRegistry;
    @Mock private PrivateChatRegistry privateChatRegistry;
    @Mock private WebSocketSession session;

    private SelectCommand selectCommand;

    private static final String SESSION_ID = "s1";

    @BeforeEach
    void setUp() {
        selectCommand = new SelectCommand(responder, userRegistry, privateChatRegistry);
        lenient().when(session.getId()).thenReturn(SESSION_ID);
    }

    @Test
    @DisplayName("Fail: User not logged in")
    void testSelect_NotLoggedIn() throws IOException {
        when(userRegistry.getSession(SESSION_ID)).thenReturn(null);

        selectCommand.execute(session,
                new CommandResult(CommandType.SELECT, "Bob", null));

        verify(responder).sendError(session, MessageConstants.ERR_NOT_LOGGED_IN);
        verifyNoInteractions(privateChatRegistry);
    }

    @Test
    @DisplayName("Fail: Missing username argument")
    void testSelect_MissingArgument() throws IOException {
        mockLoggedInUser("Alice");

        selectCommand.execute(session,
                new CommandResult(CommandType.SELECT, null, null));

        verify(responder).sendError(
                session,
                String.format(MessageConstants.ERR_MISSING_ARG_USER, "/select")
        );
    }

    @Test
    @DisplayName("Fail: Username contains spaces")
    void testSelect_UsernameWithSpaces() throws IOException {
        mockLoggedInUser("Alice");

        selectCommand.execute(session,
                new CommandResult(CommandType.SELECT, "Bob Smith", null));

        verify(responder).sendError(session, MessageConstants.ERR_USERNAME_CONTAIN_SPACE);
    }

    @Test
    @DisplayName("Fail: Self chat prevention")
    void testSelect_SelfChat() throws IOException {
        mockLoggedInUser("Alice");

        selectCommand.execute(session,
                new CommandResult(CommandType.SELECT, "Alice", null));

        verify(responder).sendError(session, MessageConstants.ERR_SELF_CHAT);
    }

    @Test
    @DisplayName("Fail: Target user offline")
    void testSelect_UserOffline() throws IOException {
        mockLoggedInUser("Alice");
        when(userRegistry.isUserOnline("Bob")).thenReturn(false);

        selectCommand.execute(session,
                new CommandResult(CommandType.SELECT, "Bob", null));

        verify(responder).sendError(
                session,
                String.format(MessageConstants.ERR_USER_OFFLINE, "Bob")
        );
    }

    @Test
    @DisplayName("Success: Switch to PRIVATE chat")
    void testSelect_Success() throws IOException {
        mockLoggedInUser("Alice");
        when(userRegistry.isUserOnline("Bob")).thenReturn(true);

        selectCommand.execute(session,
                new CommandResult(CommandType.SELECT, "Bob", null));

        verify(privateChatRegistry).setTarget(SESSION_ID, "Bob");
        verify(userRegistry).updateContext(SESSION_ID, ChatContext.PRIVATE);

        verify(responder).sendSystem(
                session,
                String.format(MessageConstants.MSG_PRIVATE_CHAT_START, "Bob")
        );
    }

    // ===== Helper =====
    private void mockLoggedInUser(String username) {
        UserSession sessionState = UserSession.builder()
                .sessionId(SESSION_ID)
                .username(username)
                .context(ChatContext.GLOBAL)
                .build();

        when(userRegistry.getSession(SESSION_ID)).thenReturn(sessionState);
    }
}
