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

    public void addSession(WebSocketSession session){
        if(session != null && session.getId()!=null){
            webSocketSessions.put(session.getId(),session);
        }

    }

    public void removeSession(String sessionId) {
        if (sessionId == null) return;
        webSocketSessions.remove(sessionId);

        logger.info("Removed session {}", sessionId);
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
     * New method: Register WebSocketSession as ChatSession
     */
    public void register(WebSocketSession webSocketSession) {
        // Backward compatibility: Also add to original map
        addSession(webSocketSession);

        // Create and store ChatSession abstraction
        ChatSession chatSession = new WebSocketChatSession(
                webSocketSession,
                userRegistry,
                objectMapper
        );
        chatSessions.put(webSocketSession.getId(), chatSession);

        logger.debug("Registered ChatSession for {}", webSocketSession.getId());
    }

    /**
     * New method: Find ChatSession by ID
     */
    public ChatSession findChatSessionById(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return chatSessions.get(sessionId);
    }

    /**
     * New method: Get all ChatSessions
     */
    public Collection<ChatSession> getAllChatSessions() {
        return Collections.unmodifiableCollection(chatSessions.values());
    }

    /**
     * New method: Check if session exists
     */
    public boolean hasSession(String sessionId) {
        return webSocketSessions.containsKey(sessionId) || chatSessions.containsKey(sessionId);
    }

    /**
     * New method: Remove ChatSession only
     */
    public void removeChatSession(String sessionId) {
        chatSessions.remove(sessionId);
    }

    // ========== PRIVATE HELPER ==========

    /**
     * Internal method to create ChatSession from WebSocketSession
     */
    private void registerAsChatSession(WebSocketSession webSocketSession) {
        ChatSession chatSession = new WebSocketChatSession(
                webSocketSession,
                userRegistry,
                objectMapper
        );
        chatSessions.put(webSocketSession.getId(), chatSession);
    }

    /**
     * Original method - kept for compatibility
     */
    public void countSessions() {
        logger.info("WebSocket Sessions: {}, ChatSessions: {}",
                webSocketSessions.size(),
                chatSessions.size());
    }

}
