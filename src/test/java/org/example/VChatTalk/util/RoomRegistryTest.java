package org.example.VChatTalk.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RoomRegistryTest {

    private RoomRegistry roomRegistry;

    @BeforeEach
    void setUp() {
        roomRegistry = new RoomRegistry();
    }

    @Test
    void joinRoom_shouldAddSessionToRoom() {
        roomRegistry.joinRoom("#room1", "s1");

        Set<String> members = roomRegistry.getRoomMembers("#room1");

        assertEquals(1, members.size());
        assertTrue(members.contains("s1"));
        assertEquals("#room1", roomRegistry.getRoomOfSession("s1").orElseThrow());
    }

    @Test
    void joinRoom_shouldAutoLeavePreviousRoom() {
        roomRegistry.joinRoom("#room1", "s1");
        roomRegistry.joinRoom("#room2", "s1");

        assertFalse(roomRegistry.getRoomMembers("#room1").contains("s1"));
        assertTrue(roomRegistry.getRoomMembers("#room2").contains("s1"));
        assertEquals("#room2", roomRegistry.getRoomOfSession("s1").orElseThrow());
    }

    @Test
    void leaveRoom_shouldRemoveSessionAndCleanupRoom() {
        roomRegistry.joinRoom("#room1", "s1");

        roomRegistry.leaveRoom("#room1", "s1");

        assertTrue(roomRegistry.getRoomMembers("#room1").isEmpty());
        assertTrue(roomRegistry.getRoomOfSession("s1").isEmpty());
    }

    @Test
    void leaveCurrentRoom_shouldRemoveSessionFromItsRoom() {
        roomRegistry.joinRoom("#room1", "s1");

        roomRegistry.leaveCurrentRoom("s1");

        assertTrue(roomRegistry.getRoomMembers("#room1").isEmpty());
        assertTrue(roomRegistry.getRoomOfSession("s1").isEmpty());
    }

    @Test
    void getRoomMembers_shouldReturnUnmodifiableSet() {
        roomRegistry.joinRoom("#room1", "s1");

        Set<String> members = roomRegistry.getRoomMembers("#room1");

        assertThrows(UnsupportedOperationException.class,
                () -> members.add("s2"));
    }

    @Test
    void joinRoom_shouldRejectInvalidRoomId() {
        assertThrows(IllegalArgumentException.class,
                () -> roomRegistry.joinRoom("room1", "s1"));
    }

    @Test
    void leaveRoom_withWrongRoomShouldNotAffectCurrentRoom() {
        roomRegistry.joinRoom("#room1", "s1");

        roomRegistry.leaveRoom("#room2", "s1");

        assertEquals("#room1", roomRegistry.getRoomOfSession("s1").orElseThrow());
        assertTrue(roomRegistry.getRoomMembers("#room1").contains("s1"));
    }

    @Test
    void multipleSessions_shouldCoexistInSameRoom() {
        roomRegistry.joinRoom("#room1", "s1");
        roomRegistry.joinRoom("#room1", "s2");

        Set<String> members = roomRegistry.getRoomMembers("#room1");

        assertEquals(2, members.size());
        assertTrue(members.contains("s1"));
        assertTrue(members.contains("s2"));
    }
}
