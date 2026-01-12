package org.example.VChatTalk.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserRegistry - User Management & Online Status")
class UserRegistryTest {

    private UserRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new UserRegistry();
    }

    // ========== REGISTRATION ==========

    @Test
    @DisplayName("Success: Register new user")
    void testTryRegisterUser_Success() {
        boolean result = registry.tryRegisterUser("sess-1", "alice");
        assertTrue(result);
        assertEquals("alice", registry.getUsername("sess-1"));
        assertTrue(registry.isUserOnline("alice"));
        assertEquals("sess-1", registry.getSessionId("alice"));
    }

    @Test
    @DisplayName("Fail: Username already taken")
    void testTryRegisterUser_UsernameTaken() {
        // First registration succeeds
        registry.tryRegisterUser("sess-1", "alice");

        // Second fails
        boolean result = registry.tryRegisterUser("sess-2", "alice");
        assertFalse(result);
        assertEquals(1, registry.countOnlineUsers());  // Still 1 user
    }

    @Test
    @DisplayName("Fail: Session already registered")
    void testTryRegisterUser_SessionTaken() {
        registry.tryRegisterUser("sess-1", "alice");
        boolean result = registry.tryRegisterUser("sess-1", "bob");  // Same session
        assertFalse(result);
        assertEquals("alice", registry.getUsername("sess-1"));  // alice kept
    }

    @Test
    @DisplayName("Fail: Null/blank inputs")
    void testTryRegisterUser_InvalidInputs() {
        assertFalse(registry.tryRegisterUser(null, "alice"));
        assertFalse(registry.tryRegisterUser("sess-1", null));
        assertFalse(registry.tryRegisterUser("sess-1", ""));
        assertFalse(registry.tryRegisterUser("sess-1", "   "));
    }

    // ========== LOOKUP ==========

    @Test
    @DisplayName("getUsername: Returns Anonymous for unregistered")
    void testGetUsername_Unregistered() {
        assertEquals("Anonymous", registry.getUsername("sess-999"));
    }

    @Test
    @DisplayName("getSessionId: Null for unregistered user")
    void testGetSessionId_Unregistered() {
        assertNull(registry.getSessionId("ghost"));
    }

    // ========== STATUS CHECKS ==========

    @Test
    @DisplayName("isUserOnline: False for unregistered")
    void testIsUserOnline_FalseCases() {
        assertFalse(registry.isUserOnline("nobody"));
        assertFalse(registry.isUserOnline(null));
    }

    @Test
    @DisplayName("isUserRegistered: False for unregistered session")
    void testIsUserRegistered_False() {
        assertFalse(registry.isUserRegistered("sess-unknown"));
    }

    // ========== REMOVAL ==========

    @Test
    @DisplayName("removeUser: Cleanup both mappings")
    void testRemoveUser() {
        registry.tryRegisterUser("sess-1", "alice");
        registry.removeUser("sess-1");

        assertEquals("Anonymous", registry.getUsername("sess-1"));
        assertFalse(registry.isUserOnline("alice"));
        assertNull(registry.getSessionId("alice"));
    }

    @Test
    @DisplayName("removeUser: Safe for unregistered")
    void testRemoveUser_Unregistered() {
        assertDoesNotThrow(() -> registry.removeUser("sess-unknown"));
    }

    // ========== LISTING & COUNTING ==========

    @Test
    @DisplayName("countOnlineUsers: Accurate count")
    void testCountOnlineUsers() {
        registry.tryRegisterUser("s1", "a");
        registry.tryRegisterUser("s2", "b");
        registry.tryRegisterUser("s3", "c");
        assertEquals(3, registry.countOnlineUsers());
    }

    @Test
    @DisplayName("getAllOnlineUsers: Returns all registered users")
    void testGetAllOnlineUsers() {
        registry.tryRegisterUser("s1", "alice");
        registry.tryRegisterUser("s2", "bob");

        Collection<String> users = registry.getAllOnlineUsers();
        assertEquals(2, users.size());
        assertTrue(users.contains("alice"));
        assertTrue(users.contains("bob"));
        assertFalse(users.contains("Anonymous"));
    }

    @Test
    @DisplayName("getAllOnlineUsers: Unmodifiable + thread-safe")
    void testGetAllOnlineUsers_Unmodifiable() {
        registry.tryRegisterUser("s1", "alice");
        Collection<String> users = registry.getAllOnlineUsers();

        assertThrows(UnsupportedOperationException.class, () -> users.add("bob"));
    }

    // ========== CONCURRENCY ==========

    @Test
    @DisplayName("Concurrent registration: Atomic username reservation")
    void testConcurrentRegistration() throws InterruptedException {
        // Simulate race condition
        Runnable registerAlice = () -> registry.tryRegisterUser("sess-race", "alice");

        Thread t1 = new Thread(registerAlice);
        Thread t2 = new Thread(registerAlice);
        t1.start(); t2.start();
        t1.join(); t2.join();

        // Only 1 should succeed
        assertEquals(1, registry.countOnlineUsers());
    }

    // ========== EDGE CASES ==========

    @Test
    @DisplayName("Long usernames handled correctly")
    void testLongUsername() {
        String longName = "a".repeat(50);
        boolean result = registry.tryRegisterUser("s1", longName);
        assertTrue(result);  // No length limit in registry
        assertEquals(longName, registry.getUsername("s1"));
    }

    @Test
    @DisplayName("Special characters in username")
    void testSpecialCharacters() {
        registry.tryRegisterUser("s1", "user@123!");
        assertEquals("user@123!", registry.getUsername("s1"));
        assertTrue(registry.isUserOnline("user@123!"));
    }
}
