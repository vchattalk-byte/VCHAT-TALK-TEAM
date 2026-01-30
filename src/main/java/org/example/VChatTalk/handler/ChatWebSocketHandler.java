package org.example.VChatTalk.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.VChatTalk.command.MessageConstants;
import org.example.VChatTalk.model.MessageDTO;
import org.example.VChatTalk.service.BroadcastService;
import org.example.VChatTalk.service.ChatService;
import org.example.VChatTalk.service.MessageProcessor;
import org.example.VChatTalk.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket handler for chat functionality
 * Manages connection lifecycle and delegates message processing
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final SessionRegistry sessionRegistry;
    private final UserRegistry userRegistry;
    private final PrivateChatRegistry privateChatRegistry;
    private final ChatService chatService;
    private final MessageProcessor messageProcessor;
    private final BroadcastService broadcastService;
    private final RateLimiter rateLimiter;
    private final SystemResponseSender systemResponseSender;

    /**
     * ========== CONNECTION LIFECYCLE ==========
     */

    /**
     * Called when new WebSocket connection is established
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessionRegistry.addSession(session);
        systemResponseSender.sendSystem(session, MessageConstants.MSG_WELCOME);

        log.info("[CONNECT] Session: {}", session.getId());
        sessionRegistry.countSessions();
        userRegistry.logCountOnlineUsers();
    }

    /**
     * Called when WebSocket connection is closed
     * Cleans up all session data and notifies affected users
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        String username = userRegistry.getUsername(sessionId);

        // Notify followers if user was registered (not Anonymous)
        if (!MessageConstants.USER_ANONYMOUS.equals(username)) {
            notifyFollowersOfDisconnect(username);
        }

        // Clean up all registries
        rateLimiter.removeSession(sessionId);
        sessionRegistry.removeSession(sessionId);

        // Handle leave and broadcast to all users
        MessageDTO leaveMessage = chatService.handleLeave(sessionId);
        if (leaveMessage != null) {
            broadcastService.broadcast(leaveMessage, (WebSocketSession) null);
        }

        log.info("[DISCONNECT] {} - Status: {}", sessionId, status.getCode());
        sessionRegistry.countSessions();
        userRegistry.logCountOnlineUsers();
    }

    /**
     * ========== MESSAGE HANDLING ==========
     */

    /**
     * Called when text message is received from WebSocket
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Rate limit check - prevent spam
        if (rateLimiter.isRateLimitExceeded(session.getId())) {
            systemResponseSender.sendError(session, MessageConstants.ERR_RATE_LIMIT);
            return;
        }

        // Delegate message processing to MessageProcessor
        messageProcessor.processMessage(session, message.getPayload());
    }

    /**
     * ========== HELPER METHODS ==========
     */

    /**
     * Notify all users who were targeting the disconnected user
     * Remove their targets and send notification
     */
    private void notifyFollowersOfDisconnect(String username) {
        privateChatRegistry.getSessionsTargeting(username).forEach(followerSessionId -> {
            WebSocketSession followerSession = sessionRegistry.findSessionById(followerSessionId);

            if (followerSession != null && followerSession.isOpen()) {
                try {
                    // Remove target and return to global mode
                    privateChatRegistry.removeTarget(followerSessionId);

                    systemResponseSender.sendSystem(
                            followerSession,
                            String.format(MessageConstants.MSG_DISCONNECT_NOTIFY, username)
                    );
                } catch (Exception e) {
                    log.error("Error notifying follower {}: {}", followerSessionId, e.getMessage());
                }
            }
        });
    }
}
