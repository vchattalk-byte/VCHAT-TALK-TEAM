package org.example.VChatTalk.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.example.VChatTalk.command.CommandParserService;
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
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public ChatWebSocketHandler(SessionRegistry sessionRegistry, ChatService chatService, CommandParserService commandParserService) {
        this.sessionRegistry = sessionRegistry;
        this.chatService = chatService;
        this.commandParserService = commandParserService;
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

                        // Send notification
                        sendSystem(followerSession, "User " + username + " has disconnected. Private chat ended.");
                    } catch (IOException e) {
                        log.error("Error sending disconnect notification: {}", e.getMessage());
                    }
                }
            }
        }

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
                    handleCommandResult(session, result);
                    return;
                }
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

    private void handleCommandResult(WebSocketSession session, CommandResult result) {
        try {
            MessageDTO dto = new MessageDTO();
            dto.setSender("System");
            dto.setTimestamp(Instant.now());

            switch (result.getType()) {

                case NONE -> {
                   // do nothing
                }
                case HELP ->{
                    String commands = "/help - Show available commands\n"
                            + "/join <username> - Join the chat with a username\n"
                            + "/list - List all online users\n"
                            + "/select <name> - Select a user for private chat (1-on-1)\n"
                            + "/exit - Disconnect from the server.\n";
                    session.sendMessage(new TextMessage("Available commands:\n" + commands));
                }

                case SELECT -> {
                    String targetUser = result.getTargetUsername();
                    String currentUser = sessionRegistry.getUsername(session.getId());

                    // Block chat with itself
                    if (targetUser.equals(currentUser)) {
                        sendError(session, "You cannot chat with yourself!");
                        break;
                    }

                    // check target is online
                    if (sessionRegistry.isUserOnline(targetUser)) {
                        sessionRegistry.setTarget(session.getId(), targetUser);

                        dto.setType(MessageType.SYSTEM);
                        dto.setContent("Switched to private chat with: " + targetUser);
                        session.sendMessage(new TextMessage(mapper.writeValueAsString(dto)));
                    } else {
                        sendError(session, "User '" + targetUser + "' is offline or does not exist.");
                    }
                }

                case LIST -> {
                    dto.setType(MessageType.SYSTEM);

                    List<String> users = new ArrayList<>();

                    for (WebSocketSession s : sessionRegistry.getAllSessions()) {
                        String username = sessionRegistry.getUsername(s.getId());
                        if (username != null) {
                            users.add(username);
                        }
                    }

                    dto.setContent(
                            users.isEmpty()
                                    ? "No users online"
                                    : "Online users: " + String.join(", ", users)
                    );
                    session.sendMessage(
                            new TextMessage(mapper.writeValueAsString(dto))
                    );
                }

                case UNKNOWN -> {
                    dto.setType(MessageType.ERROR);
                    dto.setContent(result.getError() != null ? result.getError() : "Invalid command. Type /help for assistance.");                    session.sendMessage(
                            new TextMessage(mapper.writeValueAsString(dto))
                    );
                }
            }

        } catch (IOException e) {
            log.warn("[COMMAND_HANDLE_ERROR] Session {}: {}", session.getId(), e.getMessage());
        }
    }
}