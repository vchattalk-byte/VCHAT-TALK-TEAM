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

    // Fast Lookup Map: Maps command string -> Enum constant.
    // Note: We use HashMap instead of EnumMap because the Key is a String ("/select"), not an Enum.
    private static final Map<String, CommandType> COMMAND_MAP = new HashMap<>();

    // Static initializer block: Populates the map once at startup
    static {
        for (CommandType type : values()) {
            if (type.command != null && !type.command.isBlank()) {
                COMMAND_MAP.put(type.command.toLowerCase(), type);
            }
        }
    }

    CommandType(String command, String description) {
        this.command = command;
        this.description = description;
    }

    /**
     * Finds the CommandType from a command string.
     * Performance: O(1) lookup using HashMap.
     *
     * @param text The raw command text (e.g., "/select", "/SELECT")
     * @return The matching CommandType, or UNKNOWN if not found.
     */
    public static CommandType fromString(String text) {
        if (text == null || text.isBlank()) {
            return NONE;
        }

        // Convert input to lowercase
        return COMMAND_MAP.getOrDefault(text.toLowerCase(), UNKNOWN);
    }
}