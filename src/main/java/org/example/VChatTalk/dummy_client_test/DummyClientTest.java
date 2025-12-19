package org.example.VChatTalk.dummy_client_test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.Scanner;
import java.util.concurrent.CompletionStage;

public class DummyClientTest {

    private static WebSocket webSocket;
    private static String username;

    public static void main(String[] args) {

        System.out.println("=== Dummy CLI Chat Client (CHAT-013) ===");
        System.out.println("Commands:");
        System.out.println("  /join <username>");
        System.out.println("  /send <message>");
        System.out.println("  /exit");
        System.out.println("--------------------------------------");


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

        // 2️⃣ Read CLI input
        Scanner scanner = new Scanner(System.in);
        System.out.print("> ");

        while (true) {
            String input = scanner.nextLine().trim();

            if (input.startsWith("/join ")) {
                join(input);
            } else if (input.startsWith("/send ")) {
                send(input);
            } else if (input.equals("/exit")) {
                exit();
                break;
            } else {
                System.out.println("❌ Unknown command");
            }

            System.out.print("> ");
        }

        scanner.close();
    }

    // ===================== COMMAND HANDLERS =====================

    private static void join(String input) {
        username = input.substring(6).trim();

        if (username.isEmpty()) {
            System.out.println("❌ Username cannot be empty");
            return;
        }

        String json = """
                {
                  "type": "JOIN",
                  "sender": "%s"
                }
                """.formatted(username);

        webSocket.sendText(json, true);
    }

    private static void send(String input) {
        if (username == null) {
            System.out.println("❌ You must /join first");
            return;
        }

        String content = input.substring(6).trim();

        if (content.isEmpty()) {
            System.out.println("❌ Message cannot be empty");
            return;
        }

        String json = """
                {
                  "type": "MESSAGE",
                  "sender": "%s",
                  "content": "%s"
                }
                """.formatted(username, content);

        webSocket.sendText(json, true);
    }

    private static void exit() {
        if (username != null) {
            String json = """
                    {
                      "type": "LEAVE",
                      "sender": "%s"
                    }
                    """.formatted(username);

            webSocket.sendText(json, true);
        }

        webSocket.sendClose(
                WebSocket.NORMAL_CLOSURE,
                "Client exit"
        );

        System.out.println("👋 Exit chat");
    }

    // ===================== DISPLAY =====================

    private static void printIncomingMessage(String json) {
        try {
            String sender = extract(json, "sender");
            String content = extract(json, "content");
            String timestamp = extract(json, "timestamp");

            System.out.printf(
                    "Sender: %s — Content: %s — Time: %s%n",
                    sender,
                    content,
                    timestamp
            );
        } catch (Exception e) {
            // fallback nếu message không đủ field
            System.out.println("[RAW] " + json);
        }
    }

    // VERY SIMPLE JSON FIELD EXTRACT (no lib)
    private static String extract(String json, String field) {
        String key = "\"" + field + "\":";
        int start = json.indexOf(key);

        if (start == -1) return "N/A";

        start = json.indexOf("\"", start + key.length()) + 1;
        int end = json.indexOf("\"", start);

        return json.substring(start, end);
    }
}
