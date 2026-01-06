package org.example.VChatTalk.command.implement_command;

import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
import org.example.VChatTalk.command.IChatCommand;
import org.example.VChatTalk.command.MessageConstants;
import org.example.VChatTalk.util.SessionRegistry;
import org.example.VChatTalk.util.SystemResponseSender;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Component
public class ExitCommand implements IChatCommand {
    private final SessionRegistry sessionRegistry;
    private final SystemResponseSender responder;

    public ExitCommand(SessionRegistry sessionRegistry, SystemResponseSender responder) {
        this.sessionRegistry = sessionRegistry;
        this.responder = responder;
    }

    @Override
    public CommandType getType() {
        return CommandType.EXIT;
    }

    @Override
    public void execute(WebSocketSession session, CommandResult result) throws IOException {
        String username = sessionRegistry.getUsername(session.getId());

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
