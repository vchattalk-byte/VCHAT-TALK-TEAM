package org.example.VChatTalk.command;

import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CommandParserService {

    private static final Pattern SELECT_PATTERN =
            Pattern.compile("^/select\\s+(\\S+)$");
    private static final Pattern LIST_PATTERN =
            Pattern.compile("^/list$");
    private static final Pattern HELP_PATTERN =
            Pattern.compile("^/help$");


    public CommandResult parse(String rawText) {

        if (rawText == null || rawText.isBlank()) {
            return new CommandResult(
                    CommandType.NONE,
                    null,
                    null
            );
        }

        if (!rawText.startsWith("/")) {
            return new CommandResult(
                    CommandType.NONE,
                    null,
                    null
            );
        }

        Matcher selectMatcher = SELECT_PATTERN.matcher(rawText);
        if (selectMatcher.matches()) {
            return new CommandResult(
                    CommandType.SELECT,
                    selectMatcher.group(1),
                    null
            );
        }
        Matcher listMatcher = LIST_PATTERN.matcher(rawText);
        if (listMatcher.matches()) {
            return new CommandResult(
                    CommandType.LIST,
                    null,
                    null
            );
        }

        Matcher helpMatcher = HELP_PATTERN.matcher(rawText);
        if (helpMatcher.matches()) {
            return new CommandResult(
                    CommandType.HELP,
                    null,
                    null
            );
        }

        return new CommandResult(
                CommandType.UNKNOWN,
                null,
                "Unknown command. Type /help for more information."
        );
    }
}
