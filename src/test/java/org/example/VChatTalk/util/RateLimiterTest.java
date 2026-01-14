package org.example.VChatTalk.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private RateLimiter rateLimiter;
    private final String SESSION_ID = "test-session-001";

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter();
        rateLimiter.clear(); // Ensure a clean state before each test
    }

    @Test
    @DisplayName("First message should not be rate limited")
    void isRateLimitExceeded_FirstMessage_Success() {
        boolean exceeded = rateLimiter.isRateLimitExceeded(SESSION_ID);
        assertFalse(exceeded, "The first message from a session should always be allowed.");
    }

    @Test
    @DisplayName("Rapid subsequent messages should be blocked as spam")
    void isRateLimitExceeded_TooFast_ReturnsTrue() {
        // First message allowed
        rateLimiter.isRateLimitExceeded(SESSION_ID);

        // Immediate second message (within < 200ms)
        boolean exceeded = rateLimiter.isRateLimitExceeded(SESSION_ID);

        assertTrue(exceeded, "Messages sent within less than 200ms should trigger the rate limit.");
    }

    @Test
    @DisplayName("Message after the 200ms cooldown should be allowed")
    void isRateLimitExceeded_AfterWait_Success() throws InterruptedException {
        rateLimiter.isRateLimitExceeded(SESSION_ID);

        // Wait for 250ms (exceeding the 200ms limit)
        Thread.sleep(250);

        boolean exceeded = rateLimiter.isRateLimitExceeded(SESSION_ID);
        assertFalse(exceeded, "Message should be allowed after waiting for the cooldown period.");
    }

    @Test
    @DisplayName("Different sessions should have independent rate limits")
    void isRateLimitExceeded_SessionsAreIndependent() {
        String sessionA = "User-A";
        String sessionB = "User-B";

        // User A sends a message
        rateLimiter.isRateLimitExceeded(sessionA);

        // User B sends a message immediately after
        boolean exceededB = rateLimiter.isRateLimitExceeded(sessionB);

        assertFalse(exceededB, "Rate limit for User A should not affect User B.");
    }

    @Test
    @DisplayName("Removing a session should reset its rate limit tracking")
    void removeSession_ResetsTracking() {
        // Trigger rate limit for session
        rateLimiter.isRateLimitExceeded(SESSION_ID);

        // Remove tracking for this session
        rateLimiter.removeSession(SESSION_ID);

        // Should be allowed to send again immediately
        boolean exceeded = rateLimiter.isRateLimitExceeded(SESSION_ID);
        assertFalse(exceeded, "Session should be able to send messages immediately after removal from registry.");
    }
}