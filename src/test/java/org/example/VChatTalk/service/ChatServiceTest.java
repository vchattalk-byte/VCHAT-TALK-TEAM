package org.example.VChatTalk.service;

import org.example.VChatTalk.model.ChatSession;
import org.example.VChatTalk.model.MessageDTO;
import org.example.VChatTalk.model.MessageType;
import org.example.VChatTalk.util.PrivateChatRegistry;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock private SessionRegistry sessionRegistry;
    @Mock private UserRegistry userRegistry;
    @Mock private PrivateChatRegistry privateChatRegistry;
    @Mock private BroadcastService broadcastService;
    @Mock private ChatSession senderChatSession;
    @Mock private ChatSession targetChatSession;

    @InjectMocks
    private ChatService chatService;

    private final String SENDER_ID = "session-123";
    private final String SENDER_NAME = "Alice";

    @BeforeEach
    void setUp() {
        lenient().when(senderChatSession.getId()).thenReturn(SENDER_ID);
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

    // ========== ROUTING TESTS ==========

    @Test
    @DisplayName("Route - Should broadcast to global when no private target is set")
    void routeMessage_GlobalMode() throws IOException {
        MessageDTO dto = MessageDTO.builder().content("Hello World").build();

        when(userRegistry.isUserRegistered(SENDER_ID)).thenReturn(true);
        when(userRegistry.getUsername(SENDER_ID)).thenReturn(SENDER_NAME);
        when(privateChatRegistry.getTarget(SENDER_ID)).thenReturn(null);
        when(sessionRegistry.findChatSessionById(SENDER_ID)).thenReturn(senderChatSession);
        when(senderChatSession.isOpen()).thenReturn(true);

        chatService.routeMessage(senderChatSession.getId(), dto);

        verify(broadcastService).broadcast(any(MessageDTO.class), eq(senderChatSession));
        verify(broadcastService, never()).sendToSession(any(), any());
    }

    @Test
    @DisplayName("Route - Should send private message when target is set")
    void routeMessage_PrivateMode() throws IOException {
        String targetName = "Bob";
        String targetSessId = "session-456";

        MessageDTO dto = MessageDTO.builder().content("Secret").build();

        // Setup registries
        when(userRegistry.isUserRegistered(SENDER_ID)).thenReturn(true);
        when(userRegistry.getUsername(SENDER_ID)).thenReturn(SENDER_NAME);
        when(privateChatRegistry.getTarget(SENDER_ID)).thenReturn(targetName);
        when(sessionRegistry.findChatSessionById(SENDER_ID)).thenReturn(senderChatSession);
        when(senderChatSession.isOpen()).thenReturn(true);

        // Setup Bob's availability
        when(userRegistry.getSessionId(targetName)).thenReturn(targetSessId);
        when(sessionRegistry.findChatSessionById(targetSessId)).thenReturn(targetChatSession);
        when(targetChatSession.isOpen()).thenReturn(true);

        // Use new method
        chatService.routeMessage(SENDER_ID, dto);

        // Verify messages are sent via ChatSession.sendMessage() method
        verify(senderChatSession).sendMessage(any(MessageDTO.class));
        verify(targetChatSession).sendMessage(any(MessageDTO.class));

        // Verify BroadcastService is NOT used for private messages in new implementation
        verify(broadcastService, never()).sendToSession(any(), any());
        verify(broadcastService, never()).broadcast(any(), (ChatSession) any());
    }

    @Test
    @DisplayName("Route - Should handle closed session gracefully")
    void routeMessage_SessionClosed() throws IOException {
        MessageDTO dto = MessageDTO.builder().content("Hello").build();

        when(userRegistry.isUserRegistered(SENDER_ID)).thenReturn(true);
        when(userRegistry.getUsername(SENDER_ID)).thenReturn(SENDER_NAME);

        when(sessionRegistry.findChatSessionById(SENDER_ID)).thenReturn(senderChatSession);
        when(senderChatSession.isOpen()).thenReturn(false);

        chatService.routeMessage(SENDER_ID, dto);

        // Should clean up closed session
        verify(sessionRegistry).removeSession(SENDER_ID);
        verify(broadcastService, never()).broadcast(any(), (ChatSession) any());
        verify(senderChatSession, never()).sendMessage(any());
    }

    @Test
    @DisplayName("Route - Should log warning when session not found at all")
    void routeMessage_SessionNotFound() throws IOException {
        MessageDTO dto = MessageDTO.builder().content("Hello").build();

        when(userRegistry.isUserRegistered(SENDER_ID)).thenReturn(true);
        when(userRegistry.getUsername(SENDER_ID)).thenReturn(SENDER_NAME);

        // ONLY mock findChatSessionById - your code doesn't call findSessionById anymore!
        when(sessionRegistry.findChatSessionById(SENDER_ID)).thenReturn(null);
        // Remove this line: when(sessionRegistry.findSessionById(SENDER_ID)).thenReturn(null);

        chatService.routeMessage(SENDER_ID, dto);

        // No interactions with broadcast or send methods
        verify(broadcastService, never()).broadcast(any(), (ChatSession) any());
    }

    // ========== LEAVE TESTS ==========

    @Test
    @DisplayName("Leave - Should remove user and return system message")
    void handleLeave_Success() {
        when(userRegistry.isUserRegistered(SENDER_ID)).thenReturn(true);
        when(userRegistry.getUsername(SENDER_ID)).thenReturn(SENDER_NAME);

        MessageDTO result = chatService.handleLeave(SENDER_ID);

        assertNotNull(result);
        verify(userRegistry).removeUser(SENDER_ID);
        verify(privateChatRegistry).removeTarget(SENDER_ID);
    }

    @Test
    @DisplayName("Message - Should throw exception if user sends message without joining")
    void handleMessage_Unregistered() {
        MessageDTO dto = MessageDTO.builder().content("Hi").build();
        when(userRegistry.isUserRegistered(SENDER_ID)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> chatService.handleMessage(SENDER_ID, dto));
    }
}