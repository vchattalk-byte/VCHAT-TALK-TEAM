package org.example.VChatTalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * WebSocket test client for E2E tests.
 * - Uses Jackson to safely build JSON.
 * - Logs payload (text + hex) to help debug parsing issues on server side.
 * - Thread-safe snapshot search for expected messages.
 * - closeBlockingSafe() to guarantee blocking close in test teardown.
 */
public class TestClient extends WebSocketClient {
    public final String userId;
    private final BlockingQueue<String> receivedMessages = new LinkedBlockingQueue<>();
    private static final ObjectMapper MAPPER = new ObjectMapper(); // shared mapper

    public TestClient(String userId, URI serverUri) {
        super(serverUri);
        this.userId = userId;
    }

    @Override
    public void onOpen(ServerHandshake h) {
        System.out.println("[" + userId + "] connected");
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("[" + userId + "] disconnected");
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onMessage(String message) {
        System.out.println("[" + userId + "] received: " + message);
        receivedMessages.offer(message); // non-blocking
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    /**
     * Safely build SELECT JSON and send as text frame.
     * Logs text and hex bytes for debug.
     */
    public void selectTarget(String targetUserId) throws Exception {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "SELECT");
        node.put("target", targetUserId);
        String payload = MAPPER.writeValueAsString(node);

        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        System.out.println("[" + userId + "] sending (text): " + payload);
        System.out.println("[" + userId + "] sending (hex):  " + toHex(bytes));

        send(payload); // send text frame
    }

    /**
     * Safely build MESSAGE JSON and send as text frame.
     * Logs text and hex bytes for debug.
     */
    public void sendMessage(String content) throws Exception {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "MESSAGE");
        node.put("content", content);
        String payload = MAPPER.writeValueAsString(node);

        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        System.out.println("[" + userId + "] sending (text): " + payload);
        System.out.println("[" + userId + "] sending (hex):  " + toHex(bytes));

        send(payload); // send text frame
    }

    /**
     * Wait up to timeoutMs for any received message containing text.
     * Uses snapshot to avoid re-adding or race.
     */
    public boolean waitForMessageContaining(String text, long timeoutMs) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String[] snapshot = receivedMessages.toArray(new String[0]);
            for (String msg : snapshot) {
                if (msg != null && msg.contains(text)) return true;
            }
            Thread.sleep(50);
        }
        return false;
    }

    public boolean waitForWelcome(long timeoutMs) throws InterruptedException {
        return waitForMessageContaining("Welcome!", timeoutMs);
    }

    @Override
    public void close() {
        // non-blocking close, avoid calling closeBlocking() here to prevent recursion issues
        super.close();
        // best-effort cleanup
        receivedMessages.clear();
    }

    /**
     * Blocking close for teardown to ensure connection fully closed before continuing.
     */
    public void closeBlockingSafe() {
        try {
            super.closeBlocking();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            receivedMessages.clear();
        }
    }

    public boolean isClosed() {
        return getReadyState() == org.java_websocket.enums.ReadyState.CLOSED;
    }
}