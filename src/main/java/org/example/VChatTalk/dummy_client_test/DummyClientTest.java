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

public class DummyClientTest {

    private static WebSocket webSocket;
    private static String username;

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    public static void main(String[] args) {

        System.out.println("=== Dummy CLI Chat Client (CHAT-013) ===");
        System.out.println("Commands:");
        System.out.println("  /help");
        System.out.println("  /join <username>");
        System.out.println("  /send <message>");
        System.out.println("  /exit");
        System.out.println("--------------------------------------");

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

        } catch (Exception e) {
            System.out.println("ERROR: Failed to connect to ws://localhost:8080/chat");
            return;
        }

        Scanner scanner = new Scanner(System.in);
        System.out.print("> ");

        while (true) {
            String input = scanner.nextLine().trim();

            if (input.startsWith("/join ")) {
                handleJoin(input);
            } else if (input.startsWith("/send ")) {
                handleSend(input);
            } else if (input.equals("/exit")) {
                handleExit();
                break;
            } else if (input.startsWith("/")) {
                sendMessage(
                        MessageDTO.builder()
                                .type(MessageType.MESSAGE)
                                .sender(username != null ? username : "unknown")
                                .content(input)
                                .timestamp(Instant.now())
                                .build()
                );
            } else {
                System.out.println("ERROR: Unknown command");
            }

            System.out.print("> ");
        }

        scanner.close();
    }

    // ===================== COMMAND HANDLERS =====================

    private static void handleJoin(String input) {
        String[] parts = input.split("\\s+", 2);

        if (parts.length < 2 || parts[1].isBlank()) {
            System.out.println("ERROR: Username cannot be empty");
            return;
        }

        username = parts[1];

        sendMessage(
                MessageDTO.builder()
                        .type(MessageType.JOIN)
                        .sender(username)
                        .timestamp(Instant.now())
                        .build()
        );
    }

    private static void handleSend(String input) {
        if (username == null) {
            System.out.println("ERROR: You must /join first");
            return;
        }

        String[] parts = input.split("\\s+", 2);

        if (parts.length < 2 || parts[1].isBlank()) {
            System.out.println("ERROR: Message cannot be empty");
            return;
        }

        sendMessage(
                MessageDTO.builder()
                        .type(MessageType.MESSAGE)
                        .sender(username)
                        .content(parts[1])
                        .timestamp(Instant.now())
                        .build()
        );
    }

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

        webSocket.sendClose(
                WebSocket.NORMAL_CLOSURE,
                "Client exit"
        );

        System.out.println("INFO: Exit chat");
    }

    // ===================== JSON SEND =====================

    private static void sendMessage(MessageDTO message) {
        try {
            String json = mapper.writeValueAsString(message);
            webSocket.sendText(json, true);
        } catch (Exception e) {
            System.out.println("ERROR: Failed to serialize message");
        }
    }

    // ===================== DISPLAY =====================

    private static void printIncomingMessage(String json) {
        try {
            MessageDTO message = mapper.readValue(json, MessageDTO.class);

            System.out.printf(
                    "Sender: %s | Type: %s | Content: %s | Time: %s%n",
                    message.getSender(),
                    message.getType(),
                    message.getContent(),
                    message.getTimestamp()
            );

        } catch (Exception e) {
            System.out.println("[RAW] " + json);
        }
    }
}
