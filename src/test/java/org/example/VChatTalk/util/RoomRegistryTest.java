package org.example.VChatTalk.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RoomRegistryTest {

    private RoomRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RoomRegistry();
    }

    /* =========================
       Validation tests
       ========================= */

    @Test
    void joinRoom_invalidRoomId_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.joinRoom("room", "s1"));

        assertThrows(IllegalArgumentException.class,
                () -> registry.joinRoom("#", "s1"));

        assertThrows(IllegalArgumentException.class,
                () -> registry.joinRoom("", "s1"));

        assertThrows(IllegalArgumentException.class,
                () -> registry.joinRoom(null, "s1"));
    }

    /* =========================
       Basic behavior tests
       ========================= */

    @Test
    void joinRoom_shouldAddSessionToRoom() {
        registry.joinRoom("#room", "s1");

        assertEquals(
                Set.of("s1"),
                registry.getRoomMembers("#room")
        );
        assertEquals(
                "#room",
                registry.getRoomOfSession("s1").orElseThrow()
        );
    }

    @Test
    void joinRoom_shouldAutoLeavePreviousRoom() {
        registry.joinRoom("#room1", "s1");
        registry.joinRoom("#room2", "s1");

        assertTrue(registry.getRoomMembers("#room1").isEmpty());
        assertEquals(
                Set.of("s1"),
                registry.getRoomMembers("#room2")
        );
    }

    @Test
    void leaveRoom_shouldRemoveSessionAndCleanupRoom() {
        registry.joinRoom("#room", "s1");

        registry.leaveRoom("#room", "s1");

        assertTrue(registry.getRoomMembers("#room").isEmpty());
        assertTrue(registry.getRoomOfSession("s1").isEmpty());
    }

    @Test
    void leaveRoom_nullArguments_shouldBeNoOp() {
        registry.leaveRoom(null, null);
        registry.leaveRoom("#room", null);
        registry.leaveRoom(null, "s1");
    }

    /* =========================
       Concurrent tests
       ========================= */

    @Test
    void joinRoom_concurrentJoins_shouldHandleCorrectly() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final String sessionId = "s" + i;
            executor.submit(() -> {
                try {
                    registry.joinRoom("#room", sessionId);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        Set<String> members = registry.getRoomMembers("#room");
        assertEquals(threadCount, members.size());

        for (int i = 0; i < threadCount; i++) {
            assertEquals(
                    "#room",
                    registry.getRoomOfSession("s" + i).orElseThrow()
            );
        }
    }

    @Test
    void joinRoom_concurrentRoomSwitching_shouldMaintainInvariant() throws InterruptedException {
        int iterations = 100;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);

        executor.submit(() -> {
            try {
                for (int i = 0; i < iterations; i++) {
                    registry.joinRoom(
                            i % 2 == 0 ? "#room1" : "#room2",
                            "s1"
                    );
                }
            } finally {
                latch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                for (int i = 0; i < iterations; i++) {
                    Optional<String> room = registry.getRoomOfSession("s1");
                    if (room.isPresent()) {
                        assertTrue(
                                registry.getRoomMembers(room.get()).contains("s1"),
                                "Session index and room membership inconsistent"
                        );
                    }
                }
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        Optional<String> finalRoom = registry.getRoomOfSession("s1");
        if (finalRoom.isPresent()) {
            assertTrue(
                    registry.getRoomMembers(finalRoom.get()).contains("s1")
            );

            String otherRoom =
                    finalRoom.get().equals("#room1") ? "#room2" : "#room1";

            assertFalse(
                    registry.getRoomMembers(otherRoom).contains("s1")
            );
        }
    }

    @Test
    void leaveRoom_concurrentLeaves_shouldHandleCorrectly() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            registry.joinRoom("#room", "s" + i);
        }

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final String sessionId = "s" + i;
            executor.submit(() -> {
                try {
                    registry.leaveRoom("#room", sessionId);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        assertTrue(registry.getRoomMembers("#room").isEmpty());

        for (int i = 0; i < threadCount; i++) {
            assertTrue(registry.getRoomOfSession("s" + i).isEmpty());
        }
    }
}
