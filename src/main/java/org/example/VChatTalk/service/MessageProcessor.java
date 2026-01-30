package org.example.VChatTalk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.VChatTalk.command.CommandExecutor;
import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
import org.example.VChatTalk.command.MessageConstants;
import org.example.VChatTalk.command.service.CommandParserService;
import org.example.VChatTalk.model.MessageDTO;
import org.example.VChatTalk.model.MessageType;
import org.example.VChatTalk.util.SystemResponseSender;
import org.example.VChatTalk.util.UserRegistry;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

/**
 * Processes incoming WebSocket messages
 * Handles message parsing, validation, and routing to appropriate handlers
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageProcessor {

    private final ObjectMapper mapper;
    private final UserRegistry userRegistry;
    private final ChatService chatService;
    private final CommandParserService commandParserService;
    private final CommandExecutor commandExecutor;
    private final SystemResponseSender systemResponseSender;
    private final BroadcastService broadcastService;

    /**
     * Process incoming WebSocket text message
     * @param session WebSocket session
     * @param payload Raw message payload
     * @return true if message was handled successfully
     */
    public boolean processMessage(WebSocketSession session, String payload) {
        String sessionId = session.getId();
        log.info("[RECEIVE] {} -> {}", sessionId, payload);

        try {
            MessageDTO dto = mapper.readValue(payload, MessageDTO.class);

            if (dto.getType() == MessageType.JOIN) {
                return handleJoinMessage(session, sessionId, dto);
            }
            if (dto.getType() == MessageType.MESSAGE && isCommand(dto.getContent())) {
                return handleCommandMessage(session, dto);
            }
            if (dto.getType() == MessageType.MESSAGE) {
                return handleChatMessage(session, sessionId, dto);
            }
            return true;

        } catch (IllegalArgumentException | IllegalStateException e) {
            // FIX: Don't throw RuntimeException, just log and notify user
            sendErrorSafe(session, e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("[PARSE_ERROR] From [{}]: {}", sessionId, e.getMessage());
            sendErrorSafe(session, MessageConstants.ERR_INVALID_JSON);
            return false;
        }
    }

    /**
     * Handle JOIN message - user registration
     */
    private boolean handleJoinMessage(WebSocketSession session, String sessionId, MessageDTO dto) {
        try {
            MessageDTO systemMessage = chatService.handleJoin(sessionId, dto);
            broadcastService.broadcast(systemMessage, (WebSocketSession) null);
            systemResponseSender.sendSystem(session, MessageConstants.MSG_JOINED_SUCCESS);
            return true;
        } catch (IOException e) {
            log.error("Failed to send JOIN response", e);
            return false;
        }
    }

    /**
     * Handle COMMAND message (e.g., /select, /leave, /list)
     */
    private boolean handleCommandMessage(WebSocketSession session, MessageDTO dto) throws IOException {
        CommandResult result = commandParserService.parse(dto.getContent());

        if (result.getType() == CommandType.NONE) return false;

        boolean isPublicCommand = result.getType() == CommandType.LOGIN || result.getType() == CommandType.HELP;

        if (!isPublicCommand && !userRegistry.isUserRegistered(session.getId())) {
            log.warn("Anonymous user attempted protected command: {}", dto.getContent());
            // FIX: Use standardized sender instead of raw session.sendMessage
            sendErrorSafe(session, MessageConstants.ERR_SEND_MUST_LOGIN);
            return false;
        }

        commandExecutor.execute(session, result);
        return true;
    }

    /**
     * Handle regular CHAT message
     */
    private boolean handleChatMessage(WebSocketSession session, String sessionId, MessageDTO dto) {
        if (!userRegistry.isUserRegistered(sessionId)) {
            log.warn("Anonymous user attempted to send message: {}", dto.getContent());
            sendErrorSafe(session, MessageConstants.ERR_SEND_MUST_LOGIN);
            return false;
        }

        try {
            chatService.routeMessage(sessionId, dto);
            return true;
        } catch (IOException e) {
            log.error("Failed to route message", e);
            return false;
        }
    }

    // Helper to safely send errors without polluting logic with try-catch blocks
    private void sendErrorSafe(WebSocketSession session, String message) {
        try {
            systemResponseSender.sendError(session, message);
        } catch (IOException e) {
            log.debug("Failed to send error message to session {}: {}", session.getId(), e.getMessage());
        }
    }

    /**
     * Check if message content is a command
     */
    private boolean isCommand(String content) {
        return content != null && content.startsWith("/");
    }
}