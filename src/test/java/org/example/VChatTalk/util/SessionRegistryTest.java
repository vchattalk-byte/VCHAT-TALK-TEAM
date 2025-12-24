package org.example.VChatTalk.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionRegistryTest {

    private SessionRegistry registry;

    @BeforeEach
    void setUp() {
        // Initialize a clean registry before each test
        registry = new SessionRegistry();
    }

    // 1. Test basic lifecycle: Set -> Get -> Remove
    @Test
    void testTargetLifecycle() {
        String sessionId = "s1";
        String targetName = "Alice";

        // Initially null
        assertNull(registry.getTarget(sessionId));

        // SET
        registry.setTarget(sessionId, targetName);
        assertEquals(targetName, registry.getTarget(sessionId), "Target name should match after setting");

        // REMOVE
        registry.removeTarget(sessionId);
        assertNull(registry.getTarget(sessionId), "Target should be null after removal");
    }

    // 2. Test Reverse Lookup: Who is targeting this user?
    @Test
    void testGetSessionsTargeting() {
        // Scenario: s1 and s2 target Alice, s3 targets Bob
        registry.setTarget("s1", "Alice");
        registry.setTarget("s2", "Alice");
        registry.setTarget("s3", "Bob");

        // Verify followers for Alice
        List<String> followersAlice = registry.getSessionsTargeting("Alice");

        assertEquals(2, followersAlice.size(), "Alice should have 2 followers");
        assertTrue(followersAlice.contains("s1"));
        assertTrue(followersAlice.contains("s2"));

        // Verify followers for Bob
        List<String> followersBob = registry.getSessionsTargeting("Bob");
        assertEquals(1, followersBob.size());
        assertTrue(followersBob.contains("s3"));

        // Verify user with no followers
        List<String> followersNobody = registry.getSessionsTargeting("Charlie");
        assertTrue(followersNobody.isEmpty());
    }

    // 3. Test Online/Offline check
    @Test
    void testIsUserOnline() {
        String sessionId = "s1";
        String username = "Alice";

        // Initially offline
        assertFalse(registry.isUserOnline(username));

        // Register user
        registry.tryRegisterUser(sessionId, username);

        // Should be online now
        assertTrue(registry.isUserOnline(username));

        // Check non-existent user -> False
        assertFalse(registry.isUserOnline("Ghost"));
    }

    // 4. Test Edge Cases (Null Safety) - prevent NullPointerException
    @Test
    void testEdgeCases_NullInputs() {
        // Set null -> Should not throw exception
        assertDoesNotThrow(() -> registry.setTarget(null, "Alice"));
        assertDoesNotThrow(() -> registry.setTarget("s1", null));

        // Remove null -> Should not throw exception
        assertDoesNotThrow(() -> registry.removeTarget(null));

        // Get null -> Should return null
        assertNull(registry.getTarget(null));

        // Get targeting null -> Should return empty list (not null)
        List<String> result = registry.getSessionsTargeting(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Check online null -> False
        assertFalse(registry.isUserOnline(null));
    }
}