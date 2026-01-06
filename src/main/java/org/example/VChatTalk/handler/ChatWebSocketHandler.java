package org.example.VChatTalk.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.VChatTalk.command.CommandExecutor;
import org.example.VChatTalk.command.service.CommandParserService;
import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final SessionRegistry sessionRegistry;
    private final CommandParserService commandParserService;
    private static final long RATE_LIMIT_MS = 200;

    private static final ConcurrentHashMap<String, Long> lastMessageTime = new ConcurrentHashMap<>();
    private static final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ChatService chatService;
    private final ObjectMapper mapper;
    private final CommandExecutor commandExecutor;

    public ChatWebSocketHandler(SessionRegistry sessionRegistry, ChatService chatService, CommandParserService commandParserService, ObjectMapper mapper, CommandExecutor commandExecutor) {
        this.sessionRegistry = sessionRegistry;
        this.chatService = chatService;
        this.commandParserService = commandParserService;
        this.mapper = mapper;
        this.commandExecutor = commandExecutor;
    }


    /**
     * ========== CONNECTION HANDLING ==========
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessionRegistry.addSession(session);
        sessions.add(session);

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
        String username = sessionRegistry.getUsername(sessionId);

        if (!"Anonymous".equals(username)) {
            List<String> followers = sessionRegistry.getSessionsTargeting(username);

            for (String followerSessionId : followers) {
                WebSocketSession followerSession = sessionRegistry.findSessionById(followerSessionId);
                // Check if online
                if (followerSession != null && followerSession.isOpen()) {
                    try {
                        // Remove target and reset to global or null
                        sessionRegistry.removeTarget(followerSessionId);

                        sendSystem(followerSession, "User " + username + " has disconnected. Private chat ended.");
                    } catch (IOException e) {
                        log.error("Error sending disconnect notification: {}", e.getMessage());
                    }
                }
            }
        }

        sessionRegistry.removeTarget(sessionId);

        MessageDTO leaveMessage = chatService.handleLeave(sessionId);

        sessions.remove(session);
        lastMessageTime.remove(sessionId);

        if (leaveMessage != null) {
            broadcast(leaveMessage, null);
        }

        log.info("[DISCONNECT] {} - {}", sessionId, status.getCode());
        sessionRegistry.countSessions();
    }

    /**
     * ========== MESSAGE HANDLING ==========
     */
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

            if (dto.getType() == MessageType.MESSAGE && dto.getContent() != null && dto.getContent().startsWith("/")){
                if (!sessionRegistry.isUserRegistered(session.getId())) {
                    log.warn("Anonymous user attempted to execute a command: {}", dto.getContent());
                    // Optionally, send a message back to the user saying they must register first
                    session.sendMessage(new TextMessage("You must join the chat before executing commands."));
                    return;
                }
                CommandResult result = commandParserService.parse(dto.getContent());

                if (result.getType() != CommandType.NONE) {
                    commandExecutor.execute(session, result);
                    return;
                }
            }

            if (dto.getType() == MessageType.MESSAGE) {

                if (!sessionRegistry.isUserRegistered(sessionId)) {
                    log.warn("Anonymous user attempted to send a message: {}", dto.getContent());
                    session.sendMessage(new TextMessage("You must join the chat before sending messages."));
                    return;
                }

                chatService.routeMessage(session, dto);
            }


        } catch (IllegalArgumentException | IllegalStateException e) {
            sendError(session, e.getMessage());

        } catch (Exception e) {
            log.warn("[PARSE_ERROR] From [{}]: {}", sessionId, e.getMessage());
            sendError(session, "Invalid JSON format.");

        }

    }

    /*======== HEPLPER ==========
     */
    public void setRateLimitMs(WebSocketSession session) throws  IOException{
        long now = System.currentTimeMillis();
        Long last = lastMessageTime.get(session.getId());
        lastMessageTime.put(session.getId(), now);

        if (last != null && now - last  < RATE_LIMIT_MS) {
            sendError(session, "You are sending messages too quickly. Please slow down.");
            throw new IOException("Rate limit exceeded");
        }
        lastMessageTime.put(session.getId(), now);
    }

    /**
     * ========== BROADCAST ==========
     */
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