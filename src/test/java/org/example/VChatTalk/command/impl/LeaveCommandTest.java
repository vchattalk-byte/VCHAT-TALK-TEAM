package org.example.VChatTalk.command.impl;

import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
import org.example.VChatTalk.command.MessageConstants;
import org.example.VChatTalk.model.ChatContext;
import org.example.VChatTalk.model.UserSession;
import org.example.VChatTalk.util.PrivateChatRegistry;
import org.example.VChatTalk.util.RoomRegistry;
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
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveCommandTest {

    @Mock private UserRegistry userRegistry;
    @Mock private SystemResponseSender responder;
    @Mock private WebSocketSession session;
    @Mock private PrivateChatRegistry privateChatRegistry;
    @Mock private RoomRegistry roomRegistry;

    private LeaveCommand leaveCommand;

    private static final String SESSION_ID = "s1";
    private static final String ROOM_ID = "#general";

    @BeforeEach
    void setUp() {
        leaveCommand = new LeaveCommand(
                userRegistry,
                responder,
                privateChatRegistry,
                roomRegistry
        );
        lenient().when(session.getId()).thenReturn(SESSION_ID);
    }

    @Test
    @DisplayName("Fail: User not logged in -> ERR_NOT_LOGGED_IN")
    void testExecute_NotLoggedIn() throws IOException {
        // Arrange: user not logged in
        when(userRegistry.getSession(SESSION_ID)).thenReturn(null);

        // Act
        leaveCommand.execute(session, new CommandResult(CommandType.LEAVE, null, null));

        // Assert
        verify(responder).sendError(session, MessageConstants.ERR_NOT_LOGGED_IN);
        verifyNoInteractions(privateChatRegistry, roomRegistry);
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
        verifyNoInteractions(privateChatRegistry, roomRegistry);
        verify(userRegistry, never()).updateContext(any(), any());
    }

    @Test
    @DisplayName("Success: Leave private chat (Context is PRIVATE)")
    void testExecute_LeavePrivateChat() throws IOException {
        UserSession privateSession = UserSession.builder()
                .sessionId(SESSION_ID)
                .username("Alice")
                .context(ChatContext.PRIVATE)
                .build();

        when(userRegistry.getSession(SESSION_ID)).thenReturn(privateSession);

        leaveCommand.execute(session, new CommandResult(CommandType.LEAVE, null, null));

        // 1️⃣ Private registry cleaned
        verify(privateChatRegistry).removeTarget(SESSION_ID);

        // 2️⃣ Context reset to GLOBAL (CHAT-3 compliant)
        verify(userRegistry).updateContext(SESSION_ID, ChatContext.GLOBAL);

        // 3️⃣ Feedback sent
        verify(responder).sendSystem(session, MessageConstants.MSG_PRIVATE_CHAT_LEAVE);
        verifyNoInteractions(roomRegistry);
    }

    @Test
    @DisplayName("Success: Leave ROOM chat")
    void testExecute_LeaveRoom() throws IOException {
        UserSession roomSession = UserSession.builder()
                .sessionId(SESSION_ID)
                .username("Alice")
                .context(ChatContext.ROOM)
                .build();

        when(userRegistry.getSession(SESSION_ID)).thenReturn(roomSession);
        when(roomRegistry.getRoomOfSession(SESSION_ID))
                .thenReturn(Optional.of(ROOM_ID));

        leaveCommand.execute(session, new CommandResult(CommandType.LEAVE, null, null));

        verify(roomRegistry).leaveRoom(ROOM_ID, SESSION_ID);
        verify(userRegistry).updateContext(SESSION_ID, ChatContext.GLOBAL);
        verify(responder).sendSystem(session, MessageConstants.MSG_ROOM_LEAVE);
        verifyNoInteractions(privateChatRegistry);
    }

    @Test
    @DisplayName("Edge case: Context is ROOM but no room found in registry")
    void testExecute_RoomContextButNoRoomFound() throws IOException {
        UserSession roomSession = UserSession.builder()
                .sessionId(SESSION_ID)
                .username("Alice")
                .context(ChatContext.ROOM)
                .build();

        when(userRegistry.getSession(SESSION_ID)).thenReturn(roomSession);
        when(roomRegistry.getRoomOfSession(SESSION_ID))
                .thenReturn(Optional.empty());

        leaveCommand.execute(session, new CommandResult(CommandType.LEAVE, null, null));

        verify(roomRegistry, never()).leaveRoom(any(), any());
        verify(userRegistry).updateContext(SESSION_ID, ChatContext.GLOBAL);
        verify(responder).sendSystem(session, MessageConstants.MSG_ROOM_LEAVE);
        verifyNoInteractions(privateChatRegistry);
    }
}
