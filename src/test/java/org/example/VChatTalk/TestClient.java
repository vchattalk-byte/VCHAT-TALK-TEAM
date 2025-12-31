package org.example.VChatTalk;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Dummy WebSocket client for automated E2E chat tests.
 */
public class TestClient extends WebSocketClient {

    public final String userId;
    private final BlockingQueue<String> receivedMessages = new LinkedBlockingQueue<>();
    private final List<String> allMessages = new ArrayList<>();

    public TestClient(String userId, URI serverUri) {
        super(serverUri);
        this.userId = userId;
    }

    @Override
    public void onOpen(ServerHandshake handshakeData) {
        System.out.println("[" + userId + "] connected");
    }

    @Override
    public void onMessage(String message) {
        System.out.println("[" + userId + "] received: " + message);
        receivedMessages.add(message);
        synchronized (allMessages) {
            allMessages.add(message);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("[" + userId + "] disconnected: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    // ----------------------------------------------------------
    // Commands
    // ----------------------------------------------------------

    /** Join the chat */
    public void join() throws Exception {
        Thread.sleep(500);
        String json = String.format("{\"type\":\"JOIN\",\"sender\":\"%s\"}", userId);
        send(json);
        waitForMessageContaining("joined the chat", 3000);
    }

    /** Select target user for private chat */
    public void selectTarget(String targetUserId) throws Exception {
        String json = String.format("{\"type\":\"MESSAGE\",\"content\":\"/select %s\"}", targetUserId);
        send(json);
        Thread.sleep(300);
    }

    /** Send a text message */
    public void sendMessage(String content) throws Exception {
        String json = String.format("{\"type\":\"MESSAGE\",\"content\":\"%s\"}", content);
        send(json);
        Thread.sleep(300);
    }

    // ----------------------------------------------------------
    // Utility checks
    // ----------------------------------------------------------

    /** Wait until message containing text appears */
    public boolean waitForMessageContaining(String text, long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            String msg = receivedMessages.poll(500, TimeUnit.MILLISECONDS);
            if (msg != null && msg.contains(text)) {
                return true;
            }
        }
        synchronized (allMessages) {
            return allMessages.stream().anyMatch(m -> m.contains(text));
        }
    }

    /** Check for private message from specific sender */
    public boolean hasPrivateMessageFrom(String senderId, String contentContains) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 6000;
        while (System.currentTimeMillis() < deadline) {
            String msg = receivedMessages.poll(500, TimeUnit.MILLISECONDS);
            if (msg != null && msg.contains(senderId) && msg.contains(contentContains)) {
                return true;
            }
        }
        synchronized (allMessages) {
            return allMessages.stream().anyMatch(
                    m -> m.contains(senderId) && m.contains(contentContains)
            );
        }
    }

    /** Check for welcome message */
    public boolean waitForWelcome(long timeoutMs) throws InterruptedException {
        return waitForMessageContaining("Welcome! You are connected to the chat server.", timeoutMs);
    }

    /** Check for error response */
    public boolean hasErrorContaining(String errorText, long timeoutMs) throws InterruptedException {
        return waitForMessageContaining(errorText, timeoutMs);
    }

    @Override
    public void close() {
        super.close();
        receivedMessages.clear();
        synchronized (allMessages) {
            allMessages.clear();
        }
    }
}
