package org.example.VChatTalk.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.VChatTalk.model.MessageDTO;
import org.example.VChatTalk.model.MessageType;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;

@Component
@Slf4j
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
            log.warn("Cannot send '{}' message. Session is null or closed. SessionID: {}",
                    type, (session != null ? session.getId() : "null"));
            return;
        }

        try {
            MessageDTO dto = MessageDTO.builder()
                    .type(type)
                    .sender("System")
                    .content(content)
                    .timestamp(Instant.now())
                    .build();

            session.sendMessage(new TextMessage(mapper.writeValueAsString(dto)));
        } catch (IOException e) {
            log.error("Failed to send message to session {}", session.getId(), e);
            throw e;
        }
    }
}
