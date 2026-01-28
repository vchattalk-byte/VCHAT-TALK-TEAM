package org.example.VChatTalk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.VChatTalk.model.MessageDTO;
import org.example.VChatTalk.util.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;

/**
 * Service for broadcasting messages to WebSocket sessions
 * Handles both global broadcasts and direct session messages
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BroadcastService {

    private final ObjectMapper mapper;
    private final SessionRegistry sessionRegistry;

    /**
     * Broadcast message to all connected sessions except sender
     * @param dto Message to broadcast
     * @param sender Sender session (null for system messages)
     */
    public void broadcast(MessageDTO dto, WebSocketSession sender) {
        try {
            String json = mapper.writeValueAsString(dto);
            Collection<WebSocketSession> sessions = sessionRegistry.getAllSessions();
            boolean isSystemMessage = (sender == null);

            int successCount = 0;
            for (WebSocketSession session : sessions) {
                try {
                    // Send to all (if system message) or all except sender
                    if (session.isOpen() && (isSystemMessage || !session.getId().equals(sender.getId()))) {
                        session.sendMessage(new TextMessage(json));
                        successCount++;
                    }
                } catch (IOException e) {
                    log.warn("[BROADCAST_FAILED] Session: {} - {}", session.getId(), e.getMessage());
                    // Session cleanup handled by afterConnectionClosed in handler
                }
            }

            log.info("[BROADCAST] Sender: [{}] -> {} clients", dto.getSender(), successCount);
        } catch (IOException e) {
            log.error("[BROADCAST_ERROR] Failed to serialize message: {}", e.getMessage());
        }
    }

    /**
     * Send message to specific session only
     * @param session Target session
     * @param dto Message to send
     * @throws IOException if sending fails
     */
    public void sendToSession(WebSocketSession session, MessageDTO dto) throws IOException {
        if (session != null && session.isOpen()) {
            String json = mapper.writeValueAsString(dto);
            session.sendMessage(new TextMessage(json));
        }
    }

    /**
     * Broadcast a message to all members of a room.
     * @param dto             Message to send
     * @param sessionIds      Session IDs of room members
     * @param senderSessionId Session ID of the sender (excluded from broadcast)
     */
    public void broadcastToRoom(MessageDTO dto, Collection<String> sessionIds, String senderSessionId) {
        try {
            String json = mapper.writeValueAsString(dto);
            int successCount = 0;

            for (String sessionId : sessionIds) {

                if (sessionId.equals(senderSessionId)) {
                    continue;
                }

                WebSocketSession session = sessionRegistry.findSessionById(sessionId);
                if (session == null || !session.isOpen()) {
                    continue;
                }

                try {
                    session.sendMessage(new TextMessage(json));
                    successCount++;
                } catch (IOException e) {
                    log.warn("[ROOM_BROADCAST_FAILED] sessionId={}", sessionId);
                }
            }

            log.info("[ROOM_BROADCAST] Room message from [{}] -> {} clients",
                    dto.getSender(), successCount);

        } catch (IOException e) {
            log.error("[ROOM_BROADCAST_ERROR] {}", e.getMessage());
        }
    }

    /**
     * Broadcast message to a specific set of target sessions (e.g. room members or private chat).
     * Formats message if it belongs to a room context.
     *
     * <p>
     * This method sends the message to all provided {@code sessionIds}, including the sender
     * if their session ID is present in the collection. Callers are responsible for excluding
     * the sender from {@code sessionIds} if they do not want the sender to receive the message.
     * This differs from {@link #broadcastToRoom(MessageDTO, Collection, String)} which always
     * excludes the sender based on {@code senderSessionId}.
     * </p>
     *
     * @param dto        message to send (must have non-null sender and content)
     * @param sessionIds target session IDs (may include the sender)
     */
    public void broadcastToTargets(MessageDTO dto, Collection<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            log.debug("[BROADCAST_TO_TARGETS] No sessionIds provided. Skipping broadcast.");
            return;
        }

        // Defensive null check for dto and core fields
        if (dto == null || dto.getSender() == null || dto.getContent() == null) {
            log.warn("[BROADCAST_TO_TARGETS] Invalid message: missing sender or content. Skipped.");
            return;
        }

        try {
            // Format message for room if applicable
            String formattedContent = dto.getContent();
            if (dto.getRoomId() != null && dto.getRoomId().startsWith("#")) {
                formattedContent = String.format("[%s] %s: %s",
                        dto.getRoomId(), dto.getSender(), dto.getContent());
            }

            // Prepare serialized copy
            MessageDTO formattedDto = MessageDTO.builder()
                    .type(dto.getType())
                    .sender(dto.getSender())
                    .content(formattedContent)
                    .roomId(dto.getRoomId())
                    .timestamp(dto.getTimestamp())
                    .build();

            String json = mapper.writeValueAsString(formattedDto);
            int successCount = 0;

            for (String sessionId : sessionIds) {
                WebSocketSession session = sessionRegistry.findSessionById(sessionId);

                if (session == null || !session.isOpen()) {
                    continue;
                }

                try {
                    session.sendMessage(new TextMessage(json));
                    successCount++;
                } catch (IOException e) {
                    log.warn("[BROADCAST_TO_TARGETS_FAILED] sessionId={} - {}", sessionId, e.getMessage());
                }
            }

            log.info("[BROADCAST_TO_TARGETS] Sender: [{}] -> {} sessions", dto.getSender(), successCount);
        } catch (IOException e) {
            log.error("[BROADCAST_TO_TARGETS_ERROR] {}", e.getMessage());
        }
    }

}
