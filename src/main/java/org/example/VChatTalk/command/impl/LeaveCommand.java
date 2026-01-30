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
public class LeaveCommand implements IChatCommand {

    private final UserRegistry userRegistry;
    private final SystemResponseSender responder;
    private final PrivateChatRegistry privateChatRegistry;
    private final RoomRegistry roomRegistry;

    @Override
    public CommandType getType() {
        return CommandType.LEAVE;
    }

    @Override
    public void execute(WebSocketSession session, CommandResult result) throws IOException {
        String sessionId = session.getId();
        UserSession userSession = userRegistry.getSession(sessionId);

        // 1️⃣ Auth check
        if (userSession == null ||
                MessageConstants.USER_ANONYMOUS.equals(userSession.getUsername())) {
            responder.sendError(session, MessageConstants.ERR_NOT_LOGGED_IN);
            return;
        }

        // 2️⃣ Context handling
        switch (userSession.getContext()) {

            case ROOM -> {
                roomRegistry.getRoomOfSession(sessionId).ifPresent(roomId -> roomRegistry.leaveRoom(roomId, sessionId));
                userRegistry.updateContext(sessionId, ChatContext.GLOBAL);
                responder.sendSystem(session, MessageConstants.MSG_ROOM_LEAVE);
            }

            case PRIVATE -> {
                privateChatRegistry.removeTarget(sessionId);

                userRegistry.updateContext(sessionId, ChatContext.GLOBAL);
                responder.sendSystem(session, MessageConstants.MSG_PRIVATE_CHAT_LEAVE);
            }

            case GLOBAL -> {
                responder.sendError(session, MessageConstants.ERR_ALREADY_IN_GLOBAL);
            }
        }
    }
}
