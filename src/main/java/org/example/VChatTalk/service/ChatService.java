    package org.example.VChatTalk.service;

    import lombok.RequiredArgsConstructor;
    import lombok.extern.slf4j.Slf4j;
    import org.example.VChatTalk.model.ChatContext;
    import org.example.VChatTalk.model.MessageDTO;
    import org.example.VChatTalk.model.MessageType;
    import org.example.VChatTalk.model.UserSession;
    import org.example.VChatTalk.util.AnsiColor;
    import org.example.VChatTalk.util.PrivateChatRegistry;
    import org.example.VChatTalk.util.SessionRegistry;
    import org.example.VChatTalk.util.UserRegistry;
    import org.springframework.stereotype.Service;
    import org.springframework.web.socket.WebSocketSession;
    import org.example.VChatTalk.util.RoomRegistry;


    import java.io.IOException;
    import java.time.Instant;

    /**
     * Core chat business logic service
     * Handles user join/leave, message processing, and routing
     */
    @Slf4j
    @Service
    @RequiredArgsConstructor
    public class ChatService {

        private final SessionRegistry sessionRegistry;
        private final UserRegistry userRegistry;
        private final PrivateChatRegistry privateChatRegistry;
        private final BroadcastService broadcastService;
        private final RoomRegistry roomRegistry;

        // ========== MESSAGE ROUTING ==========
        private void recoverFromBrokenPrivateContext(WebSocketSession session, String sessionId)
                throws IOException {

            log.warn("Broken PRIVATE context for session {}. Resetting to GLOBAL.", sessionId);

            privateChatRegistry.removeTarget(sessionId);
            userRegistry.updateContext(sessionId, ChatContext.GLOBAL);

            broadcastService.sendToSession(
                    session,
                    systemMessage(AnsiColor.RED +
                            "Private chat target lost. Returned to global chat." +
                            AnsiColor.RESET)
            );
        }
        /**
         * Route message to either global chat or private chat based on user's target
         */
        public void routeMessage(WebSocketSession senderSession, MessageDTO dto) throws IOException {
            String sessionId = senderSession.getId();

            // Auth Check (Get Logical Session)
            if (!userRegistry.isUserRegistered(sessionId)) {
                throw new IllegalStateException("Please login before chatting.");
            }

            // Prepare Base Message
            MessageDTO message = handleMessage(sessionId, dto);

            UserSession sender = userRegistry.getSession(sessionId);
            if (sender == null) {
                log.debug("route failed: sender is null");
                return;
            }

            // Route based on Context
            switch (sender.getContext()) {
                case PRIVATE -> {
                    String targetUser = privateChatRegistry.getTarget(sessionId);
                    if (targetUser != null) {
                        handlePrivateMessage(senderSession, message, targetUser);
                    } else {
                        recoverFromBrokenPrivateContext(senderSession, sessionId);
                    }
                }
                case ROOM -> {
                    String roomId = roomRegistry.getRoomOfSession(sessionId).orElseThrow();
                    handleRoomMessage(sender, message, roomId);
                }
                case GLOBAL -> broadcastService.broadcast(message, senderSession);
            }
        }

        // ========== JOIN/LEAVE HANDLERS ==========

        /**
         * Handle user join request
         * Validates username and registers user
         */
        public MessageDTO handleJoin(String sessionId, MessageDTO dto) {
            if (userRegistry.isUserRegistered(sessionId)) {
                throw new IllegalStateException("You have already joined the chat.");
            }

            if (dto.getContent() != null && !dto.getContent().isBlank()) {
                throw new IllegalArgumentException("JOIN message must not contain content.");
            }

            String name = sanitize(dto.getSender());
            if (name.isBlank()) {
                throw new IllegalArgumentException("Username is required. Please login with a username.");
            }

            if (name.length() > 20) {
                throw new IllegalArgumentException("Username is too long (max 20 characters).");
            }

            boolean success = userRegistry.tryRegisterUser(sessionId, name);
            if (!success) {
                throw new IllegalArgumentException("Username '" + name + "' is already taken. Please choose another.");
            } else {
                userRegistry.updateContext(sessionId, ChatContext.GLOBAL);
            }

            int count = userRegistry.countOnlineUsers();
            return systemMessage(name + " has joined the chat. (Total: " + count + ")");
        }

        /**
         * Handle user leave/disconnect
         * Cleans up user registration and private chat target
         */
        public MessageDTO handleLeave(String sessionId) {
            if (!userRegistry.isUserRegistered(sessionId)) {
                return null;
            }

            String username = userRegistry.getUsername(sessionId);

            int count = Math.max(0, userRegistry.countOnlineUsers() - 1);
            return systemMessage(username + " has left the chat. (Total: " + count + ")");
        }

        // ========== MESSAGE PROCESSING ==========

        /**
         * Process and validate regular chat message
         * Sets sender username and timestamp
         */
        public MessageDTO handleMessage(String sessionId, MessageDTO dto) {
            if (!userRegistry.isUserRegistered(sessionId)) {
                throw new IllegalStateException("Please login before chatting.");
            }

            if (dto.getContent() == null || dto.getContent().isBlank()) {
                throw new IllegalArgumentException("Empty message ignored.");
            }

            dto.setSender(userRegistry.getUsername(sessionId));
            dto.setType(MessageType.MESSAGE);
            dto.setTimestamp(Instant.now());
            return dto;
        }

        // ========== PRIVATE METHODS ==========

        /**
         * Handle private message routing
         * Validates target user and sends message to both sender and receiver
         */
        private void handlePrivateMessage(WebSocketSession sender, MessageDTO message, String targetUsername)
                throws IOException {

            // Find target session using UserRegistry
            String targetSessionId = userRegistry.getSessionId(targetUsername);
            if (targetSessionId == null) {
                sendUserOfflineMessage(sender, targetUsername);
                return;
            }

            WebSocketSession targetSession = sessionRegistry.findSessionById(targetSessionId);
            if (targetSession == null || !targetSession.isOpen()) {
                sendUserOfflineMessage(sender, targetUsername);
                return;
            }

            // Send PM to target with [PM] prefix
            MessageDTO toTarget = MessageDTO.builder()
                    .type(message.getType())
                    .sender(message.getSender())
                    .timestamp(message.getTimestamp())
                    .content(AnsiColor.GREEN + "[PM] " + message.getContent() + AnsiColor.RESET)
                    .build();
            broadcastService.sendToSession(targetSession, toTarget);

            // Echo to sender with [You → target] prefix
            MessageDTO selfEcho = MessageDTO.builder()
                    .type(message.getType())
                    .sender(message.getSender())
                    .timestamp(message.getTimestamp())
                    .content(AnsiColor.CYAN + "[You → " + targetUsername + "] " +
                            message.getContent() + AnsiColor.RESET)
                    .build();
            broadcastService.sendToSession(sender, selfEcho);

            log.info("[PRIVATE_MSG] {} → {}: {}", message.getSender(), targetUsername, message.getContent());
        }

        /**
         * Handles broadcasting a message to a specific room.
         */
        private void handleRoomMessage(UserSession sender, MessageDTO message, String roomId) throws IOException {
            var members = roomRegistry.getRoomMembers(roomId);
            if (members.isEmpty()) {
                log.warn("Room {} has no members", roomId);
                return;
            }

            broadcastService.broadcastToRoom(message, members, sender.getSessionId());
        }

        /**
         * Send "user offline" notification and remove target
         */
        private void sendUserOfflineMessage(WebSocketSession session, String targetUsername) throws IOException {
            MessageDTO offlineMsg = systemMessage(
                    AnsiColor.RED + "User '" + targetUsername + "' is offline. " +
                            "Returning to global chat." + AnsiColor.RESET
            );
            broadcastService.sendToSession(session, offlineMsg);
            privateChatRegistry.removeTarget(session.getId());
        }

        /**
         * Create system message DTO
         */
        private MessageDTO systemMessage(String content) {
            return MessageDTO.builder()
                    .type(MessageType.SYSTEM)
                    .sender("System")
                    .content(content)
                    .timestamp(Instant.now())
                    .build();
        }

        /**
         * Sanitize username input - remove special characters
         */
        private String sanitize(String input) {
            if (input == null) return "";
            return input.replaceAll("[^a-zA-Z0-9_\\s-]", "").trim();
        }
    }
