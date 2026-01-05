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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SelectCommandTest {

    @Mock
    private SessionRegistry sessionRegistry;

    @Mock
    private SystemResponseSender responder;

    @Mock
    private WebSocketSession session;

    private SelectCommand selectCommand;

    @BeforeEach
    void setUp() {
        // Inject Mocks vào Command
        selectCommand = new SelectCommand(sessionRegistry, responder);
    }

    @Test
    @DisplayName("Should return SELECT type")
    void testGetType() {
        assertEquals(CommandType.SELECT, selectCommand.getType());
    }

    @Test
    @DisplayName("Fail if user is not logged in")
    void testExecute_NotLoggedIn() throws IOException {
        // Giả lập chưa login
        when(session.getId()).thenReturn("sess-1");
        when(sessionRegistry.isUserRegistered("sess-1")).thenReturn(false);

        CommandResult result = new CommandResult(CommandType.SELECT, "Alice", null);
        selectCommand.execute(session, result);

        // Verify: Phải gửi lỗi ERR_NOT_LOGGED_IN
        verify(responder).sendError(session, MessageConstants.ERR_NOT_LOGGED_IN);
        // Verify: Không được set target
        verify(sessionRegistry, never()).setTarget(anyString(), anyString());
    }

    @Test
    @DisplayName("Fail if argument (target user) is missing")
    void testExecute_MissingArgument() throws IOException {
        when(session.getId()).thenReturn("sess-1");
        when(sessionRegistry.isUserRegistered("sess-1")).thenReturn(true);

        // Argument là null
        CommandResult result = new CommandResult(CommandType.SELECT, null, null);
        selectCommand.execute(session, result);

        verify(responder).sendError(session, String.format(MessageConstants.ERR_MISSING_ARG_USER, "/select"));
    }

    @Test
    @DisplayName("Fail if username contains spaces")
    void testExecute_SpaceInUsername() throws IOException {
        when(session.getId()).thenReturn("sess-1");
        when(sessionRegistry.isUserRegistered("sess-1")).thenReturn(true);

        CommandResult result = new CommandResult(CommandType.SELECT, "User Name", null);
        selectCommand.execute(session, result);

        verify(responder).sendError(eq(session), contains("cannot contain spaces"));
    }

    @Test
    @DisplayName("Fail if self-chat")
    void testExecute_SelfChat() throws IOException {
        when(session.getId()).thenReturn("sess-1");
        when(sessionRegistry.isUserRegistered("sess-1")).thenReturn(true);
        when(sessionRegistry.getUsername("sess-1")).thenReturn("Alice");

        // Target là Alice (chính mình)
        CommandResult result = new CommandResult(CommandType.SELECT, "Alice", null);
        selectCommand.execute(session, result);

        verify(responder).sendError(session, MessageConstants.ERR_SELF_CHAT);
    }

    @Test
    @DisplayName("Fail if target user is offline")
    void testExecute_TargetOffline() throws IOException {
        when(session.getId()).thenReturn("sess-1");
        when(sessionRegistry.isUserRegistered("sess-1")).thenReturn(true);
        when(sessionRegistry.getUsername("sess-1")).thenReturn("Alice");

        // Bob offline
        when(sessionRegistry.isUserOnline("Bob")).thenReturn(false);

        CommandResult result = new CommandResult(CommandType.SELECT, "Bob", null);
        selectCommand.execute(session, result);

        verify(responder).sendError(session, String.format(MessageConstants.ERR_USER_OFFLINE, "Bob"));
    }

    @Test
    @DisplayName("Success: Switch to private chat")
    void testExecute_Success() throws IOException {
        when(session.getId()).thenReturn("sess-1");
        when(sessionRegistry.isUserRegistered("sess-1")).thenReturn(true);
        when(sessionRegistry.getUsername("sess-1")).thenReturn("Alice");

        // Bob online
        when(sessionRegistry.isUserOnline("Bob")).thenReturn(true);

        CommandResult result = new CommandResult(CommandType.SELECT, "Bob", null);
        selectCommand.execute(session, result);

        // Verify quan trọng: Phải gọi setTarget
        verify(sessionRegistry).setTarget("sess-1", "Bob");
        // Verify: Gửi thông báo thành công
        verify(responder).sendSystem(session, String.format(MessageConstants.MSG_PRIVATE_CHAT_START, "Bob"));
    }
}