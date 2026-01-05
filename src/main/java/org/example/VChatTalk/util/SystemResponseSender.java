package org.example.VChatTalk.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.VChatTalk.model.MessageDTO;
import org.example.VChatTalk.model.MessageType;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;

@Component
public class SystemResponseSender {
    private final ObjectMapper mapper;

    public SystemResponseSender(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void sendSystem(WebSocketSession session, String content) throws IOException {
        send(session, MessageType.SYSTEM, content);
    }

    public void sendError(WebSocketSession session, String content) throws IOException {
        send(session, MessageType.ERROR, content);
    }

    private void send(WebSocketSession session, MessageType type, String content) throws IOException {
        if (session == null || !session.isOpen()) {
            return;
        }

        MessageDTO dto = MessageDTO.builder()
                .type(type)
                .sender("System")
                .content(content)
                .timestamp(Instant.now())
                .build();

        session.sendMessage(new TextMessage(mapper.writeValueAsString(dto)));
    }
}
