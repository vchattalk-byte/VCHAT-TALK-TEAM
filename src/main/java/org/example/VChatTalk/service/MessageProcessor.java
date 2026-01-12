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
import org.springframework.web.socket.TextMessage;
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

            // Handle JOIN message
            if (dto.getType() == MessageType.JOIN) {
                return handleJoinMessage(session, sessionId, dto);
            }

            // Handle COMMAND message (starts with /)
            if (dto.getType() == MessageType.MESSAGE && isCommand(dto.getContent())) {
                return handleCommandMessage(session, dto);
            }

            // Handle regular CHAT message
            if (dto.getType() == MessageType.MESSAGE) {
                return handleChatMessage(session, sessionId, dto);
            }

            return true;

        } catch (IllegalArgumentException | IllegalStateException e) {
            try {
                systemResponseSender.sendError(session, e.getMessage());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            return false;

        } catch (Exception e) {
            log.warn("[PARSE_ERROR] From [{}]: {}", sessionId, e.getMessage());
            try {
                systemResponseSender.sendError(session, MessageConstants.ERR_INVALID_JSON);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            return false;
        }
    }

    /**
     * Handle JOIN message - user registration
     */
    private boolean handleJoinMessage(WebSocketSession session, String sessionId, MessageDTO dto) {
        MessageDTO systemMessage = chatService.handleJoin(sessionId, dto);
        broadcastService.broadcast(systemMessage, null);
        try {
            systemResponseSender.sendSystem(session, MessageConstants.MSG_JOINED_SUCCESS);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    /**
     * Handle COMMAND message (e.g., /select, /leave, /list)
     */
    private boolean handleCommandMessage(WebSocketSession session, MessageDTO dto) throws IOException {

        CommandResult result = commandParserService.parse(dto.getContent());

        if (result.getType() == CommandType.NONE) {
            return false;
        }

        boolean isPublicCommand =
                result.getType() == CommandType.LOGIN
                        || result.getType() == CommandType.HELP;

        if (!isPublicCommand && !userRegistry.isUserRegistered(session.getId())) {
            log.warn("Anonymous user attempted protected command: {}", dto.getContent());
            session.sendMessage(new TextMessage(MessageConstants.ERR_SEND_MUST_LOGIN));
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
            try {
                session.sendMessage(new TextMessage(MessageConstants.ERR_SEND_MUST_LOGIN));
            } catch (IOException e) {
                log.error("Failed to send error message", e);
            }
            return false;
        }

        try {
            chatService.routeMessage(session, dto);
            return true;
        } catch (IOException e) {
            log.error("Failed to route message", e);
            return false;
        }
    }

    /**
     * Check if message content is a command
     */
    private boolean isCommand(String content) {
        return content != null && content.startsWith("/");
    }
}
