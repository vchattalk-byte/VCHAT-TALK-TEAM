package org.example.VChatTalk.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoomRegistryTest {

    private RoomRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RoomRegistry();
    }

    // ---------- joinRoom validation ----------

    @Test
    void joinRoom_withNullRoomId_shouldThrowException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> registry.joinRoom(null, "s1")
        );

        assertTrue(ex.getMessage().contains("Room ID"));
    }

    @Test
    void joinRoom_withEmptyRoomId_shouldThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.joinRoom("", "s1")
        );
    }

    @Test
    void joinRoom_withHashOnlyRoomId_shouldThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.joinRoom("#", "s1")
        );
    }

    @Test
    void joinRoom_withRoomIdWithoutHash_shouldThrowException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.joinRoom("room1", "s1")
        );
    }

    @Test
    void joinRoom_withNullSessionId_shouldThrowException() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> registry.joinRoom("#room", null)
        );

        assertTrue(ex.getMessage().contains("Session ID"));
    }

    // ---------- joinRoom behavior ----------

    @Test
    void joinRoom_shouldAddSessionToRoom() {
        registry.joinRoom("#room", "s1");

        Set<String> members = registry.getRoomMembers("#room");
        assertEquals(1, members.size());
        assertTrue(members.contains("s1"));
    }

    @Test
    void joinRoom_shouldAllowMultipleSessions() {
        registry.joinRoom("#room", "s1");
        registry.joinRoom("#room", "s2");

        Set<String> members = registry.getRoomMembers("#room");
        assertEquals(2, members.size());
    }

    @Test
    void joinRoom_sameRoomTwice_shouldNotDuplicateSession() {
        registry.joinRoom("#room", "s1");
        registry.joinRoom("#room", "s1");

        Set<String> members = registry.getRoomMembers("#room");
        assertEquals(1, members.size());
    }

    @Test
    void joinRoom_shouldAutoLeavePreviousRoom() {
        registry.joinRoom("#room1", "s1");
        registry.joinRoom("#room2", "s1");

        assertFalse(registry.getRoomMembers("#room1").contains("s1"));
        assertTrue(registry.getRoomMembers("#room2").contains("s1"));
    }

    // ---------- getRoomOfSession ----------

    @Test
    void getRoomOfSession_shouldReturnEmpty_forUnknownSession() {
        assertTrue(registry.getRoomOfSession("unknown").isEmpty());
    }

    @Test
    void getRoomOfSession_shouldReturnCurrentRoom() {
        registry.joinRoom("#room", "s1");

        assertEquals("#room", registry.getRoomOfSession("s1").orElseThrow());
    }

    // ---------- leaveRoom ----------

    @Test
    void leaveRoom_shouldRemoveSession() {
        registry.joinRoom("#room", "s1");
        registry.leaveRoom("#room", "s1");

        assertTrue(registry.getRoomMembers("#room").isEmpty());
        assertTrue(registry.getRoomOfSession("s1").isEmpty());
    }

    @Test
    void leaveRoom_withWrongRoom_shouldDoNothing() {
        registry.joinRoom("#room1", "s1");
        registry.leaveRoom("#room2", "s1");

        assertTrue(registry.getRoomMembers("#room1").contains("s1"));
    }

    @Test
    void leaveRoom_withNullArguments_shouldDoNothing() {
        assertDoesNotThrow(() -> registry.leaveRoom(null, "s1"));
        assertDoesNotThrow(() -> registry.leaveRoom("#room", null));
        assertDoesNotThrow(() -> registry.leaveRoom(null, null));
    }
}
