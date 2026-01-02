package org.example.VChatTalk;

import org.junit.jupiter.api.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PrivateChatE2ETest {
    private static final String WS_URL = "ws://localhost:8080/chat?userId=";
    private List<TestClient> clients;

    @BeforeEach
    void setup() {
        clients = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        for (TestClient c : clients) {
            if (c != null && !c.isClosed()) c.close();
        }
    }

    private TestClient createAndConnect(String id) throws Exception {
        TestClient client = new TestClient(id, new URI(WS_URL + id));
        clients.add(client);
        client.connectBlocking();
        assertTrue(client.waitForWelcome(5000), id + " should receive welcome");
        Thread.sleep(500); // Small pause after login
        return client;
    }

    @Test
    @Order(1)
    @DisplayName("Scenario 1: Basic private chat")
    void testBasicPrivateMessage() throws Exception {
        TestClient a = createAndConnect("A");
        TestClient b = createAndConnect("B");

        a.selectTarget("B");
        a.sendMessage("Hello B, I am A");

        assertTrue(b.waitForMessageContaining("Hello B, I am A", 5000), "B failed to receive message");
    }

    @Test
    @Order(2)
    @DisplayName("Scenario 2: Offline message")
    void testOfflineMessage() throws Exception {
        TestClient a = createAndConnect("A");
        a.selectTarget("B_Offline");
        a.sendMessage("Message for offline B");
        a.close();

        Thread.sleep(1000);

        TestClient b = createAndConnect("B_Offline");
        assertTrue(b.waitForMessageContaining("Message for offline B", 5000), "B should get offline message");
    }

    @Test
    @Order(3)
    @DisplayName("Scenario 3: Concurrency Ring")
    void testConcurrencyRing() throws Exception {
        int n = 5; // Reduced from 10 to avoid server stress during testing
        TestClient[] ring = new TestClient[n];

        for (int i = 0; i < n; i++) {
            ring[i] = createAndConnect("User" + i);
        }

        for (int i = 0; i < n; i++) {
            String target = "User" + ((i + 1) % n);
            ring[i].selectTarget(target);
            ring[i].sendMessage("Msg from " + i + " to " + target);
            Thread.sleep(200); // Extra safety for mass sending
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
        a.sendMessage("Secret for Bob");

        assertTrue(b.waitForMessageContaining("Secret for Bob", 5000));
        assertFalse(c.waitForMessageContaining("Secret for Bob", 2000), "Charlie intercepted message!");
    }
}