package org.example.VChatTalk.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the lifecycle of chat rooms and their members.
 *
 * Invariant:
 * A session can only belong to ONE room at a time.
 *
 * Concurrency model:
 * All public methods are synchronized to ensure STRONG CONSISTENCY.
 * Compound operations across internal data structures are atomic.
 *
 * No observable inconsistencies are allowed under concurrent access.
 */

@Slf4j
@Component
public class RoomRegistry {

    // Room ID -> Set of Session IDs
    private final ConcurrentHashMap<String, Set<String>> roomSessions = new ConcurrentHashMap<>();

    // Session ID -> Room ID (Primary Index for Invariant enforcement)
    private final ConcurrentHashMap<String, String> sessionRoomIndex = new ConcurrentHashMap<>();

    /**
     * Add a session to a room.
     * Auto-removes session from previous room if necessary.
     */
    public synchronized void joinRoom(String roomId, String sessionId) {
        // Validation: Fail fast for programming errors, but use DEBUG to reduce noise
        if (roomId == null || !roomId.startsWith("#") || roomId.length() <= 1) {
            log.debug("Join failed: Invalid roomId format '{}'", roomId);
            throw new IllegalArgumentException("Room ID must start with '#' and contain a name");
        }
        if (sessionId == null) {
            log.debug("Join failed: sessionId is null");
            throw new IllegalArgumentException("Session ID must not be null");
        }

        // 1. Invariant Enforcement: Atomically update index
        String previousRoom = sessionRoomIndex.put(sessionId, roomId);

        // Auto-leave previous room if different
        if (previousRoom != null && !previousRoom.equals(roomId)) {
            log.debug("Auto-leaving room [{}] to join [{}]", previousRoom, roomId);
            removeFromRoomSet(previousRoom, sessionId);
        }

        // 2. Update Derived View
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);

        log.info("Session [{}] joined room [{}]", sessionId, roomId);
    }

    /**
     * Remove a session from a specific room.
     */
    public synchronized void leaveRoom(String roomId, String sessionId) {
        if (roomId == null || sessionId == null) {
            log.debug("Leave failed: null params");
            return;
        }

        // 1. Remove from Index (Source of Truth)
        boolean removed = sessionRoomIndex.remove(sessionId, roomId);

        if (removed) {
            // 2. Update Derived View
            removeFromRoomSet(roomId, sessionId);
            log.info("Session [{}] left room [{}]", sessionId, roomId);
        }
    }

    /**
     * Convenience method to leave whatever room the user is currently in.
     * Simplifies the LeaveCommand logic.
     */
    public synchronized void leaveCurrentRoom(String sessionId) {
        if (sessionId == null) return;

        String currentRoom = sessionRoomIndex.get(sessionId);
        if (currentRoom != null) {
            leaveRoom(currentRoom, sessionId);
        }
    }

    /**
     * Helper to clean up roomSessions map
     */
    private void removeFromRoomSet(String roomId, String sessionId) {
        roomSessions.computeIfPresent(roomId, (id, members) -> {
            members.remove(sessionId);
            if (members.isEmpty()) {
                log.debug("Room [{}] is empty and removed", roomId);
                return null; // remove this room from the map
            }
            return members;
        });
    }

    /**
     * Get all members of a specific room.
     */
    public synchronized Set<String> getRoomMembers(String roomId) {
        if (roomId == null) return Collections.emptySet();

        Set<String> members = roomSessions.get(roomId);
        if (members == null) return Collections.emptySet();

        return Collections.unmodifiableSet(members);
    }

    /**
     * Find which room a specific session is currently in.
     */
    public synchronized Optional<String> getRoomOfSession(String sessionId) {
        if (sessionId == null) return Optional.empty();
        return Optional.ofNullable(sessionRoomIndex.get(sessionId));
    }
}