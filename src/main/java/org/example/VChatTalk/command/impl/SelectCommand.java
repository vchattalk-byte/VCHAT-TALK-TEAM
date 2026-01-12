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
public class SelectCommand implements IChatCommand {
    private final SystemResponseSender responder;
    private final UserRegistry userRegistry;
    private final PrivateChatRegistry privateChatRegistry;


    public SelectCommand(UserRegistry userRegistry, SystemResponseSender responder, PrivateChatRegistry privateChatRegistry) {
        this.userRegistry = userRegistry;
        this.responder = responder;
        this.privateChatRegistry = privateChatRegistry;
    }

    @Override
    public CommandType getType() {
        return CommandType.SELECT;
    }

    @Override
    public void execute(WebSocketSession session, CommandResult result) throws IOException {
        // Auth Check
        if (!userRegistry.isUserRegistered(session.getId())) {
            responder.sendError(session, MessageConstants.ERR_NOT_LOGGED_IN);
            return;
        }

        // Arg Check
        String targetUser = result.getArgument();
        if (targetUser == null || targetUser.isBlank()) {
            responder.sendError(session, String.format(MessageConstants.ERR_MISSING_ARG_USER, "/select"));
            return;
        }

        // Username cannot contain spaces
        if (targetUser.trim().contains(" ")) {
            responder.sendError(session, MessageConstants.ERR_USERNAME_CONTAIN_SPACE);
            return;
        }

        // Self Chat Check
        String currentUser = userRegistry.getUsername(session.getId());
        if (targetUser.equals(currentUser)) {
            responder.sendError(session, MessageConstants.ERR_SELF_CHAT);
            return;
        }

        // Online Check & Switch
        if (userRegistry.isUserOnline(targetUser)) {
            privateChatRegistry.setTarget(session.getId(), targetUser);
            responder.sendSystem(session, String.format(MessageConstants.MSG_PRIVATE_CHAT_START, targetUser));
        } else {
            responder.sendError(session, String.format(MessageConstants.ERR_USER_OFFLINE, targetUser));
        }
    }
}
