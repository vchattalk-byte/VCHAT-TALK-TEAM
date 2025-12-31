package org.example.VChatTalk;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end WebSocket chat tests using dummy clients.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PrivateChatE2ETest {

    @LocalServerPort
    private int port;

    private String wsUrl() {
        return "ws://localhost:" + port + "/chat?user=";
    }

    @BeforeEach
    void beforeEach() {
        System.out.println("\n--- Starting test ---");
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        Thread.sleep(300);
        System.out.println("--- Test complete ---\n");
    }

    // ----------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("Scenario 1: Basic private message delivery")
    void testBasicPrivateMessage() throws Exception {
        TestClient a = null, b = null;
        try {
            a = new TestClient("A" + System.currentTimeMillis(), new URI(wsUrl() + "A"));
            b = new TestClient("B" + System.currentTimeMillis(), new URI(wsUrl() + "B"));
            a.connectBlocking();
            b.connectBlocking();

            assertTrue(a.waitForWelcome(3000));
            assertTrue(b.waitForWelcome(3000));

            a.join();
            b.join();

            a.selectTarget(b.userId);
            a.sendMessage("Hello from A to B");

            assertTrue(b.hasPrivateMessageFrom("A", "Hello from A to B"),
                    "B should receive private message from A");
        } finally {
            if (a != null) a.close();
            if (b != null) b.close();
        }
    }

    // ----------------------------------------------------------------------

    @Test
    @Order(2)
    @DisplayName("Scenario 2: Offline message error handling")
    void testOfflineMessage() throws Exception {
        TestClient a = null, b = null;
        try {
            a = new TestClient("A" + System.currentTimeMillis(), new URI(wsUrl() + "A"));
            a.connectBlocking();
            assertTrue(a.waitForWelcome(3000));
            a.join();

            a.selectTarget("BOffline");
            a.sendMessage("Offline message test");

            // Expect server to respond with an error
            assertTrue(a.hasErrorContaining("offline or does not exist", 5000),
                    "Server should report BOffline is offline, not store message");

            // Now B joins later (should not get the offline message)
            b = new TestClient("BOffline", new URI(wsUrl() + "BOffline"));
            b.connectBlocking();
            b.waitForWelcome(3000);
            b.join();

            assertFalse(b.waitForMessageContaining("Offline message test", 2000),
                    "B should NOT receive offline message later");
        } finally {
            if (a != null) a.close();
            if (b != null) b.close();
        }
    }

    // ----------------------------------------------------------------------

    @Test
    @Order(3)
    @DisplayName("Scenario 3: 10 concurrent clients")
    void testConcurrency10Clients() throws Exception {
        int n = 10;
        TestClient[] clients = new TestClient[n];
        try {
            for (int i = 0; i < n; i++) {
                String id = "U" + (i + 1) + "_" + System.currentTimeMillis();
                clients[i] = new TestClient(id, new URI(wsUrl() + id));
                clients[i].connectBlocking();
                clients[i].waitForWelcome(3000);
                clients[i].join();
            }

            for (int i = 0; i < n; i++) {
                String target = "U" + ((i + 1) % n + 1) + "_";
                clients[i].selectTarget(target);
                clients[i].sendMessage("Hello from " + clients[i].userId + " to " + target);
            }

            for (int i = 0; i < n; i++) {
                String expectedSender = "U" + ((i - 1 + n) % n + 1);
                assertTrue(clients[i].hasPrivateMessageFrom(expectedSender, "Hello"),
                        clients[i].userId + " should receive message from " + expectedSender);
            }
        } finally {
            for (TestClient c : clients) {
                if (c != null) c.close();
            }
        }
    }

    // ----------------------------------------------------------------------

    @Test
    @Order(4)
    @DisplayName("Scenario 4: Private chat isolation (no leaks)")
    void testPrivateIsolation() throws Exception {
        TestClient a = null, b = null, c = null;
        try {
            a = new TestClient("A" + System.currentTimeMillis(), new URI(wsUrl() + "A"));
            b = new TestClient("B" + System.currentTimeMillis(), new URI(wsUrl() + "B"));
            c = new TestClient("C" + System.currentTimeMillis(), new URI(wsUrl() + "C"));

            a.connectBlocking();
            b.connectBlocking();
            c.connectBlocking();

            a.waitForWelcome(3000);
            b.waitForWelcome(3000);
            c.waitForWelcome(3000);

            a.join();
            b.join();
            c.join();

            a.selectTarget(b.userId);
            a.sendMessage("Private message only for B");

            assertTrue(b.hasPrivateMessageFrom("A", "Private message only for B"),
                    "B should receive private message");
            assertFalse(c.waitForMessageContaining("Private message only for B", 2000),
                    "C must not see messages between A and B");
        } finally {
            if (a != null) a.close();
            if (b != null) b.close();
            if (c != null) c.close();
        }
    }
}
