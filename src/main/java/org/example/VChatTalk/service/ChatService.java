package org.example.VChatTalk.service;

import lombok.extern.slf4j.Slf4j;
import org.example.VChatTalk.model.MessageDTO;
import org.example.VChatTalk.model.MessageType;
import org.example.VChatTalk.util.SessionRegistry;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.example.VChatTalk.util.AnsiColor;

import java.io.IOException;

import java.time.Instant;

@Slf4j
@Service
public class ChatService {

    private final SessionRegistry sessionRegistry;

    private final ObjectMapper mapper;

    public ChatService(SessionRegistry sessionRegistry, ObjectMapper mapper) {

        this.sessionRegistry = sessionRegistry;
        this.mapper = mapper;
    }

    private MessageDTO cloneForPrivate(MessageDTO base, String content) {
        MessageDTO dto = new MessageDTO();
        dto.setType(base.getType());
        dto.setSender(base.getSender());
        dto.setTimestamp(base.getTimestamp());
        dto.setContent(content);
        return dto;
    }

    public MessageDTO handleJoin(String sessionId, MessageDTO dto) {

        if (sessionRegistry.isUserRegistered(sessionId)) {
            throw new IllegalStateException("You have already joined the chat.");
        }

        if (dto.getContent() != null && !dto.getContent().isBlank()) {
            throw new IllegalArgumentException("JOIN message must not contain content.");
        }

        String name = sanitize(dto.getSender());
        if (name.isBlank()) {
            name = "Guest_" + sessionId.substring(0, 8);
        }

        if (name.length() > 20) {
            throw new IllegalArgumentException("Username is too long (max 20 characters).");
        }

        boolean success = sessionRegistry.tryRegisterUser(sessionId, name);
        if (!success) {
            throw new IllegalArgumentException("Username '" + name + "' is already taken. Please choose another.");
        }


        int count = sessionRegistry.getAllSessions().size();
        return systemMessage(name + " has joined the chat. (Total: " + count + ")");
    }

    public MessageDTO handleMessage(String sessionId, MessageDTO dto) {

        if (!sessionRegistry.isUserRegistered(sessionId)) {
            throw new IllegalStateException("Please send a JOIN message before chatting.");
        }

        if (dto.getContent() == null || dto.getContent().isBlank()) {
            throw new IllegalArgumentException("Empty message ignored.");
        }

        dto.setSender(sessionRegistry.getUsername(sessionId));
        dto.setType(MessageType.MESSAGE);
        dto.setTimestamp(Instant.now());
        return dto;
    }

    public MessageDTO handleLeave(String sessionId) {
        if (!sessionRegistry.isUserRegistered(sessionId)) {
            return null;
        }

        String username = sessionRegistry.getUsername(sessionId);
        sessionRegistry.removeSession(sessionId);
        int count = sessionRegistry.getAllSessions().size();
        return systemMessage(username + " has left the chat. (Total: " + count + ")");
    }

    private MessageDTO systemMessage(String content) {
        MessageDTO dto = new MessageDTO();
        dto.setType(MessageType.SYSTEM);
        dto.setSender("System");
        dto.setContent(content);
        dto.setTimestamp(Instant.now());
        return dto;
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return input.replaceAll("[^a-zA-Z0-9_\\s-]", "").trim();
    }

    public void routeMessage(WebSocketSession senderSession, MessageDTO dto) throws IOException {

        String sessionId = senderSession.getId();

        MessageDTO baseMessage = handleMessage(sessionId, dto);

        String targetUsername = sessionRegistry.getTarget(sessionId);

        if (targetUsername == null) {
            sendSystem(senderSession,
                    AnsiColor.YELLOW +
                            "Use /select <username> to start a private chat." +
                            AnsiColor.RESET
            );
            return;
        }

        WebSocketSession targetSession =
                sessionRegistry.findSessionByUsername(targetUsername);

        if (targetSession == null || !targetSession.isOpen()) {
            sendSystem(senderSession,
                    AnsiColor.RED + "User Offline" + AnsiColor.RESET
            );
            sessionRegistry.removeTarget(sessionId);
            return;
        }

        MessageDTO toTarget = cloneForPrivate(baseMessage,
                AnsiColor.GREEN + "[PM] " + baseMessage.getContent() + AnsiColor.RESET);

        targetSession.sendMessage(new TextMessage(
                mapper.writeValueAsString(toTarget)
        ));

        MessageDTO selfEcho = cloneForPrivate(baseMessage,
                AnsiColor.CYAN +
                        "[You → " + targetUsername + "] " +
                        baseMessage.getContent() +
                        AnsiColor.RESET);

        senderSession.sendMessage(new TextMessage(
                mapper.writeValueAsString(selfEcho)
        ));
    }
    private void sendSystem(WebSocketSession session, String content) throws IOException {
        MessageDTO dto = systemMessage(content);
        session.sendMessage(
                new TextMessage(
                        mapper.writeValueAsString(dto)
                )
        );
    }


}
