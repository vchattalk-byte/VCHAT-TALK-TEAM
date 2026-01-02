package org.example.VChatTalk;

import org.junit.jupiter.api.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests for private chat using TestClient.
 * Notes:
 * - Uses small configurable throttles to avoid server rate-limit while debugging.
 * - Ideally should wait for server ACKs (if server implements) instead of sleeps.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PrivateChatE2ETest {
    private static final String WS_URL = "ws://localhost:8080/chat?userId=";
    private static final long THROTTLE_MS = 200L; // adjust if server rate-limits
    private List<TestClient> clients;

    @BeforeEach
    void setup() {
        clients = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        for (TestClient c : clients) {
            if (c != null && !c.isClosed()) {
                try {
                    c.closeBlockingSafe(); // ensure fully closed
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private TestClient createAndConnect(String id) throws Exception {
        TestClient client = new TestClient(id, new URI(WS_URL + id));
        clients.add(client);
        client.connectBlocking();
        assertTrue(client.waitForWelcome(5000), id + " should receive welcome");
        Thread.sleep(50); // small pause after welcome
        return client;
    }

    @Test
    @Order(1)
    @DisplayName("Scenario 1: Basic private chat")
    void testBasicPrivateMessage() throws Exception {
        TestClient a = createAndConnect("A");
        TestClient b = createAndConnect("B");

        a.selectTarget("B");
        Thread.sleep(THROTTLE_MS); // temporary throttle to avoid rate-limit
        a.sendMessage("Hello B, I am A");

        assertTrue(b.waitForMessageContaining("Hello B, I am A", 5000), "B failed to receive message");
    }

    @Test
    @Order(2)
    @DisplayName("Scenario 2: Offline message")
    void testOfflineMessage() throws Exception {
        TestClient a = createAndConnect("A");
        a.selectTarget("B_Offline");
        Thread.sleep(THROTTLE_MS);
        a.sendMessage("Message for offline B");
        a.closeBlockingSafe(); // simulate disconnect

        // allow server to persist offline message if necessary
        Thread.sleep(300);

        TestClient b = createAndConnect("B_Offline");
        assertTrue(b.waitForMessageContaining("Message for offline B", 5000), "B should get offline message");
    }

    @Test
    @Order(3)
    @DisplayName("Scenario 3: Concurrency Ring")
    void testConcurrencyRing() throws Exception {
        int n = 5;
        TestClient[] ring = new TestClient[n];

        for (int i = 0; i < n; i++) ring[i] = createAndConnect("User" + i);

        for (int i = 0; i < n; i++) {
            String target = "User" + ((i + 1) % n);
            ring[i].selectTarget(target);
            Thread.sleep(THROTTLE_MS / 4);
            ring[i].sendMessage("Msg from " + i + " to " + target);
            Thread.sleep(THROTTLE_MS / 5);
        }

        for (int i = 0; i < n; i++) {
            assertTrue(ring[i].waitForMessageContaining("Msg from", 5000));
        }
    }

    @Test
    @Order(4)
    @DisplayName("Scenario 4: Privacy Isolation")
    void testIsolation() throws Exception {
        TestClient a = createAndConnect("Alice");
        TestClient b = createAndConnect("Bob");
        TestClient c = createAndConnect("Charlie");

        a.selectTarget("Bob");
        Thread.sleep(THROTTLE_MS);
        a.sendMessage("Secret for Bob");

        assertTrue(b.waitForMessageContaining("Secret for Bob", 5000));
        assertFalse(c.waitForMessageContaining("Secret for Bob", 2000), "Charlie intercepted message!");
    }
}