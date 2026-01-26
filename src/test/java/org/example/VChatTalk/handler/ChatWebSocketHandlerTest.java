package org.example.VChatTalk.handler;

import org.example.VChatTalk.command.MessageConstants;
import org.example.VChatTalk.model.MessageDTO;
import org.example.VChatTalk.service.BroadcastService;
import org.example.VChatTalk.service.ChatService;
import org.example.VChatTalk.service.MessageProcessor;
import org.example.VChatTalk.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketHandlerTest {

    @Mock private SessionRegistry sessionRegistry;
    @Mock private UserRegistry userRegistry;
    @Mock private PrivateChatRegistry privateChatRegistry;
    @Mock private ChatService chatService;
    @Mock private MessageProcessor messageProcessor;
    @Mock private BroadcastService broadcastService;
    @Mock private RateLimiter rateLimiter;
    @Mock private SystemResponseSender systemResponseSender;
    @Mock private WebSocketSession session;

    @InjectMocks
    private ChatWebSocketHandler chatWebSocketHandler;

    private final String SESSION_ID = "test-session-id";

    @BeforeEach
    void setUp() {
        lenient().when(session.getId()).thenReturn(SESSION_ID);
    }

    // ========== CONNECTION TESTS ==========

    @Test
    @DisplayName("On Connect: Should register session and send welcome")
    void afterConnectionEstablished_Success() throws Exception {
        chatWebSocketHandler.afterConnectionEstablished(session);

        verify(sessionRegistry).addSession(session);
        verify(systemResponseSender).sendSystem(session, MessageConstants.MSG_WELCOME);
    }

    @Test
    @DisplayName("On Disconnect: Should cleanup Registries explicitly")
    void afterConnectionClosed_Success() {
        String username = "Alice";

        // Mock behaviors
        when(userRegistry.getUsername(SESSION_ID)).thenReturn(username);
        when(chatService.handleLeave(SESSION_ID)).thenReturn(MessageDTO.builder().content("Bye").build());

        // Execute
        chatWebSocketHandler.afterConnectionClosed(session, CloseStatus.NORMAL);

        // Verify CLEANUP Logic (Moved from Service to Handler)
        verify(rateLimiter).removeSession(SESSION_ID);
        verify(sessionRegistry).removeSession(SESSION_ID);
        verify(userRegistry).removeUser(SESSION_ID); // QUAN TRỌNG: Verify handler gọi hàm xóa user

        // Verify Broadcast
        verify(broadcastService).broadcast(any(MessageDTO.class), eq(null));
    }

    // ========== MESSAGE TESTS ==========

    @Test
    @DisplayName("On Message: Should delegate to processor if within rate limit")
    void handleTextMessage_Success() throws Exception {
        TextMessage rawMessage = new TextMessage("{\"type\":\"MESSAGE\", \"content\":\"Hi\"}");

        when(rateLimiter.isRateLimitExceeded(SESSION_ID)).thenReturn(false);

        chatWebSocketHandler.handleTextMessage(session, rawMessage);

        verify(messageProcessor).processMessage(session, rawMessage.getPayload());
    }

    @Test
    @DisplayName("On Message: Should block and send error if rate limit exceeded")
    void handleTextMessage_RateLimited() throws Exception {
        TextMessage rawMessage = new TextMessage("Spam message");

        when(rateLimiter.isRateLimitExceeded(SESSION_ID)).thenReturn(true);

        chatWebSocketHandler.handleTextMessage(session, rawMessage);

        verify(systemResponseSender).sendError(session, MessageConstants.ERR_RATE_LIMIT);
        verifyNoInteractions(messageProcessor);
    }

    @Test
    @DisplayName("On Disconnect: Notify followers and cleanup private chat targets")
    void afterConnectionClosed_ShouldNotifyFollowersAndCleanupTargets() throws Exception {
        // Alice disconnects
        String aliceSessionId = SESSION_ID;
        String aliceUsername = "Alice";

        // Two followers chatting with Alice
        String bobSessionId = "sess-bob";
        String charlieSessionId = "sess-charlie";

        WebSocketSession bobSession = mock(WebSocketSession.class);
        WebSocketSession charlieSession = mock(WebSocketSession.class);

        when(bobSession.isOpen()).thenReturn(true);
        when(charlieSession.isOpen()).thenReturn(true);

        // Mock registries
        when(userRegistry.getUsername(aliceSessionId)).thenReturn(aliceUsername);
        when(privateChatRegistry.getSessionsTargeting(aliceUsername))
                .thenReturn(Set.of(bobSessionId, charlieSessionId));

        when(sessionRegistry.findSessionById(bobSessionId)).thenReturn(bobSession);
        when(sessionRegistry.findSessionById(charlieSessionId)).thenReturn(charlieSession);

        // Execute
        chatWebSocketHandler.afterConnectionClosed(session, CloseStatus.NORMAL);

        // 1️⃣ Followers should be cleaned up
        verify(privateChatRegistry).removeTarget(bobSessionId);
        verify(privateChatRegistry).removeTarget(charlieSessionId);

        // 2️⃣ Followers should be notified
        verify(systemResponseSender).sendSystem(
                eq(bobSession),
                contains("Alice")
        );
        verify(systemResponseSender).sendSystem(
                eq(charlieSession),
                contains("Alice")
        );

        // 3️⃣ Alice should be removed
        verify(userRegistry).removeUser(aliceSessionId);
        verify(sessionRegistry).removeSession(aliceSessionId);
    }
}