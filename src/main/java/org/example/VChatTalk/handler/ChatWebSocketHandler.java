package org.example.VChatTalk.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.VChatTalk.model.MessageDTO;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {


    private static final Set<WebSocketSession> sessions =
            Collections.synchronizedSet(new HashSet<>());

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("[CONNECT] New connection established. Session ID: {}. Total sessions: {}",
                session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.info("[RECEIVE] Received message from [{}]: {}", session.getId(), payload);

        MessageDTO dto;

        try {

            dto = objectMapper.readValue(payload, MessageDTO.class);
            log.info("[PARSE] Parsed message DTO: {}", dto);
        } catch (Exception e) {

            log.warn("[WARN] Invalid JSON from [{}], fallback to plain text mode.", session.getId());
            dto = new MessageDTO();
            dto.setSender("Anonymous");
            dto.setContent(payload);
        }

        dto.setTimestamp(Instant.now());

        broadcast(dto);
    }


    private void broadcast(MessageDTO dto) {
        try {
            String json = objectMapper.writeValueAsString(dto);
            synchronized (sessions) {
                for (WebSocketSession s : sessions) {
                    if (s.isOpen()) {
                        s.sendMessage(new TextMessage(json));
                    }
                }
            }
            log.info("[BROADCAST] Sent message from [{}] to all clients: {}",
                    dto.getSender(), dto.getContent());
        } catch (IOException e) {
            log.error("[ERROR] Broadcasting message failed: {}", e.getMessage(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        log.info("[DISCONNECT] Session disconnected: [{}] with status {}. Total sessions: {}",
                session.getId(), status.getCode(), sessions.size());
    }
}
