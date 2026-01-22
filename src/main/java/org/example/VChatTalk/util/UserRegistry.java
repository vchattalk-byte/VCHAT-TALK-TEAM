package org.example.VChatTalk.util;

import org.example.VChatTalk.command.MessageConstants;
import org.example.VChatTalk.model.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserRegistry {
    private static final Logger logger = LoggerFactory.getLogger(UserRegistry.class);

    // Primary Store: SessionID -> UserSession (State)
    private final ConcurrentHashMap<String, UserSession> sessionUsernames = new ConcurrentHashMap<>();

    // Secondary Index: Username -> SessionID (Lookup)
    private final ConcurrentHashMap<String, String> usernameSessions = new ConcurrentHashMap<>();

    /**
     * Initialize logical state for a new connection.
     * @param sessionId The ID from the WebSocketSession
     */
    public void addSession(String sessionId) {
        if (sessionId == null) {
            logger.debug("UserRegistry addSession failed: sessionId is null.");
            return;
        }
        sessionUsernames.put(sessionId, UserSession.create(sessionId));
    }

    /**
     * Attempts to register a username.
     */
    public boolean tryRegisterUser(String sessionId, String username) {
        if (sessionId == null || username == null || username.isBlank()) {
            return false;
        }

        if (isUserRegistered(sessionId)) {
            return false;
        }

        // Check if username taken
        String existingSession = usernameSessions.putIfAbsent(username, sessionId);
        if (existingSession != null) {
            return false;
        }
        // Update UserSession State
        UserSession updated = sessionUsernames.computeIfPresent(sessionId, (id, userSession) ->
                userSession.withUsername(username)
        );

        if (updated == null) {
            usernameSessions.remove(username); // Rollback if session gone
            return false;
        }

        logger.info("Registered user: '{}' with session ID: {}", username, sessionId);
        return true;
    }

    /**
     * Update the entire session state (e.g. changing Context).
     */
    public void updateUser(UserSession session) {
        if (session != null) {
            sessionUsernames.put(session.getSessionId(), session);
        }
    }

    /**
     * Remove user state on disconnect.
     */
    public void removeUser(String sessionId) {
        if (sessionId == null) {
            logger.debug("UserRegistry removeUser failed: sessionId is null.");
            return;
        }

        UserSession removed = sessionUsernames.remove(sessionId);

        if (removed != null && !MessageConstants.USER_ANONYMOUS.equals(removed.getUsername())) {
            usernameSessions.remove(removed.getUsername());
            logger.info("Removed user: '{}'", removed.getUsername());
        }
    }

    // ========== LOOKUP METHODS ==========

    public UserSession getSession(String sessionId) {
        return sessionUsernames.get(sessionId);
    }

    public String getUsername(String sessionId) {
        UserSession session = sessionUsernames.get(sessionId);
        return (session != null) ? session.getUsername() : MessageConstants.USER_ANONYMOUS;
    }

    public boolean isUserRegistered(String sessionId) {
        UserSession session = sessionUsernames.get(sessionId);
        return session != null && !MessageConstants.USER_ANONYMOUS.equals(session.getUsername());
    }

    public boolean isUserOnline(String username) {
        if (username == null) {
            logger.debug("isUserOnline failed: username is null.");
            return false;
        }

        return usernameSessions.containsKey(username);
    }

    public String getSessionId(String username) {
        return usernameSessions.get(username);
    }

    public int countOnlineUsers() {
        return usernameSessions.size();
    }

    public Collection<String> getAllOnlineUsers() {
        return Collections.unmodifiableCollection(usernameSessions.keySet());
    }

    public void logCountOnlineUsers() {
        int count = Math.max(0, countOnlineUsers() - 1);
        logger.info("All online connections: {}", count);
    }
}
