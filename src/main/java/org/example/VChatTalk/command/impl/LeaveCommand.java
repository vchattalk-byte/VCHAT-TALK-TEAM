package org.example.VChatTalk.command.impl;

import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
import org.example.VChatTalk.command.IChatCommand;
import org.example.VChatTalk.command.MessageConstants;
import org.example.VChatTalk.util.PrivateChatRegistry;
import org.example.VChatTalk.util.SystemResponseSender;
import org.example.VChatTalk.util.UserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Component
public class LeaveCommand implements IChatCommand {
    private final UserRegistry userRegistry;
    private final SystemResponseSender responder;
    private final PrivateChatRegistry privateChatRegistry;

    public LeaveCommand(UserRegistry userRegistry, SystemResponseSender responder, PrivateChatRegistry privateChatRegistry) {
        this.userRegistry = userRegistry;
        this.responder = responder;
        this.privateChatRegistry = privateChatRegistry;
    }

    @Override
    public CommandType getType() {
        return CommandType.LEAVE;
    }

    @Override
    public void execute(WebSocketSession session, CommandResult result) throws IOException {
        if (!userRegistry.isUserRegistered(session.getId())) {
            responder.sendError(session, MessageConstants.ERR_NOT_LOGGED_IN);
            return;
        }

        String currentTarget = privateChatRegistry.getTarget(session.getId());

        if (currentTarget != null) {
            privateChatRegistry.removeTarget(session.getId());
            responder.sendSystem(session, MessageConstants.MSG_PRIVATE_CHAT_LEAVE);
        } else {
            responder.sendSystem(session, MessageConstants.ERR_ALREADY_IN_GLOBAL);
        }
    }
}
