// New file: org/example/VChatTalk/service/WebSocketChatSession.java
package org.example.VChatTalk.service;

import lombok.RequiredArgsConstructor;
import org.example.VChatTalk.model.ChatSession;
import org.example.VChatTalk.model.MessageDTO;
import org.example.VChatTalk.util.UserRegistry;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Adapter that wraps WebSocketSession with our ChatSession interface
 */
@RequiredArgsConstructor
public class WebSocketChatSession implements ChatSession {

    private final WebSocketSession webSocketSession;
    private final UserRegistry userRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public String getId() {
        return webSocketSession.getId();
    }

    @Override
    public String getUsername() throws IOException {
        // Extract from session attributes or UserRegistry
        String sessionId = getId();
        String username = userRegistry.getUsername(sessionId);
        if (username == null) {
            throw new IOException("User not registered for session: " + sessionId);
        }
        return username;
    }

    @Override
    public boolean isOpen() {
        return webSocketSession.isOpen();
    }

    @Override
    public void sendMessage(MessageDTO message) throws IOException {
        String json = objectMapper.writeValueAsString(message);
        webSocketSession.sendMessage(new TextMessage(json));
    }
}