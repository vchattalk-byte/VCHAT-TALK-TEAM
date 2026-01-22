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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveCommandTest {

    @Mock private UserRegistry userRegistry;
    @Mock private SystemResponseSender responder;
    @Mock private WebSocketSession session;
    @Mock private PrivateChatRegistry privateChatRegistry;

    private LeaveCommand leaveCommand;
    private final String SESSION_ID = "s1";

    @BeforeEach
    void setUp() {
        leaveCommand = new LeaveCommand(userRegistry, responder, privateChatRegistry);
        lenient().when(session.getId()).thenReturn(SESSION_ID);
    }

    @Test
    @DisplayName("Fail if already in global chat (Context is GLOBAL)")
    void testExecute_AlreadyGlobal() throws IOException {
        UserSession globalSession = UserSession.builder()
                .sessionId(SESSION_ID)
                .username("Alice")
                .context(ChatContext.GLOBAL)
                .build();

        when(userRegistry.getSession(SESSION_ID)).thenReturn(globalSession);

        leaveCommand.execute(session, new CommandResult(CommandType.LEAVE, null, null));

        verify(responder).sendError(session, MessageConstants.ERR_ALREADY_IN_GLOBAL);
        verify(privateChatRegistry, never()).removeTarget(anyString());
    }

    @Test
    @DisplayName("Success: Leave private chat (Context is PRIVATE)")
    void testExecute_Success() throws IOException {
        // Arrange: User is in PRIVATE context
        UserSession privateSession = UserSession.builder()
                .sessionId(SESSION_ID)
                .username("Alice")
                .context(ChatContext.PRIVATE)
                .build();

        when(userRegistry.getSession(SESSION_ID)).thenReturn(privateSession);

        // Act
        leaveCommand.execute(session, new CommandResult(CommandType.LEAVE, null, null));

        // Assert 1: Registry Cleaned
        verify(privateChatRegistry).removeTarget(SESSION_ID);

        // Assert 2: Context Reset to GLOBAL
        ArgumentCaptor<UserSession> captor = ArgumentCaptor.forClass(UserSession.class);
        verify(userRegistry).updateUser(captor.capture());
        assertEquals(ChatContext.GLOBAL, captor.getValue().getContext());

        verify(responder).sendSystem(session, MessageConstants.MSG_PRIVATE_CHAT_LEAVE);
    }
}