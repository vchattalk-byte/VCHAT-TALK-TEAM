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

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JoinCommandTest {

    @Mock private UserRegistry userRegistry;
    @Mock private RoomRegistry roomRegistry;
    @Mock private PrivateChatRegistry privateChatRegistry;
    @Mock private SystemResponseSender responder;
    @Mock private WebSocketSession session;

    private JoinCommand joinCommand;

    private static final String SESSION_ID = "s1";
    private static final String ROOM_ID = "#general";

    @BeforeEach
    void setUp() {
        joinCommand = new JoinCommand(
                userRegistry,
                roomRegistry,
                privateChatRegistry,
                responder
        );
        when(session.getId()).thenReturn(SESSION_ID);
    }

    // ---------- helper ----------
    private CommandResult joinCmd(String argument) {
        return new CommandResult(CommandType.JOIN, argument, null);
    }

    // ---------- FAIL CASES ----------

    @Test
    @DisplayName("Fail: User not logged in -> ERR_NOT_LOGGED_IN")
    void testExecute_NotLoggedIn() throws IOException {
        when(userRegistry.getSession(SESSION_ID)).thenReturn(null);

        joinCommand.execute(session, joinCmd(ROOM_ID));

        verify(responder).sendError(session, MessageConstants.ERR_NOT_LOGGED_IN);
        verifyNoInteractions(roomRegistry, privateChatRegistry);
    }

    @Test
    @DisplayName("Fail: Room argument missing -> ERR_ROOM_REQUIRED")
    void testExecute_RoomRequired() throws IOException {
        UserSession userSession = UserSession.builder()
                .sessionId(SESSION_ID)
                .username("Alice")
                .context(ChatContext.GLOBAL)
                .build();

        when(userRegistry.getSession(SESSION_ID)).thenReturn(userSession);

        joinCommand.execute(session, joinCmd("   "));

        verify(responder).sendError(session, MessageConstants.ERR_ROOM_REQUIRED);
        verifyNoInteractions(roomRegistry, privateChatRegistry);
    }

    @Test
    @DisplayName("Fail: Invalid room format -> ERR_INVALID_ROOM_FORMAT")
    void testExecute_InvalidRoomFormat() throws IOException {
        UserSession userSession = UserSession.builder()
                .sessionId(SESSION_ID)
                .username("Alice")
                .context(ChatContext.GLOBAL)
                .build();

        when(userRegistry.getSession(SESSION_ID)).thenReturn(userSession);

        joinCommand.execute(session, joinCmd("room1"));

        verify(responder).sendError(session, MessageConstants.ERR_INVALID_ROOM_FORMAT);
        verifyNoInteractions(roomRegistry, privateChatRegistry);
    }

    // ---------- SUCCESS CASES ----------

    @Test
    @DisplayName("Success: Join room from GLOBAL context")
    void testExecute_JoinFromGlobal() throws IOException {
        UserSession globalSession = UserSession.builder()
                .sessionId(SESSION_ID)
                .username("Alice")
                .context(ChatContext.GLOBAL)
                .build();

        when(userRegistry.getSession(SESSION_ID)).thenReturn(globalSession);

        joinCommand.execute(session, joinCmd(ROOM_ID));

        verify(roomRegistry).joinRoom(ROOM_ID, SESSION_ID);
        verify(userRegistry).updateContext(SESSION_ID, ChatContext.ROOM);
        verify(responder).sendSystem(
                session,
                String.format(MessageConstants.MSG_ROOM_JOIN, ROOM_ID)
        );

        verifyNoInteractions(privateChatRegistry);
    }

    @Test
    @DisplayName("Success: Join room from PRIVATE context (auto leave private)")
    void testExecute_JoinFromPrivate() throws IOException {
        UserSession privateSession = UserSession.builder()
                .sessionId(SESSION_ID)
                .username("Alice")
                .context(ChatContext.PRIVATE)
                .build();

        when(userRegistry.getSession(SESSION_ID)).thenReturn(privateSession);

        joinCommand.execute(session, joinCmd(ROOM_ID));

        verify(privateChatRegistry).removeTarget(SESSION_ID);
        verify(roomRegistry).joinRoom(ROOM_ID, SESSION_ID);
        verify(userRegistry).updateContext(SESSION_ID, ChatContext.ROOM);
        verify(responder).sendSystem(
                session,
                String.format(MessageConstants.MSG_ROOM_JOIN, ROOM_ID)
        );
    }

    @Test
    @DisplayName("Success: Switch room from ROOM context (auto leave current room)")
    void testExecute_SwitchRoom() throws IOException {
        UserSession roomSession = UserSession.builder()
                .sessionId(SESSION_ID)
                .username("Alice")
                .context(ChatContext.ROOM)
                .build();

        when(userRegistry.getSession(SESSION_ID)).thenReturn(roomSession);

        joinCommand.execute(session, joinCmd(ROOM_ID));

        verify(roomRegistry).leaveCurrentRoom(SESSION_ID);
        verify(roomRegistry).joinRoom(ROOM_ID, SESSION_ID);
        verify(userRegistry).updateContext(SESSION_ID, ChatContext.ROOM);
        verify(responder).sendSystem(
                session,
                String.format(MessageConstants.MSG_ROOM_JOIN, ROOM_ID)
        );

        verifyNoInteractions(privateChatRegistry);
    }
}
