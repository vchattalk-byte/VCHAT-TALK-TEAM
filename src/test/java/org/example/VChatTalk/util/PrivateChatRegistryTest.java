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

    // ========== REVERSE LOOKUP (O(1) Feature) ==========

    @Test
    @DisplayName("getSessionsTargeting: Multiple sessions → 1 user")
    void testGetSessionsTargeting_Multiple() {
        registry.setTarget("s1", "alice");
        registry.setTarget("s2", "alice");
        registry.setTarget("s3", "bob");

        List<String> aliceFollowers = List.copyOf(registry.getSessionsTargeting("alice"));
        assertEquals(2, aliceFollowers.size());
        assertTrue(aliceFollowers.contains("s1"));
        assertTrue(aliceFollowers.contains("s2"));
    }

    @Test
    @DisplayName("Memory Leak Check: Should cleanup Reverse Index when empty")
    void testReverseIndexCleanup() {
        // 1. Register s1 targeting alice
        registry.setTarget("s1", "alice");
        assertFalse(registry.getSessionsTargeting("alice").isEmpty());

        // 2. Remove s1
        registry.removeTarget("s1");

        // 3. Verify alice is completely gone from the reverse index
        assertTrue(registry.getSessionsTargeting("alice").isEmpty(),
                "Reverse index should be empty/cleaned up");
    }

    @Test
    @DisplayName("getSessionsTargeting: Unmodifiable list")
    void testGetSessionsTargeting_Unmodifiable() {
        registry.setTarget("s1", "alice");
        var result = registry.getSessionsTargeting("alice");

        assertThrows(UnsupportedOperationException.class, () -> result.add("s2"));
    }

    // ========== EDGE CASES ==========

    @Test
    @DisplayName("Switch Target: Should clean old mapping and add new")
    void testSwitchTarget() {
        // s1: alice -> bob
        registry.setTarget("s1", "alice");
        assertTrue(registry.getSessionsTargeting("alice").contains("s1"));

        registry.setTarget("s1", "bob");

        // Verify s1 removed from alice
        assertFalse(registry.getSessionsTargeting("alice").contains("s1"));
        // Verify s1 added to bob
        assertTrue(registry.getSessionsTargeting("bob").contains("s1"));
        assertEquals("bob", registry.getTarget("s1"));
    }
}