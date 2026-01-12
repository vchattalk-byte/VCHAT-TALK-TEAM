package org.example.VChatTalk.command.impl;

import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
import org.example.VChatTalk.command.IChatCommand;
import org.example.VChatTalk.util.SystemResponseSender;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Component
public class HelpCommand implements IChatCommand {
    private final SystemResponseSender responder;

    public HelpCommand(SystemResponseSender responder) {
        this.responder = responder;
    }

    @Override
    public CommandType getType() {
        return CommandType.HELP;
    }

    @Override
    public void execute(WebSocketSession session, CommandResult result) throws IOException {
        StringBuilder helpBuilder = new StringBuilder();
        helpBuilder.append("Available commands:\n");

        for (CommandType type : CommandType.values()) {
            if (type != CommandType.NONE && type != CommandType.UNKNOWN && type.getCommand() != null && !type.getCommand().isEmpty()) {
                helpBuilder.append(String.format("%-15s : %s\n",
                        type.getCommand(),
                        type.getDescription()));
            }
        }

        responder.sendSystem(session, helpBuilder.toString());
    }
}
