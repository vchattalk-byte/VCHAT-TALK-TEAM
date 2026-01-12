package org.example.VChatTalk.util;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter to prevent message spam
 * Tracks last message timestamp per session
 */
@Component
public class RateLimiter {

    private static final long RATE_LIMIT_MS = 200L;
    private final ConcurrentHashMap<String, Long> lastMessageTime = new ConcurrentHashMap<>();

    /**
     * Check if session has exceeded rate limit
     * @param sessionId WebSocket session ID
     * @return true if rate limit exceeded, false otherwise
     */
    public boolean isRateLimitExceeded(String sessionId) {
        long now = System.currentTimeMillis();
        Long last = lastMessageTime.get(sessionId);

        if (last != null && now - last < RATE_LIMIT_MS) {
            return true;
        }

        lastMessageTime.put(sessionId, now);
        return false;
    }

    /**
     * Remove session from rate limiter when disconnected
     */
    public void removeSession(String sessionId) {
        if (sessionId != null) {
            lastMessageTime.remove(sessionId);
        }
    }

    /**
     * Clear all rate limit data (for testing/maintenance)
     */
    public void clear() {
        lastMessageTime.clear();
    }
}
