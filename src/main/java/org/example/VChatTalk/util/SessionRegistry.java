package org.example.VChatTalk.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.VChatTalk.model.ChatSession;
import org.example.VChatTalk.service.WebSocketChatSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class SessionRegistry {

    // Backward compatible: Keep original WebSocketSession map
    private final ConcurrentHashMap<String, WebSocketSession> webSocketSessions= new ConcurrentHashMap<>();

    // New: ChatSession abstraction map
    private final Map<String, ChatSession> chatSessions = new ConcurrentHashMap<>();

    private final UserRegistry userRegistry;
    private final ObjectMapper objectMapper;

    private static final Logger logger = LoggerFactory.getLogger(SessionRegistry.class);

    // ========== BACKWARD COMPATIBLE METHODS ==========

    public void addSession(WebSocketSession session) {
        if (session != null && session.getId() != null) {
            webSocketSessions.put(session.getId(), session);
            createChatSession(session); // Auto-create ChatSession
            logger.debug("Added session and auto-created ChatSession: {}", session.getId());
        }
    }

    public void removeSession(String sessionId) {
        if (sessionId == null) return;

        webSocketSessions.remove(sessionId);
        chatSessions.remove(sessionId); // Remove from both maps

        logger.info("Removed session from both registries: {}", sessionId);
    }

    public WebSocketSession findSessionById(String sessionId){
        if(sessionId == null) {
            return null;
        }
        return webSocketSessions.get(sessionId);
    }

    public Collection<WebSocketSession> getAllSessions(){
        return Collections.unmodifiableCollection(webSocketSessions.values());
    }

    // ========== NEW ABSTRACTION METHODS ==========

    /**
     * Find ChatSession by ID
     */
    public ChatSession findChatSessionById(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return chatSessions.get(sessionId);
    }

    /**
     * Get all ChatSessions
     */
    public Collection<ChatSession> getAllChatSessions() {
        return Collections.unmodifiableCollection(chatSessions.values());
    }

    /**
     * Check if session exists in either registry
     */
    public boolean hasSession(String sessionId) {
        return webSocketSessions.containsKey(sessionId) || chatSessions.containsKey(sessionId);
    }

    /**
     * Remove ChatSession only (for specific cleanup)
     */
    public void removeChatSession(String sessionId) {
        chatSessions.remove(sessionId);
        logger.debug("Removed ChatSession only: {}", sessionId);
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Internal method to create ChatSession from WebSocketSession
     */
    private void createChatSession(WebSocketSession webSocketSession) {
        if (webSocketSession == null || webSocketSession.getId() == null) {
            return;
        }

        // Check if already exists
        if (chatSessions.containsKey(webSocketSession.getId())) {
            logger.debug("ChatSession already exists for: {}", webSocketSession.getId());
            return;
        }

        ChatSession chatSession = new WebSocketChatSession(
                webSocketSession,
                userRegistry,
                objectMapper
        );
        chatSessions.put(webSocketSession.getId(), chatSession);
        logger.debug("Created ChatSession for: {}", webSocketSession.getId());
    }

    /**
     * Log session counts for debugging
     */
    public void countSessions() {
        logger.info("WebSocket Sessions: {}, ChatSessions: {}",
                webSocketSessions.size(),
                chatSessions.size());
    }

    /**
     * Get statistics about sessions
     */
    public Map<String, Object> getSessionStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("webSocketSessions", webSocketSessions.size());
        stats.put("chatSessions", chatSessions.size());
        stats.put("sessionIds", new ArrayList<>(webSocketSessions.keySet()));
        return stats;
    }
}
