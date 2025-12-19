package org.example.VChatTalk.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {
    private final ConcurrentHashMap<String, WebSocketSession> sessions= new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(SessionRegistry.class);

    private final ConcurrentHashMap<String, String> sessionUsernames = new ConcurrentHashMap<>();


    public void addSession(WebSocketSession session){
        if(session != null && session.getId()!=null){
            sessions.put(session.getId(),session);
        }

    }

    public void removeSession(String sessionId) {
        if(sessionId != null){
            sessions.remove(sessionId);
            sessionUsernames.remove(sessionId);
        }

    }
    public String getUsername(String sessionId) {
        return sessionUsernames.getOrDefault(sessionId, "Anonymous");
    }

    public void registerUser(String sessionId, String name) {
        if (sessionId != null && name != null) {
            sessionUsernames.put(sessionId, name);
            logger.info("Registered user: {} with session: {}", name, sessionId);
        }
    }

    public WebSocketSession findSessionById(String sessionId){
        if(sessionId == null) {
            return null;
        }
        return sessions.get(sessionId);
    }
    public boolean isUserRegistered(String sessionId) {
        return sessionUsernames.containsKey(sessionId);
    }
    public Collection<WebSocketSession> getAllSessions(){
        return sessions.values();
    }
    public void countSessions(){
        logger.info("Active sessions({})", sessions.size());
    }


}
