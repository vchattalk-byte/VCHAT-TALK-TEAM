package org.example.VChatTalk;

import org.junit.jupiter.api.*;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PrivateChatE2ETest {

    private static final String WS_URL = "ws://localhost:8080/chat?userId=";

    @Test
    @Order(1)
    @DisplayName("Scenario 1: Basic private chat between A and B")
    void testBasicPrivateMessage() throws Exception {
        TestClient clientA = new TestClient("A", new URI(WS_URL + "A"));
        TestClient clientB = new TestClient("B", new URI(WS_URL + "B"));

        clientA.connectBlocking();
        clientB.connectBlocking();

        // Chờ welcome để chắc chắn kết nối ổn
        assertTrue(clientA.waitForWelcome(3000));
        assertTrue(clientB.waitForWelcome(3000));

        Thread.sleep(500);

        clientA.selectTarget("B");
        clientA.sendMessage("Hello from A to B");

        assertTrue(clientB.waitForMessageContaining("Hello from A to B", 5000),
                "B should receive private message from A");

        clientA.close();
        clientB.close();
    }

    @Test
    @Order(2)
    @DisplayName("Scenario 2: Offline message delivery")
    void testOfflineMessage() throws Exception {
        TestClient clientA = new TestClient("A", new URI(WS_URL + "A"));
        clientA.connectBlocking();
        assertTrue(clientA.waitForWelcome(3000));
        Thread.sleep(500);

        clientA.selectTarget("B");
        clientA.sendMessage("This is offline message for B");

        // B connect sau
        TestClient clientB = new TestClient("B", new URI(WS_URL + "B"));
        clientB.connectBlocking();
        assertTrue(clientB.waitForWelcome(3000));

        Thread.sleep(1000); // chờ server gửi offline message

        assertTrue(clientB.waitForMessageContaining("This is offline message for B", 5000),
                "B should receive offline message");

        clientA.close();
        clientB.close();
    }

    @Test
    @Order(3)
    @DisplayName("Scenario 3: Concurrency with 10 clients (ring)")
    void testConcurrency10Clients() throws Exception {
        int n = 10;
        TestClient[] clients = new TestClient[n];

        for (int i = 0; i < n; i++) {
            String id = "U" + (i + 1);
            clients[i] = new TestClient(id, new URI(WS_URL + id));
            clients[i].connectBlocking();
            assertTrue(clients[i].waitForWelcome(3000));
        }

        Thread.sleep(1000);

        // Mỗi client gửi cho client kế tiếp
        for (int i = 0; i < n; i++) {
            String target = "U" + ((i + 1) % n + 1);
            clients[i].selectTarget(target);
            clients[i].sendMessage("Hello from " + clients[i].userId + " to " + target);
            Thread.sleep(400); // tránh rate limit
        }

        Thread.sleep(3000);

        // Kiểm tra mỗi client nhận được tin từ người trước
        for (int i = 0; i < n; i++) {
            String expectedSender = "U" + ((i - 1 + n) % n + 1);
            assertTrue(clients[i].waitForMessageContaining("Hello from " + expectedSender, 3000),
                    clients[i].userId + " should receive message from " + expectedSender);
        }

        for (TestClient c : clients) c.close();
    }

    @Test
    @Order(4)
    @DisplayName("Scenario 4: Private chat isolation - C not receive message A→B")
    void testPrivateIsolation() throws Exception {
        TestClient a = new TestClient("A", new URI(WS_URL + "A"));
        TestClient b = new TestClient("B", new URI(WS_URL + "B"));
        TestClient c = new TestClient("C", new URI(WS_URL + "C"));

        a.connectBlocking();
        b.connectBlocking();
        c.connectBlocking();

        assertTrue(a.waitForWelcome(3000));
        assertTrue(b.waitForWelcome(3000));
        assertTrue(c.waitForWelcome(3000));

        Thread.sleep(500);

        a.selectTarget("B");
        a.sendMessage("Secret message only for B");

        Thread.sleep(1000);

        assertTrue(b.waitForMessageContaining("Secret message only for B", 2000));
        assertFalse(c.waitForMessageContaining("Secret message only for B", 1000),
                "C should NOT receive private message between A and B");

        a.close(); b.close(); c.close();
    }
}