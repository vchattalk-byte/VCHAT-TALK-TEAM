package org.example.VChatTalk.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserRegistry {
    private static final Logger logger = LoggerFactory.getLogger(UserRegistry.class);

    private final ConcurrentHashMap<String, String> sessionUsernames = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> usernameSessions = new ConcurrentHashMap<>();

    public boolean tryRegisterUser(String sessionId, String username) {
        if (sessionId == null || username == null || username.isBlank()) return false;

        String existingSession = usernameSessions.putIfAbsent(username, sessionId);
        if (existingSession != null) return false;

        String previousUsername = sessionUsernames.putIfAbsent(sessionId, username);
        if (previousUsername != null) {
            usernameSessions.remove(username, sessionId);
            return false;
        }

        logger.info("Registered user: '{}' with session ID: {}", username, sessionId);
        return true;
    }

    public void removeUser(String sessionId) {
        if (sessionId == null) return;
        String username = sessionUsernames.remove(sessionId);
        if (username != null) {
            usernameSessions.remove(username);
        }
    }

    public String getUsername(String sessionId) {
        return sessionUsernames.getOrDefault(sessionId, "Anonymous");
    }

    public boolean isUserRegistered(String sessionId) {
        return sessionUsernames.containsKey(sessionId);
    }

    public boolean isUserOnline(String username) {
        if (username == null) return false;
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
        logger.info("All online connections: {}", sessionUsernames.size());
    }
}
