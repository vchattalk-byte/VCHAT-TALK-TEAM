package org.example.VChatTalk.command;

import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

public interface IChatCommand {
    CommandType getType();
    void execute(WebSocketSession session, CommandResult result) throws IOException;
}
