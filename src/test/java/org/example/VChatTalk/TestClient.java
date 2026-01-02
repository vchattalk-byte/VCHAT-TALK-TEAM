package org.example.VChatTalk;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
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

    @Override public void onOpen(ServerHandshake h) { System.out.println("[" + userId + "] connected"); }
    @Override public void onClose(int c, String r, boolean rem) { System.out.println("[" + userId + "] disconnected"); }
    @Override public void onError(Exception ex) { ex.printStackTrace(); }

    @Override
    public void onMessage(String message) {
        System.out.println("[" + userId + "] received: " + message);
        receivedMessages.add(message);
    }

    public void selectTarget(String targetUserId) throws InterruptedException {
        String json = String.format("{\"type\":\"SELECT\",\"target\":\"%s\"}", targetUserId);
        send(json);
        Thread.sleep(600);
    }

    public void sendMessage(String content) throws InterruptedException {
        String json = String.format("{\"type\":\"MESSAGE\",\"content\":\"%s\"}", content);
        send(json);
        Thread.sleep(600);
    }

    public boolean waitForMessageContaining(String text, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        List<String> skippedMessages = new ArrayList<>(); // Temporarily store unmatched messages

        while (System.currentTimeMillis() < deadline) {
            String msg = receivedMessages.poll(100, TimeUnit.MILLISECONDS);
            if (msg != null) {
                if (msg.contains(text)) {
                    // Put back other messages into the queue to maintain order for other checks
                    receivedMessages.addAll(skippedMessages);
                    return true;
                }
                skippedMessages.add(msg);
            }
        }
        // Restore skipped messages if target text is not found
        receivedMessages.addAll(skippedMessages);
        return false;
    }

    public boolean waitForWelcome(long timeoutMs) throws InterruptedException {
        return waitForMessageContaining("Welcome!", timeoutMs);
    }

    public void clearMessages() {
        receivedMessages.clear();
    }
}