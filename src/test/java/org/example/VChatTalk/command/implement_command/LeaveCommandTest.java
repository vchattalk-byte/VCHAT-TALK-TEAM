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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveCommandTest {

    @Mock private SessionRegistry sessionRegistry;
    @Mock private SystemResponseSender responder;
    @Mock private WebSocketSession session;

    private LeaveCommand leaveCommand;

    @BeforeEach
    void setUp() {
        leaveCommand = new LeaveCommand(sessionRegistry, responder);
    }

    @Test
    void testGetType() {
        assertEquals(CommandType.LEAVE, leaveCommand.getType());
    }

    @Test
    @DisplayName("Fail if user not logged in")
    void testExecute_NotLoggedIn() throws IOException {
        when(session.getId()).thenReturn("s1");
        when(sessionRegistry.isUserRegistered("s1")).thenReturn(false);

        leaveCommand.execute(session, new CommandResult(CommandType.LEAVE, null, null));

        verify(responder).sendError(session, MessageConstants.ERR_NOT_LOGGED_IN);
    }

    @Test
    @DisplayName("Fail if already in global chat (no target)")
    void testExecute_AlreadyGlobal() throws IOException {
        when(session.getId()).thenReturn("s1");
        when(sessionRegistry.isUserRegistered("s1")).thenReturn(true);
        when(sessionRegistry.getTarget("s1")).thenReturn(null); // Không có target

        leaveCommand.execute(session, new CommandResult(CommandType.LEAVE, null, null));

        verify(responder).sendSystem(session, MessageConstants.ERR_ALREADY_IN_GLOBAL);
        verify(sessionRegistry, never()).removeTarget(anyString());
    }

    @Test
    @DisplayName("Success: Leave private chat")
    void testExecute_Success() throws IOException {
        when(session.getId()).thenReturn("s1");
        when(sessionRegistry.isUserRegistered("s1")).thenReturn(true);
        when(sessionRegistry.getTarget("s1")).thenReturn("Alice"); // Đang chat với Alice

        leaveCommand.execute(session, new CommandResult(CommandType.LEAVE, null, null));

        verify(sessionRegistry).removeTarget("s1");
        verify(responder).sendSystem(session, MessageConstants.MSG_PRIVATE_CHAT_LEAVE);
    }
}