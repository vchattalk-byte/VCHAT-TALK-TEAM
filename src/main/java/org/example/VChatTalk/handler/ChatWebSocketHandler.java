package org.example.VChatTalk.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.VChatTalk.model.MessageDTO;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.concurrent.ConcurrentHashMap;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;


@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final ConcurrentHashMap<String, Long> lastMessageTime = new ConcurrentHashMap<>();
    private static final long LIMIT_MS = 200; //

    private static final Set<WebSocketSession> sessions =
            ConcurrentHashMap.newKeySet();

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);



    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

        long now = System.currentTimeMillis();
        Long last = lastMessageTime.get(session.getId());
        if (last != null && now - last < LIMIT_MS) {
            log.warn("[RATE_LIMIT] Session {} sending too fast", session.getId());
            return;
        }
        lastMessageTime.put(session.getId(), now);

        String payload = message.getPayload();

        log.info("[RECEIVE] Received message from [{}]: {}", session.getId(), payload);

        MessageDTO dto;

        try {

            dto = objectMapper.readValue(payload, MessageDTO.class);
            if (dto.getSender() == null || dto.getSender().isBlank()) {
                dto.setSender("Anonymous");
            }
            if (dto.getContent() == null || dto.getContent().isBlank()) {
                log.warn("[VALIDATION] Empty message from [{}], ignored", session.getId());
                return;
            }

            log.info("[PARSE] Parsed message DTO: {}", dto);
        } catch (Exception e) {
            log.warn(
                    "[PARSE_ERROR] Invalid JSON from [{}], payload: {}, error: {}",
                    session.getId(), payload, e.getMessage()
            );
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
            for (WebSocketSession s : sessions) {
                try {
                    if (s.isOpen()) {
                        s.sendMessage(new TextMessage(json));
                    } else {
                        sessions.remove(s);
                    }
                } catch (IOException e) {
                    sessions.remove(s);
                    log.warn("[CLEANUP] Removed dead session {}", s.getId());
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
