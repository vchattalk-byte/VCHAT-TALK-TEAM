package org.example.VChatTalk.command.impl;

import lombok.RequiredArgsConstructor;
import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
import org.example.VChatTalk.command.IChatCommand;
import org.example.VChatTalk.command.MessageConstants;
import org.example.VChatTalk.model.ChatContext;
import org.example.VChatTalk.model.UserSession;
import org.example.VChatTalk.util.PrivateChatRegistry;
import org.example.VChatTalk.util.RoomRegistry;
import org.example.VChatTalk.util.SystemResponseSender;
import org.example.VChatTalk.util.UserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JoinCommand implements IChatCommand {
    private final UserRegistry userRegistry;
    private final RoomRegistry roomRegistry;
    private final PrivateChatRegistry privateChatRegistry;
    private final SystemResponseSender responder;

    @Override
    public CommandType getType() {
        return CommandType.JOIN;
    }

    @Override
    public void execute(WebSocketSession session, CommandResult result) throws IOException {
        String sessionId = session.getId();
        UserSession userSession = userRegistry.getSession(sessionId);

        // Auth check
        if (userSession == null ||
                MessageConstants.USER_ANONYMOUS.equals(userSession.getUsername())) {
            responder.sendError(session, MessageConstants.ERR_NOT_LOGGED_IN);
            return;
        }

        // Validate input: /join #room
        String argument = result.getArgument();
        if (argument == null || argument.isBlank()) {
            responder.sendError(session, MessageConstants.ERR_ROOM_REQUIRED);
            return;
        }
        String roomId = argument.trim();
        if (roomId.isEmpty()) {
            responder.sendError(session, MessageConstants.ERR_ROOM_REQUIRED);
            return;
        }

        if (!roomId.startsWith("#") || roomId.length() <= 1) {
            responder.sendError(session, MessageConstants.ERR_INVALID_ROOM_FORMAT);
            return;
        }

        switch (userSession.getContext()) {
            case PRIVATE -> privateChatRegistry.removeTarget(sessionId);

            case ROOM -> roomRegistry.leaveCurrentRoom(sessionId);

            case GLOBAL -> {
                // no-op
            }
        }

        roomRegistry.joinRoom(roomId, sessionId);

        userRegistry.updateContext(sessionId, ChatContext.ROOM);

        responder.sendSystem(session,
                String.format(MessageConstants.MSG_ROOM_JOIN, roomId));

    }
}
