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
import java.util.ArrayList;
import java.util.List;

@Component
public class ListCommand implements IChatCommand {
    private final SessionRegistry sessionRegistry;
    private final SystemResponseSender responder;

    public ListCommand(SessionRegistry sessionRegistry, SystemResponseSender responder) {
        this.sessionRegistry = sessionRegistry;
        this.responder = responder;
    }

    @Override
    public CommandType getType() {
        return CommandType.LIST;
    }

    @Override
    public void execute(WebSocketSession session, CommandResult result) throws IOException {
        List<String> users = new ArrayList<>();

        for (WebSocketSession s : sessionRegistry.getAllSessions()) {
            String username = sessionRegistry.getUsername(s.getId());
            if (username != null && !username.equals(MessageConstants.USER_ANONYMOUS)) {
                users.add(username);
            }
        }

        String content = users.isEmpty()
                ? MessageConstants.MSG_NO_USERS
                : String.format(MessageConstants.MSG_ONLINE_USERS, users.size(), String.join(", ", users));

        responder.sendSystem(session, content);
    }
}
