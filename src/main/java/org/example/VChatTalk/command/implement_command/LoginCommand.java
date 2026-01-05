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
public class LoginCommand implements IChatCommand {
    private final SessionRegistry sessionRegistry;
    private final SystemResponseSender responder;

    public LoginCommand(SessionRegistry sessionRegistry, SystemResponseSender responder) {
        this.sessionRegistry = sessionRegistry;
        this.responder = responder;
    }

    @Override
    public CommandType getType() {
        return CommandType.LOGIN;
    }

    @Override
    public void execute(WebSocketSession session, CommandResult result) throws IOException {
        // 1. Check if already logged in
        if (sessionRegistry.isUserRegistered(session.getId())) {
            String currentName = sessionRegistry.getUsername(session.getId());
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
        boolean success = sessionRegistry.tryRegisterUser(session.getId(), newUsername);

        if (success) {
            responder.sendSystem(session, String.format(MessageConstants.MSG_LOGIN_SUCCESS, newUsername));
        } else {
            responder.sendError(session, String.format(MessageConstants.ERR_USERNAME_TAKEN, newUsername));
        }
    }
}
