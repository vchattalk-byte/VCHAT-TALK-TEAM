package org.example.VChatTalk.command;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CommandParserService {

    private static final Pattern SELECT_PATTERN =
            Pattern.compile("^/select\\s+(\\S+)$");

    public CommandResult parse(String rawText) {

        if (rawText == null || rawText.isBlank()) {
            return new CommandResult(
                    CommandType.NONE,
                    null,
                    "",
                    null
            );
        }

        if (!rawText.startsWith("/")) {
            return new CommandResult(
                    CommandType.NONE,
                    null,
                    rawText,
                    null
            );
        }

        Matcher selectMatcher = SELECT_PATTERN.matcher(rawText);
        if (selectMatcher.matches()) {
            return new CommandResult(
                    CommandType.SELECT,
                    selectMatcher.group(1),
                    null,
                    null
            );
        }

        return new CommandResult(
                CommandType.UNKNOWN,
                null,
                null,
                "Invalid command"
        );
    }
}
