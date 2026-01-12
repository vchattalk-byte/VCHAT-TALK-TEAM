package org.example.VChatTalk.command.impl;

import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
import org.example.VChatTalk.command.IChatCommand;
import org.example.VChatTalk.command.MessageConstants;
import org.example.VChatTalk.util.SystemResponseSender;
import org.example.VChatTalk.util.UserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Component
public class ExitCommand implements IChatCommand {
    private final UserRegistry userRegistry;
    private final SystemResponseSender responder;

    public ExitCommand(UserRegistry userRegistry, SystemResponseSender responder) {
        this.userRegistry = userRegistry;
        this.responder = responder;
    }

    @Override
    public CommandType getType() {
        return CommandType.EXIT;
    }

    @Override
    public void execute(WebSocketSession session, CommandResult result) throws IOException {
        String username = userRegistry.getUsername(session.getId());

        if (username != null && !username.equals(MessageConstants.USER_ANONYMOUS)) {
            try {
                responder.sendSystem(session, String.format(MessageConstants.MSG_GOODBYE, username));
            } catch (IOException ignored) {
                // Connection might already be closing
            }
        }

        // Close Session
        session.close(CloseStatus.NORMAL);
    }
}
