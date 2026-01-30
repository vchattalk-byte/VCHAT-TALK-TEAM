package org.example.VChatTalk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.VChatTalk.model.ChatSession;
import org.example.VChatTalk.model.MessageDTO;
import org.example.VChatTalk.util.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;

/**
 * Service for broadcasting messages to WebSocket sessions
 * Handles both global broadcasts and direct session messages
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BroadcastService {

    private final ObjectMapper mapper;
    private final SessionRegistry sessionRegistry;

    /**
     * Broadcast message to all connected sessions except sender
     * @param dto Message to broadcast
     * @param sender Sender session (null for system messages)
     * @deprecated Use {@link #broadcast(MessageDTO, ChatSession)} instead
     */
    @Deprecated
    public void broadcast(MessageDTO dto, WebSocketSession sender) {
        try {
            String json = mapper.writeValueAsString(dto);
            Collection<WebSocketSession> sessions = sessionRegistry.getAllSessions();
            boolean isSystemMessage = (sender == null);

            int successCount = 0;
            for (WebSocketSession session : sessions) {
                try {
                    // Send to all (if system message) or all except sender
                    if (session.isOpen() && (isSystemMessage || !session.getId().equals(sender.getId()))) {
                        session.sendMessage(new TextMessage(json));
                        successCount++;
                    }
                } catch (IOException e) {
                    log.warn("[BROADCAST_FAILED] Session: {} - {}", session.getId(), e.getMessage());
                    // Session cleanup handled by afterConnectionClosed in handler
                }
            }

            log.info("[BROADCAST] Sender: [{}] -> {} clients", dto.getSender(), successCount);
        } catch (IOException e) {
            log.error("[BROADCAST_ERROR] Failed to serialize message: {}", e.getMessage());
        }
    }

    /**
     * Send message to specific session only
     * @param session Target session
     * @param dto Message to send
     * @throws IOException if sending fails
     */
    @Deprecated
    public void sendToSession(WebSocketSession session, MessageDTO dto) throws IOException {
        if (session != null && session.isOpen()) {
            String json = mapper.writeValueAsString(dto);
            session.sendMessage(new TextMessage(json));
        }
    }

    // ========== NEW ABSTRACTION METHODS (ChatSession) ==========

    /**
     * NEW: Broadcast message to all connected ChatSessions except sender
     * @param dto Message to broadcast
     * @param excludeSession Session to exclude (null for system messages)
     */
    public void broadcast(MessageDTO dto, ChatSession excludeSession) {
        try {
            String json = mapper.writeValueAsString(dto);
            Collection<ChatSession> sessions = sessionRegistry.getAllChatSessions();
            boolean isSystemMessage = (excludeSession == null);

            int successCount = 0;
            for (ChatSession session : sessions) {
                try {
                    // Send to all (if system message) or all except excluded session
                    if (session.isOpen() && (isSystemMessage || !session.getId().equals(excludeSession.getId()))) {
                        // Use ChatSession's sendMessage method
                        session.sendMessage(dto);
                        successCount++;
                    }
                } catch (IOException e) {
                    log.warn("[BROADCAST_FAILED] ChatSession: {} - {}", session.getId(), e.getMessage());
                }
            }

            log.info("[BROADCAST] Sender: [{}] -> {} clients", dto.getSender(), successCount);
        } catch (Exception e) {
            log.error("[BROADCAST_ERROR] Failed to broadcast message: {}", e.getMessage());
        }
    }
}
