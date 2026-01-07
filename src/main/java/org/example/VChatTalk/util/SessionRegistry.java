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

    private final ConcurrentHashMap<String, String> sessionUsernames = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, String> usernameSessions = new ConcurrentHashMap<>();

    // Map of private chat targets: key = sessionId (who is targeting), value = targetUsername (who is being targeted)
    private final ConcurrentHashMap<String, String> sessionTargets = new ConcurrentHashMap<>();

    public void addSession(WebSocketSession session){
        if(session != null && session.getId()!=null){
            sessions.put(session.getId(),session);
        }

    }

    public void removeSession(String sessionId) {
        if (sessionId == null) return;

        String username = sessionUsernames.remove(sessionId);
        if (username != null) {
            usernameSessions.remove(username);
        }
        sessionTargets.remove(sessionId);
        sessions.remove(sessionId);

        logger.info("Removed session {}", sessionId);
    }
    public String getUsername(String sessionId) {
        return sessionUsernames.getOrDefault(sessionId, "Anonymous");
    }

    public boolean tryRegisterUser(String sessionId, String username) {
        if (sessionId == null || username == null || username.isBlank()) {
            return false;
        }
        String existingSession =
                usernameSessions.putIfAbsent(username, sessionId);

        if (existingSession != null) {
            return false;
        }

        sessionUsernames.put(sessionId, username);

        logger.info("Registered user: '{}' with session ID: {}", username, sessionId);
        return true;
    }

    public WebSocketSession findSessionById(String sessionId){
        if(sessionId == null) {
            return null;
        }
        return sessions.get(sessionId);
    }
    /**
     * Retrieves the {@link WebSocketSession} associated with the given username.
     * <p>
     * This method looks up the internal username-to-session mapping and returns
     * the corresponding {@code WebSocketSession} if one is currently registered.
     *
     * @param username the username whose session should be retrieved; may be {@code null}
     * @return the {@link WebSocketSession} associated with the given username,
     *         or {@code null} if the username is not registered or if {@code username} is {@code null}
     */
    public WebSocketSession findSessionByUsername(String username) {
        String sessionId = usernameSessions.get(username);
        return sessionId != null ? sessions.get(sessionId) : null;
    }

    public boolean isUserRegistered(String sessionId) {
        return sessionUsernames.containsKey(sessionId);
    }

    public Collection<WebSocketSession> getAllSessions(){
        return Collections.unmodifiableCollection(sessions.values());
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
        if (sessionId == null) {
            return null;
        }
        return sessionTargets.get(sessionId);
    }

    // Remove target
    public void removeTarget(String sessionId) {
        if (sessionId != null) {
            sessionTargets.remove(sessionId);
        }
    }

    // Check if user is online
    public boolean isUserOnline(String username) {
        if (username == null) {
            return false;
        }
        return usernameSessions.containsKey(username);
    }


    // Find list of sessionIds targeting a single username
    public List<String> getSessionsTargeting(String targetUsername) {
        if (targetUsername == null) {
            return Collections.emptyList();
        }

        List<String> targetingSessions = new ArrayList<>();

        sessionTargets.forEach((sessionId, target) -> {
            if (target.equals(targetUsername)) {
                targetingSessions.add(sessionId);
            }
        });

        return Collections.unmodifiableList(targetingSessions);
    }
}
