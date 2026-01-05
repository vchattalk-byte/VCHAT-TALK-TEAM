package org.example.VChatTalk.command.service;

import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CommandParserService {

    // Regex Explanation:
    // ^                 : Start of string
    // (/[a-zA-Z0-9]+)   : Group 1 - Command (Must start with '/' followed by alphanumeric characters)
    // (?:\\s+(.*))?     : Non-capturing Group (Optional part for argument)
    //    \\s+           : At least one whitespace separator
    //    (.*)           : Group 2 - Argument (Captures the rest of the string)
    // $                 : End of string
    private static final Pattern COMMAND_PATTERN = Pattern.compile("^(/[a-zA-Z0-9]+)(?:\\s+(.*))?$");

    public CommandResult parse(String rawText) {
        // 1. Guard Clause: Check for null or empty input
        if (rawText == null || rawText.isBlank()) {
            return new CommandResult(CommandType.NONE, null, null);
        }

        String trimmed = rawText.trim();

        // 2. Fast check: If it doesn't start with '/', treat as a regular chat message (NONE)
        if (!trimmed.startsWith("/")) {
            return new CommandResult(CommandType.NONE, null, null);
        }

        // 3. Use Regex to split Command and Argument
        Matcher matcher = COMMAND_PATTERN.matcher(trimmed);

        if (matcher.matches()) {
            // Group 1: The Command part (e.g., "/select")
            String commandStr = matcher.group(1);

            // Group 2: The Argument part (e.g., "Alice" or "User Name").
            // Can be null if the input is just a command like "/list".
            String argument = matcher.group(2);

            // 4. Lookup Enum (Static method call)
            CommandType type = CommandType.fromString(commandStr);

            if (type == CommandType.UNKNOWN) {
                return new CommandResult(
                        CommandType.UNKNOWN,
                        null,
                        "Unknown command: " + commandStr
                );
            }

            // 5. Return valid result
            return new CommandResult(type, argument, null);
        }

        // Case: Starts with '/' but doesn't match Regex format (e.g., "/@#$%" or "/ select")
        return new CommandResult(
                CommandType.UNKNOWN,
                null,
                "Invalid command format."
        );
    }
}