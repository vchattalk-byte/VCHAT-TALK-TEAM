package org.example.VChatTalk.command;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public enum CommandType {
    // Declare commands with prefix and description
    HELP("/help", "Show available commands"),
    SELECT("/select", "Private chat with user. Usage: /select <username>"),
    LIST("/list", "Show all online users"),
    LOGIN("/login", "Register username to start chatting. Usage: /login <username>"),
    JOIN("/join", "Join a specific chat room. Usage: /join <room_name>"), //Sprint 6
    LEAVE("/leave", "Leave private chat, room chat / return to global"),
    EXIT("/exit", "Disconnect from server"),

    NONE("", ""),
    UNKNOWN("", "Unknown command");

    private final String command;
    private final String description;

    private static final Map<String, CommandType> COMMAND_MAP = new HashMap<>();

    static {
        for (CommandType type : values()) {
            // Only include commands with actual prefixes in the Map (excluding NONE and UNKNOWN).
            if (type.command != null && !type.command.isBlank()) {
                COMMAND_MAP.put(type.command.toLowerCase(), type);
            }
        }
    }

    CommandType(String command, String description) {
        this.command = command;
        this.description = description;
    }

    // Static lookup function: Finds an Enum based on the command string (input)
    // Example: Input "/select" -> Returns CommandType.SELECT
    public static CommandType fromString(String text) {
        if (text == null || text.isBlank()) {
            return NONE;
        }

        // Convert input to lowercase
        return COMMAND_MAP.getOrDefault(text.toLowerCase(), UNKNOWN);
    }
}