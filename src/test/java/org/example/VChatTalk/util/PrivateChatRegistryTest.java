package org.example.VChatTalk.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PrivateChatRegistry - Private Chat Targeting")
class PrivateChatRegistryTest {

    private PrivateChatRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PrivateChatRegistry();
    }

    // ========== TARGET LIFECYCLE ==========

    @Test
    @DisplayName("Success: Set → Get → Remove target")
    void testTargetLifecycle() {
        String sessionId = "s1";
        String target = "alice";

        assertNull(registry.getTarget(sessionId));

        registry.setTarget(sessionId, target);
        assertEquals(target, registry.getTarget(sessionId));

        registry.removeTarget(sessionId);
        assertNull(registry.getTarget(sessionId));
    }

    @Test
    @DisplayName("Null safety: Invalid inputs")
    void testNullSafety() {
        assertDoesNotThrow(() -> registry.setTarget(null, "alice"));
        assertDoesNotThrow(() -> registry.setTarget("s1", null));
        assertDoesNotThrow(() -> registry.removeTarget(null));
        assertNull(registry.getTarget(null));
    }

    // ========== REVERSE LOOKUP ==========

    @Test
    @DisplayName("getSessionsTargeting: Multiple sessions → 1 user")
    void testGetSessionsTargeting_Multiple() {
        registry.setTarget("s1", "alice");
        registry.setTarget("s2", "alice");
        registry.setTarget("s3", "bob");

        List<String> aliceFollowers = registry.getSessionsTargeting("alice");
        assertEquals(2, aliceFollowers.size());
        assertTrue(aliceFollowers.contains("s1"));
        assertTrue(aliceFollowers.contains("s2"));
    }

    @Test
    @DisplayName("getSessionsTargeting: No followers → empty list")
    void testGetSessionsTargeting_Empty() {
        List<String> result = registry.getSessionsTargeting("nobody");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getSessionsTargeting: Unmodifiable list")
    void testGetSessionsTargeting_Unmodifiable() {
        registry.setTarget("s1", "alice");
        List<String> result = registry.getSessionsTargeting("alice");

        assertThrows(UnsupportedOperationException.class, () -> result.add("s2"));
    }

    // ========== CONCURRENCY ==========

    @Test
    @DisplayName("Concurrent setTarget: No race conditions")
    void testConcurrentSetTarget() throws InterruptedException {
        Runnable setAlice = () -> registry.setTarget("sess-race", "alice");

        Thread t1 = new Thread(setAlice);
        Thread t2 = new Thread(setAlice);
        t1.start(); t2.start();
        t1.join(); t2.join();

        assertEquals("alice", registry.getTarget("sess-race"));
    }

    // ========== EDGE CASES ==========

    @Test
    @DisplayName("Remove non-existent target")
    void testRemoveNonExistent() {
        assertDoesNotThrow(() -> registry.removeTarget("non-existent"));
        assertNull(registry.getTarget("non-existent"));
    }

    @Test
    @DisplayName("Multiple targets for same session → last wins")
    void testMultipleSetTarget() {
        registry.setTarget("s1", "alice");
        registry.setTarget("s1", "bob");
        assertEquals("bob", registry.getTarget("s1"));

        List<String> aliceFollowers = registry.getSessionsTargeting("alice");
        assertTrue(aliceFollowers.isEmpty());
    }

    @Test
    @DisplayName("Clear all targets for user")
    void testRemoveAllFollowers() {
        registry.setTarget("s1", "alice");
        registry.setTarget("s2", "alice");

        registry.removeTarget("s1");
        List<String> followers = registry.getSessionsTargeting("alice");
        assertEquals(1, followers.size());  // s2 still targeting
        assertTrue(followers.contains("s2"));
    }
}
