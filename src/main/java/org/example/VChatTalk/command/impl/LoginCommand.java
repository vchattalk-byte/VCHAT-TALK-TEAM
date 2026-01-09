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

@Component
public class LoginCommand implements IChatCommand {
    private final UserRegistry userRegistry;;
    private final SystemResponseSender responder;

    public LoginCommand(UserRegistry userRegistry, SystemResponseSender responder) {
        this.userRegistry = userRegistry;
        this.responder = responder;
    }

    @Override
    public CommandType getType() {
        return CommandType.LOGIN;
    }

    @Override
    public void execute(WebSocketSession session, CommandResult result) throws IOException {
        // 1. Check if already logged in
        if (userRegistry.isUserRegistered(session.getId())) {
            String currentName = userRegistry.getUsername(session.getId());
            responder.sendError(session, String.format(MessageConstants.ERR_ALREADY_LOGGED_IN, currentName));
            return;
        }

        // 2. Validate Input
        String newUsername = result.getArgument();
        if (newUsername == null || newUsername.isBlank()) {
            responder.sendError(session, String.format(MessageConstants.ERR_MISSING_ARG_USER, "/login"));
            return;
        }

        // 3. Sanitize (Regex validation)
        if (!newUsername.matches("^[a-zA-Z0-9_-]{3,20}$")) {
            responder.sendError(session, MessageConstants.ERR_INVALID_USERNAME);
            return;
        }

        // 4. Register
        boolean success = userRegistry.tryRegisterUser(session.getId(), newUsername);

        if (success) {
            responder.sendSystem(session, String.format(MessageConstants.MSG_LOGIN_SUCCESS, newUsername));
        } else {
            responder.sendError(session, String.format(MessageConstants.ERR_USERNAME_TAKEN, newUsername));
        }
    }
}
