package org.example.VChatTalk.service;

import lombok.extern.slf4j.Slf4j;
import org.example.VChatTalk.model.MessageDTO;
import org.example.VChatTalk.model.MessageType;
import org.example.VChatTalk.util.SessionRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class ChatService {

    private final SessionRegistry sessionRegistry;

    public ChatService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
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

        if (sessionRegistry.isUserOnline(name)) {
            throw new IllegalArgumentException("Username '" + name + "' is already taken. Please choose another.");
        }

        sessionRegistry.registerUser(sessionId, name);


        int count = sessionRegistry.getAllSessions().size();
        return systemMessage(name + " has join the chat. (Active: " + count + ")");
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
        return systemMessage(username + " has left the chat. (Active: " + count + ")");
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
}
