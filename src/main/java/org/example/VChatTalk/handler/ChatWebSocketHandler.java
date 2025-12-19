package org.example.VChatTalk.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.example.VChatTalk.service.ChatService;
import org.example.VChatTalk.util.SessionRegistry;
import org.example.VChatTalk.model.MessageDTO;
import org.example.VChatTalk.model.MessageType;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final SessionRegistry sessionRegistry;
    private static final long RATE_LIMIT_MS = 200;

    private static final ConcurrentHashMap<String, Long> lastMessageTime = new ConcurrentHashMap<>();
    private static final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ChatService chatService;
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public ChatWebSocketHandler(SessionRegistry sessionRegistry,
                                ChatService chatService) {
        this.sessionRegistry = sessionRegistry;
        this.chatService = chatService;
    }


    /** ========== CONNECTION HANDLING ========== */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessionRegistry.addSession(session);
        sessions.add(session);

        // Gửi welcome message
        MessageDTO welcome = new MessageDTO();
        welcome.setType(MessageType.SYSTEM);
        welcome.setSender("System");
        welcome.setContent("Welcome! You are connected to the chat server.");
        welcome.setTimestamp(Instant.now());
        mapper.writeValueAsString(welcome);
        session.sendMessage(new TextMessage(mapper.writeValueAsString(welcome)));

        log.info("[CONNECT] New session: {}", session.getId());
        sessionRegistry.countSessions();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {

        String sessionId = session.getId();


        MessageDTO leave = chatService.handleLeave(session.getId());
        if (leave != null) {
            broadcast(leave, null);
        }
        sessions.remove(session);
        lastMessageTime.remove(sessionId);

        log.info("[DISCONNECT] {} - {}", sessionId, status.getCode());
        sessionRegistry.countSessions();

    }

    /** ========== MESSAGE HANDLING ========== */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        long now = System.currentTimeMillis();
        Long last = lastMessageTime.get(session.getId());

        // rate limit check
        if (last != null && now - last < RATE_LIMIT_MS) {
            sendError(session, "You are sending messages too quickly. Please slow down.");
            return;
        }
        lastMessageTime.put(session.getId(), now);

        String payload = message.getPayload();
        log.info("[RECEIVE] {} -> {}", session.getId(), payload);

        String sessionId = session.getId();
        try {
            MessageDTO dto = mapper.readValue(payload, MessageDTO.class);
            if (dto.getType() == MessageType.JOIN) {
                MessageDTO system = chatService.handleJoin(sessionId, dto);
                broadcast(system, null);
                sendSystem(session, "You joined the chat successfully.");
                return;
            }

            MessageDTO chat = chatService.handleMessage(sessionId, dto);
            broadcast(chat, session);

        } catch (IllegalArgumentException | IllegalStateException e) {
            sendError(session, e.getMessage());

        } catch (Exception e) {
            log.warn("[PARSE_ERROR] From [{}]: {}", sessionId, e.getMessage());
            sendError(session, "Invalid JSON format.");

        }

    }

    /** ========== BROADCAST ========== */
    private void broadcast(MessageDTO dto, WebSocketSession sender) {
        try {
            String json = mapper.writeValueAsString(dto);
            Iterator<WebSocketSession> iterator = sessions.iterator();
            boolean isSystemMessage = (sender == null);

            while (iterator.hasNext()) {
                WebSocketSession s = iterator.next();
                try {
                    if (s.isOpen() && (isSystemMessage || !s.getId().equals(sender.getId()))) {
                        s.sendMessage(new TextMessage(json));
                    } else if (!s.isOpen()) {
                        cleanup(iterator, s);
                    }
                } catch (IOException e) {
                    cleanup(iterator, s);
                }
            }

            log.info("[BROADCAST] [{}] -> {} clients", dto.getSender(), sessions.size() - 1);
        } catch (IOException e) {
            log.error("[BROADCAST_ERROR] {}", e.getMessage());
        }
    }

    private void sendError(WebSocketSession session, String content) throws IOException {
        MessageDTO dto = new MessageDTO();
        dto.setType(MessageType.ERROR);
        dto.setSender("System");
        dto.setContent(content);
        dto.setTimestamp(Instant.now());
        session.sendMessage(new TextMessage(mapper.writeValueAsString(dto)));
    }

    private void sendSystem(WebSocketSession session, String content) throws IOException {
        MessageDTO dto = new MessageDTO();
        dto.setType(MessageType.SYSTEM);
        dto.setSender("System");
        dto.setContent(content);
        dto.setTimestamp(Instant.now());
        session.sendMessage(new TextMessage(mapper.writeValueAsString(dto)));
    }


    private void cleanup(Iterator<WebSocketSession> iterator, WebSocketSession s) {
        iterator.remove();
        sessionRegistry.removeSession(s.getId());
        lastMessageTime.remove(s.getId());
        log.warn("[CLEANUP] Removed dead session {}", s.getId());
    }

}
