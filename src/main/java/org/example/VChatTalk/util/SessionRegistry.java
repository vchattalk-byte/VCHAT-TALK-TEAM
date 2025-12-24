package org.example.VChatTalk.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {
    private final ConcurrentHashMap<String, WebSocketSession> sessions= new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(SessionRegistry.class);

    private final ConcurrentHashMap<String, String> sessionUsernames = new ConcurrentHashMap<>();

    // Map target: key = sessionId, value = username
    private final ConcurrentHashMap<String, String> sessionTargets = new ConcurrentHashMap<>();

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

    // Set target for current session
    public void setTarget(String sessionId, String targetUsername) {
        if (sessionId != null && targetUsername != null) {
            sessionTargets.put(sessionId, targetUsername);
        }
    }

    // Get target for current session
    public String getTarget(String sessionId) {
        return sessionTargets.get(sessionId);
    }

    // Remove target
    public void removeTarget(String sessionId) {
        if (sessionId != null) {
            sessionTargets.remove(sessionId);
        }
    }

    // Check user is online or offline
    public boolean isUserOnline(String username) {
        if (username == null){
            return false;
        }

        return sessionUsernames.containsValue(username);
    }

    // Find list of sessionId is targeted to one username
    public List<String> getSessionsTargeting (String targetUsername) {
        List<String> targetingSessions = new ArrayList<>();

        if (targetUsername == null) {
            return targetingSessions;
        }

        sessionTargets.forEach((sessionId, target) -> {
            if (target.equals(targetUsername)) {
                targetingSessions.add(sessionId);
            }
        });

        return targetingSessions;
    }
}
