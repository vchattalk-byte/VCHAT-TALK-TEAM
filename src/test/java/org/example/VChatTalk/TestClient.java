package org.example.VChatTalk;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TestClient extends WebSocketClient {

    public final String userId;
    private final BlockingQueue<String> receivedMessages = new LinkedBlockingQueue<>();

    public TestClient(String userId, URI serverUri) {
        super(serverUri);
        this.userId = userId;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("[" + userId + "] connected");
    }

    @Override
    public void onMessage(String message) {
        System.out.println("[" + userId + "] received: " + message);
        receivedMessages.add(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("[" + userId + "] disconnected: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    // ================== COMMAND METHODS ==================

    /**
     * Chọn target user (JSON chuẩn)
     */
    public void selectTarget(String targetUserId) throws Exception {
        String json = String.format("{\"type\":\"SELECT\",\"target\":\"%s\"}", targetUserId);
        send(json);
        Thread.sleep(300);
    }

    /**
     * Gửi tin nhắn private (JSON chuẩn)
     */
    public void sendMessage(String content) throws Exception {
        String json = String.format("{\"type\":\"MESSAGE\",\"content\":\"%s\"}", content);
        send(json);
        Thread.sleep(200);
    }

    // ================== WAITING & ASSERT METHODS ==================

    /**
     * Chờ message chứa text cụ thể, timeout tùy chỉnh
     */
    public boolean waitForMessageContaining(String text, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String msg = receivedMessages.poll(500, TimeUnit.MILLISECONDS);
            if (msg != null && msg.contains(text)) {
                return true;
            }
            if (msg != null) {
                receivedMessages.add(msg); // trả lại queue nếu không match
            }
        }
        return false;
    }

    public boolean waitForMessageContaining(String text) throws InterruptedException {
        return waitForMessageContaining(text, 5000);
    }

    /**
     * Chờ welcome message để chắc chắn kết nối ổn định
     */
    public boolean waitForWelcome(long timeoutMs) throws InterruptedException {
        return waitForMessageContaining("Welcome! You are connected to the chat server.", timeoutMs);
    }

    /**
     * Kiểm tra có nhận tin nhắn private từ sender cụ thể không
     */
    public boolean hasPrivateMessageFrom(String senderId, String contentContains) throws InterruptedException {
        return waitForMessageContaining(senderId) && waitForMessageContaining(contentContains, 1000);
    }

    /**
     * Kiểm tra có nhận error message chứa text không
     */
    public boolean hasErrorContaining(String errorText, long timeoutMs) throws InterruptedException {
        return waitForMessageContaining(errorText, timeoutMs);
    }

    /**
     * Debug: in tất cả message đã nhận
     */
    public void printAllReceived() {
        System.out.println("[" + userId + "] All received messages:");
        receivedMessages.forEach(msg -> System.out.println("  -> " + msg));
    }

    @Override
    public void close() {
        super.close();
        receivedMessages.clear();
    }
}
