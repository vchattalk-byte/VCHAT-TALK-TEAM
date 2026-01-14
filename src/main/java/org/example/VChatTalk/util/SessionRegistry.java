package org.example.VChatTalk.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {
    private final ConcurrentHashMap<String, WebSocketSession> sessions= new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(SessionRegistry.class);

    public void addSession(WebSocketSession session){
        if(session != null && session.getId()!=null){
            sessions.put(session.getId(),session);
        }

    }

    public void removeSession(String sessionId) {
        if (sessionId == null) return;
        sessions.remove(sessionId);

        logger.info("Removed session {}", sessionId);
    }

    public WebSocketSession findSessionById(String sessionId){
        if(sessionId == null) {
            return null;
        }
        return sessions.get(sessionId);
    }

    public Collection<WebSocketSession> getAllSessions(){
        return Collections.unmodifiableCollection(sessions.values());
    }

    public void countSessions() {
        logger.info("All session available: {}", sessions.size());
    }
}
