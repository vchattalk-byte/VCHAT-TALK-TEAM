package org.example.VChatTalk.command.impl;

import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
import org.example.VChatTalk.command.IChatCommand;
import org.example.VChatTalk.command.MessageConstants;
import org.example.VChatTalk.util.SystemResponseSender;
import org.example.VChatTalk.util.UserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;

@Component
public class ListCommand implements IChatCommand {
    private final UserRegistry userRegistry;
    private final SystemResponseSender responder;

    public ListCommand(UserRegistry userRegistry, SystemResponseSender responder) {
        this.userRegistry = userRegistry;
        this.responder = responder;
    }

    @Override
    public CommandType getType() {
        return CommandType.LIST;
    }

    @Override
    public void execute(WebSocketSession session, CommandResult result) throws IOException {
        if (!userRegistry.isUserRegistered(session.getId())) {
            responder.sendError(session, MessageConstants.ERR_NOT_LOGGED_IN);
            return;
        }

        Collection<String> onlineUsers = userRegistry.getAllOnlineUsers();

        if (onlineUsers.isEmpty()) {
            responder.sendSystem(session, MessageConstants.MSG_NO_USERS);
        } else {
            String userList = String.join(", ", onlineUsers);
            responder.sendSystem(session,
                    String.format(MessageConstants.MSG_ONLINE_USERS, onlineUsers.size(), userList));
        }
    }
}
