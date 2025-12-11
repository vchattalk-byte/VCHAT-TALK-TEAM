package org.example.VChatTalk.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("New connection established. Session ID: {}", session.getId());
        session.sendMessage(new TextMessage("Welcome! You are connected to the chat server."));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        logger.info("Session disconnected: [{}] with status {}", session.getId(), status.getCode());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        logger.info("Received message from [{}]: {}", session.getId(), message.getPayload());
        session.sendMessage(new TextMessage("Echo: " + message.getPayload()));
    }
}
