package org.example.VChatTalk.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.example.VChatTalk.Service.SessionRegistry;
import org.example.VChatTalk.model.MessageDTO;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final SessionRegistry sessionRegistry;

    private static final ConcurrentHashMap<String, Long> lastMessageTime = new ConcurrentHashMap<>();
    private static final long LIMIT_MS = 200;

    private static final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public ChatWebSocketHandler(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessionRegistry.addSession(session);
        sessions.add(session);
        log.info("New connection established. Session ID: {}", session.getId());
        session.sendMessage(new TextMessage("Welcome! You are connected to the chat server."));
        sessionRegistry.countSessions();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionRegistry.removeSession(session.getId());
        sessions.remove(session);
        lastMessageTime.remove(session.getId());
        log.info("Session disconnected: [{}] with status {}", session.getId(), status.getCode());
        sessionRegistry.countSessions();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        long now = System.currentTimeMillis();
        Long last = lastMessageTime.get(session.getId());
        if (last != null && now - last < LIMIT_MS) {
            log.warn("[RATE_LIMIT] Session {} sending too fast", session.getId());
            try {
                session.sendMessage(new TextMessage("Error: You are sending messages too quickly. Please slow down."));
            } catch (IOException e) {
                log.warn("[RATE_LIMIT_NOTIFY_FAILED] Failed to notify session {}: {}", session.getId(), e.getMessage());
            }
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
                session.sendMessage(new TextMessage("Error: empty messages are not allowed."));
                return;
            }
        } catch (Exception e) {
            log.warn("[PARSE_ERROR] Invalid JSON from [{}], payload: {}, error: {}",
                    session.getId(), payload, e.getMessage());
            dto = new MessageDTO();
            dto.setSender("Anonymous");
            dto.setContent(payload);
        }

        dto.setTimestamp(Instant.now());
        broadcast(dto, session);
    }

    private void broadcast(MessageDTO dto, WebSocketSession sender) {
        try {
            String json = objectMapper.writeValueAsString(dto);
            List<WebSocketSession> toRemove = new ArrayList<>();

            for (WebSocketSession s : sessions) {
                try {
                    if (s.isOpen()) {
                        // Skip echo back to sender (avoid duplicate message)
                        if (!s.getId().equals(sender.getId())) {
                            s.sendMessage(new TextMessage(json));
                        }
                    } else {
                        toRemove.add(s);
                    }
                } catch (IOException e) {
                    toRemove.add(s);
                    log.warn("[CLEANUP] Error sending to session {}, removed: {}", s.getId(), e.getMessage());
                }
            }

            for (WebSocketSession s : toRemove) {
                sessions.remove(s);
                log.warn("[CLEANUP] Removed dead session {}", s.getId());
            }

            log.info("[BROADCAST] Sent message from [{}] to {} active clients",
                    dto.getSender(), sessions.size() - 1);
        } catch (IOException e) {
            log.error("[ERROR] Broadcasting message failed: {}", e.getMessage(), e);
        }
    }
}
