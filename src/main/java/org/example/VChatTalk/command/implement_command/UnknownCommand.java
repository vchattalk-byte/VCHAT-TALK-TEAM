package org.example.VChatTalk.command.implement_command;

import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
import org.example.VChatTalk.command.IChatCommand;
import org.example.VChatTalk.util.SystemResponseSender;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Component
public class UnknownCommand implements IChatCommand {

    private final SystemResponseSender responder;

    public UnknownCommand(SystemResponseSender responder) {
        this.responder = responder;
    }

    @Override
    public CommandType getType() {
        return CommandType.UNKNOWN;
    }

    @Override
    public void execute(WebSocketSession session, CommandResult result) throws IOException {
        // Get error messages from Parser (if any), otherwise use the default message.
        String errorMessage = (result.getError() != null && !result.getError().isBlank())
                ? result.getError()
                : "Unknown command. Type /help for assistance.";

        responder.sendError(session, errorMessage);
    }
}
