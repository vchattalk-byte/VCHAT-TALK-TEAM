package org.example.VChatTalk.handler;

<<<<<<< HEAD
import org.example.VChatTalk.Service.SessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
=======
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.VChatTalk.model.MessageDTO;

>>>>>>> origin/feature/Implement-Text-Message-Handling
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
<<<<<<< HEAD
    private final SessionRegistry sessionRegistry;
    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);
=======
    private static final ConcurrentHashMap<String, Long> lastMessageTime = new ConcurrentHashMap<>();
    private static final long LIMIT_MS = 200; //

    private static final Set<WebSocketSession> sessions =
            ConcurrentHashMap.newKeySet();

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);


>>>>>>> origin/feature/Implement-Text-Message-Handling

    public ChatWebSocketHandler(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
<<<<<<< HEAD
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessionRegistry.addSession(session);
        logger.info("New connection established. Session ID: {}", session.getId());
        session.sendMessage(new TextMessage("Welcome! You are connected to the chat server."));
        sessionRegistry.logActiveSessions();

=======
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
>>>>>>> origin/feature/Implement-Text-Message-Handling
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
<<<<<<< HEAD
        sessionRegistry.removeSession(session.getId());
        logger.info("Session disconnected: [{}] with status {}", session.getId(), status.getCode());
        sessionRegistry.logActiveSessions();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        logger.info("Received message from [{}]: {}", session.getId(), message.getPayload());
        session.sendMessage(new TextMessage("Echo: " + message.getPayload()));
=======
        sessions.remove(session);
        log.info("[DISCONNECT] Session disconnected: [{}] with status {}. Total sessions: {}",
                session.getId(), status.getCode(), sessions.size());
>>>>>>> origin/feature/Implement-Text-Message-Handling
    }
}
