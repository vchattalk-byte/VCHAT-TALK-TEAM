package org.example.VChatTalk.service;

import org.example.VChatTalk.model.ChatContext;
import org.example.VChatTalk.model.MessageDTO;
import org.example.VChatTalk.model.MessageType;
import org.example.VChatTalk.model.UserSession;
import org.example.VChatTalk.util.PrivateChatRegistry;
import org.example.VChatTalk.util.RoomRegistry;
import org.example.VChatTalk.util.SessionRegistry;
import org.example.VChatTalk.util.UserRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private SessionRegistry sessionRegistry;
    @Mock private UserRegistry userRegistry;
    @Mock private PrivateChatRegistry privateChatRegistry;
    @Mock private BroadcastService broadcastService;
    @Mock private WebSocketSession senderSession;
    @Mock private RoomRegistry roomRegistry;

    @InjectMocks
    private ChatService chatService;

    private final String SENDER_ID = "session-123";
    private final String SENDER_NAME = "Alice";

    @BeforeEach
    void setUp() {
        lenient().when(senderSession.getId()).thenReturn(SENDER_ID);
    }

    // ========== JOIN TESTS ==========

    @Test
    @DisplayName("Join - Should succeed when username is valid and available")
    void handleJoin_Success() {
        MessageDTO joinDto = MessageDTO.builder().sender("Alice!").build(); // '!' should be sanitized

        when(userRegistry.isUserRegistered(SENDER_ID)).thenReturn(false);
        when(userRegistry.tryRegisterUser(eq(SENDER_ID), eq("Alice"))).thenReturn(true);
        when(userRegistry.countOnlineUsers()).thenReturn(1);

        MessageDTO result = chatService.handleJoin(SENDER_ID, joinDto);

        assertNotNull(result);
        assertEquals(MessageType.SYSTEM, result.getType());
        assertTrue(result.getContent().contains("Alice"));
        verify(userRegistry).tryRegisterUser(SENDER_ID, "Alice");
    }

    @Test
    @DisplayName("Join - Should throw exception if username is already taken")
    void handleJoin_DuplicateUsername() {
        MessageDTO joinDto = MessageDTO.builder().sender("Bob").build();
        when(userRegistry.tryRegisterUser(anyString(), anyString())).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> chatService.handleJoin(SENDER_ID, joinDto));
    }

    // ========== ROUTING TESTS (UPDATED FOR CONTEXT) ==========

    @Test
    @DisplayName("Route - Should broadcast to global when Context is GLOBAL")
    void routeMessage_GlobalMode() throws IOException {
        MessageDTO dto = MessageDTO.builder().content("Hello World").build();

        UserSession globalSession = UserSession.builder()
                .sessionId(SENDER_ID)
                .username(SENDER_NAME)
                .context(ChatContext.GLOBAL)
                .build();

        when(userRegistry.getSession(SENDER_ID)).thenReturn(globalSession);

        when(userRegistry.isUserRegistered(SENDER_ID)).thenReturn(true);
        when(userRegistry.getUsername(SENDER_ID)).thenReturn(SENDER_NAME);

        chatService.routeMessage(senderSession, dto);

        verify(broadcastService).broadcast(any(MessageDTO.class), eq(senderSession));
        verify(broadcastService, never()).sendToSession(any(), any());
    }

    @Test
    @DisplayName("Route - Should send private message when Context is PRIVATE")
    void routeMessage_PrivateMode() throws IOException {
        String targetName = "Bob";
        String targetSessId = "session-456";
        WebSocketSession targetSession = mock(WebSocketSession.class);
        MessageDTO dto = MessageDTO.builder().content("Secret").build();

        UserSession privateSession = UserSession.builder()
                .sessionId(SENDER_ID)
                .username(SENDER_NAME)
                .context(ChatContext.PRIVATE)
                .build();

        when(userRegistry.getSession(SENDER_ID)).thenReturn(privateSession);

        when(userRegistry.isUserRegistered(SENDER_ID)).thenReturn(true);
        when(userRegistry.getUsername(SENDER_ID)).thenReturn(SENDER_NAME);

        when(privateChatRegistry.getTarget(SENDER_ID)).thenReturn(targetName);

        when(userRegistry.getSessionId(targetName)).thenReturn(targetSessId);
        when(sessionRegistry.findSessionById(targetSessId)).thenReturn(targetSession);
        when(targetSession.isOpen()).thenReturn(true);

        chatService.routeMessage(senderSession, dto);

        verify(broadcastService, times(2)).sendToSession(any(), any());

        ArgumentCaptor<MessageDTO> msgCaptor = ArgumentCaptor.forClass(MessageDTO.class);
        verify(broadcastService).sendToSession(eq(targetSession), msgCaptor.capture());
        assertTrue(msgCaptor.getValue().getContent().contains("[PM]"));
    }

    // ========== LEAVE TESTS (UPDATED CLEANUP LOGIC) ==========

    @Test
    @DisplayName("Leave - Should return message BUT NOT clean registry (Handler job now)")
    void handleLeave_Success() {
        when(userRegistry.isUserRegistered(SENDER_ID)).thenReturn(true);
        when(userRegistry.getUsername(SENDER_ID)).thenReturn(SENDER_NAME);

        MessageDTO result = chatService.handleLeave(SENDER_ID);

        assertNotNull(result);

        verify(userRegistry, never()).removeUser(SENDER_ID);
        verify(privateChatRegistry, never()).removeTarget(SENDER_ID);
    }

    @Test
    @DisplayName("Message - Should throw exception if user sends message without joining")
    void handleMessage_Unregistered() {
        MessageDTO dto = MessageDTO.builder().content("Hi").build();
        when(userRegistry.isUserRegistered(SENDER_ID)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> chatService.handleMessage(SENDER_ID, dto));
    }
    @Test
    @DisplayName("Route - ROOM with no members should NOT broadcast")
    void routeMessage_RoomMode_NoMembers() throws IOException {
        String roomId = "room-1";

        MessageDTO dto = MessageDTO.builder()
                .content("hello room")
                .build();

        UserSession roomSession = UserSession.builder()
                .sessionId(SENDER_ID)
                .username(SENDER_NAME)
                .context(ChatContext.ROOM)
                .build();

        when(userRegistry.isUserRegistered(SENDER_ID)).thenReturn(true);
        when(userRegistry.getUsername(SENDER_ID)).thenReturn(SENDER_NAME);
        when(userRegistry.getSession(SENDER_ID)).thenReturn(roomSession);

        when(roomRegistry.getRoomOfSession(SENDER_ID))
                .thenReturn(Optional.of(roomId));

        chatService.routeMessage(senderSession, dto);

        verify(broadcastService, never()).broadcastToRoom(any(), anyCollection(), anyString());
    }
    @Test
    @DisplayName("Route - ROOM context but no room assigned should throw exception")
    void routeMessage_RoomMode_NoRoom() {
        // given
        UserSession roomUser = UserSession.builder()
                .sessionId(SENDER_ID)
                .username(SENDER_NAME)
                .context(ChatContext.ROOM)
                .build();

        when(userRegistry.isUserRegistered(SENDER_ID)).thenReturn(true);
        when(userRegistry.getUsername(SENDER_ID)).thenReturn(SENDER_NAME);
        when(userRegistry.getSession(SENDER_ID)).thenReturn(roomUser);

        when(roomRegistry.getRoomOfSession(SENDER_ID))
                .thenReturn(Optional.empty());

        MessageDTO dto = MessageDTO.builder()
                .content("hello room")
                .build();

        // when + then
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> chatService.routeMessage(senderSession, dto)
        );

        assertEquals(
                "User is in ROOM context but not assigned to any room",
                ex.getMessage()
        );

        verify(broadcastService, never())
                .broadcastToRoom(any(), any(), any());
    }

}