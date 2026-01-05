package org.example.VChatTalk.command.implement_command;

import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
import org.example.VChatTalk.command.IChatCommand;
import org.example.VChatTalk.command.MessageConstants;
import org.example.VChatTalk.util.SessionRegistry;
import org.example.VChatTalk.util.SystemResponseSender;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Component
public class LeaveCommand implements IChatCommand {
    private final SessionRegistry sessionRegistry;
    private final SystemResponseSender responder;

    public LeaveCommand(SessionRegistry sessionRegistry, SystemResponseSender responder) {
        this.sessionRegistry = sessionRegistry;
        this.responder = responder;
    }

    @Override
    public CommandType getType() {
        return CommandType.LEAVE;
    }

    @Override
    public void execute(WebSocketSession session, CommandResult result) throws IOException {
        if (!sessionRegistry.isUserRegistered(session.getId())) {
            responder.sendError(session, MessageConstants.ERR_NOT_LOGGED_IN);
            return;
        }

        String currentTarget = sessionRegistry.getTarget(session.getId());

        if (currentTarget != null) {
            sessionRegistry.removeTarget(session.getId());
            responder.sendSystem(session, MessageConstants.MSG_PRIVATE_CHAT_LEAVE);
        } else {
            responder.sendSystem(session, MessageConstants.ERR_ALREADY_IN_GLOBAL);
        }
    }
}
