package org.example.VChatTalk.dummy_client_test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.VChatTalk.model.MessageDTO;
import org.example.VChatTalk.model.MessageType;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.Scanner;
import java.util.concurrent.CompletionStage;

/**
 * Simple CLI WebSocket client for VChat-Talk testing
 * All logic is handled by server
 */
public class DummyClientTest {

    private static WebSocket webSocket;
    private static String username;

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public static void main(String[] args) {
        System.out.println("=== VChat-Talk CLI Client ===");
        System.out.println("Getting started:");
        System.out.println("  1. /login <username>  - Login to connect to the chat");
        System.out.println("  2. /join <room>       - Join room. Need login first");
        System.out.println("  3. /select <username> - Private chat. Need login first");
        System.out.println("  4. /help              - Show all available commands");
        System.out.println("  5. /exit              - Exit client");
        System.out.println("----------------------------------------------------");

        try {
            HttpClient client = HttpClient.newHttpClient();

            webSocket = client.newWebSocketBuilder()
                    .buildAsync(
                            URI.create("ws://localhost:8080/chat"),
                            new WebSocket.Listener() {

                                @Override
                                public CompletionStage<?> onText(
                                        WebSocket ws,
                                        CharSequence data,
                                        boolean last) {

                                    printIncomingMessage(data.toString());
                                    System.out.print("> ");
                                    return WebSocket.Listener.super
                                            .onText(ws, data, last);
                                }
                            }
                    ).join();

            System.out.println("Connected to server");

        } catch (Exception e) {
            System.out.println("ERROR: Failed to connect to ws://localhost:8080/chat");
            System.out.println("Make sure the server is running!");
            return;
        }

        Scanner scanner = new Scanner(System.in);
        System.out.print("> ");

        while (true) {
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                System.out.print("> ");
                continue;
            }

            // Local exit command only
            if (input.equals("/exit")) {
                handleExit();
                break;
            }

            // Handle /join locally to track username
            if (input.startsWith("/login ")) {
                String[] parts = input.split("\\s+", 2);
                if (parts.length >= 2) {
                    username = parts[1];
                }
            }

            // Send everything to server (including /join)
            sendMessage(
                    MessageDTO.builder()
                            .type(input.startsWith("/login") ? MessageType.JOIN : MessageType.MESSAGE)
                            .sender(username != null ? username : "Guest")
                            .content(input.startsWith("/login") ? null : input)
                            .timestamp(Instant.now())
                            .build()
            );
        }

        scanner.close();
    }

    // ===================== EXIT COMMAND =====================

    private static void handleExit() {
        if (username != null) {
            sendMessage(
                    MessageDTO.builder()
                            .type(MessageType.LEAVE)
                            .sender(username)
                            .timestamp(Instant.now())
                            .build()
            );
        }

        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client exit");
        System.out.println("Goodbye!");
    }

    // ===================== SEND/RECEIVE =====================

    private static void sendMessage(MessageDTO message) {
        try {
            String json = mapper.writeValueAsString(message);
            webSocket.sendText(json, true);
        } catch (Exception e) {
            System.out.println("ERROR: Failed to send message");
        }
    }

    private static void printIncomingMessage(String json) {
        try {
            MessageDTO message = mapper.readValue(json, MessageDTO.class);

            System.out.println();

            String content = message.getContent() != null ? message.getContent() : "";

            // Simple display based on message type
            switch (message.getType()) {
                case SYSTEM:
                    System.out.println("[SYSTEM] " + content);
                    break;

                case ERROR:
                    System.out.println("[ERROR] " + content);
                    break;

                case MESSAGE:
                    System.out.println(message.getSender() + ": " + content);
                    break;

                default:
                    System.out.println(message.getSender() + ": " + content);
            }

        } catch (Exception e) {
            System.out.println("[RAW] " + json);
        }
    }
}