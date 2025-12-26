package org.example.VChatTalk.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.VChatTalk.command.CommandParserService;
import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
import org.example.VChatTalk.model.MessageDTO;
import org.example.VChatTalk.model.MessageType;
import org.example.VChatTalk.service.ChatService;
import org.example.VChatTalk.util.SessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketHandlerTest {

    @Mock
    private SessionRegistry sessionRegistry;
    @Mock
    private ChatService chatService;
    @Mock
    private CommandParserService commandParserService;
    @Mock
    private WebSocketSession session;

    private ChatWebSocketHandler handler;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);


    @BeforeEach
    void setUp() {
        // Mock leniently to avoid UnnecessaryStubbingException if test fails early
        handler = new ChatWebSocketHandler(sessionRegistry, chatService, commandParserService, mapper);
        lenient().when(session.getId()).thenReturn("session-123");
    }

    // [IMPORTANT] Clean up session after each test to reset Rate Limit
    @AfterEach
    void tearDown() {
        // This will remove session-123 from the lastMessageTime map
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
    }

    @Test
    void testSelect_ValidOnlineUser_ShouldSetTarget() throws Exception {
        // GIVEN
        String command = "/select Alice";
        String senderName = "Bob";
        String targetName = "Alice";

        when(sessionRegistry.isUserRegistered("session-123")).thenReturn(true);
        when(commandParserService.parse(command)).thenReturn(new CommandResult(CommandType.SELECT, targetName, null));
        when(sessionRegistry.getUsername("session-123")).thenReturn(senderName);
        when(sessionRegistry.isUserOnline(targetName)).thenReturn(true); // Updated to match renamed method

        // WHEN
        MessageDTO dto = new MessageDTO();
        dto.setType(MessageType.MESSAGE);
        dto.setContent(command);
        String payload = mapper.writeValueAsString(dto);

        handler.handleTextMessage(session, new TextMessage(payload));

        // THEN
        verify(sessionRegistry).setTarget("session-123", targetName);

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(messageCaptor.capture());

        String sentMessage = messageCaptor.getValue().getPayload();
        assertTrue(sentMessage.contains("Switched to private chat"), "Should contain success message");
    }

    @Test
    void testSelect_OfflineUser_ShouldSendError() throws Exception {
        // GIVEN
        String targetName = "GhostUser";

        when(sessionRegistry.isUserRegistered("session-123")).thenReturn(true);
        when(commandParserService.parse(anyString())).thenReturn(new CommandResult(CommandType.SELECT, targetName, null));
        when(sessionRegistry.getUsername("session-123")).thenReturn("Bob");
        when(sessionRegistry.isUserOnline(targetName)).thenReturn(false);

        // WHEN
        MessageDTO dto = new MessageDTO();
        dto.setType(MessageType.MESSAGE);
        dto.setContent("/select GhostUser");
        handler.handleTextMessage(session, new TextMessage(mapper.writeValueAsString(dto)));

        // THEN
        verify(sessionRegistry, never()).setTarget(anyString(), anyString());

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(messageCaptor.capture());
        assertTrue(messageCaptor.getValue().getPayload().contains("offline") ||
                        messageCaptor.getValue().getPayload().contains("ERROR"),
                "Should report offline user error");
    }

    @Test
    void testSelect_Self_ShouldSendError() throws Exception {
        // GIVEN
        String myName = "Bob";

        when(sessionRegistry.isUserRegistered("session-123")).thenReturn(true);
        when(commandParserService.parse(anyString())).thenReturn(new CommandResult(CommandType.SELECT, myName, null));
        when(sessionRegistry.getUsername("session-123")).thenReturn(myName);

        // WHEN
        MessageDTO dto = new MessageDTO();
        dto.setType(MessageType.MESSAGE);
        dto.setContent("/select Bob");
        handler.handleTextMessage(session, new TextMessage(mapper.writeValueAsString(dto)));

        // THEN
        verify(sessionRegistry, never()).setTarget(anyString(), anyString());

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(messageCaptor.capture());
        assertTrue(messageCaptor.getValue().getPayload().contains("yourself"), "Should report self-chat error");
    }

    @Test
    void testDisconnect_ShouldNotifyFollowersAndClearTarget() throws Exception {
        // --- GIVEN ---

        // 1. Simulate the leaver (Alice)
        String leaverSessionId = "session-Alice";
        String leaverName = "Alice";
        WebSocketSession leaverSession = mock(WebSocketSession.class);
        when(leaverSession.getId()).thenReturn(leaverSessionId);

        // 2. Simulate the follower (Bob)
        String followerSessionId = "session-Bob";
        WebSocketSession followerSession = mock(WebSocketSession.class);
        when(followerSession.isOpen()).thenReturn(true); // Bob is online

        // --- MOCKING BEHAVIOR ---

        // Return Alice when asking for leaver's username
        when(sessionRegistry.getUsername(leaverSessionId)).thenReturn(leaverName);

        // Return Bob when asking "Who is targeting Alice?"
        when(sessionRegistry.getSessionsTargeting(leaverName)).thenReturn(java.util.List.of(followerSessionId));

        // Return mock followerSession when Handler looks up Bob's session
        when(sessionRegistry.findSessionById(followerSessionId)).thenReturn(followerSession);

        // --- WHEN ---
        // Call connection closed handler for Alice
        handler.afterConnectionClosed(leaverSession, CloseStatus.NORMAL);

        // --- THEN ---

        // 1. Verify: Bob's target must be removed
        verify(sessionRegistry).removeTarget(followerSessionId);

        // 2. Verify: Notification sent to Bob
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(followerSession).sendMessage(messageCaptor.capture());

        String sentMessage = messageCaptor.getValue().getPayload();
        // Verify message content
        assertTrue(sentMessage.contains("Private chat ended"), "Bob should receive private chat ended notification");
        assertTrue(sentMessage.contains(leaverName), "Notification should contain leaver's name");
    }

    @Test
    void testSelect_UnregisteredUser_ShouldSendError() throws Exception {
        // GIVEN
        String command = "/select Alice";

        // [FIX]: Add lenient() to avoid UnnecessaryStubbingException
        // (In case logic checks user registration before parsing command)
        lenient().when(sessionRegistry.isUserRegistered("session-123")).thenReturn(false);

        // [FIX]: Add lenient() for parsing as well
        lenient().when(commandParserService.parse(command)).thenReturn(new CommandResult(CommandType.SELECT, "Alice", null));

        // WHEN
        MessageDTO dto = new MessageDTO();
        dto.setType(MessageType.MESSAGE);
        dto.setContent(command);

        handler.handleTextMessage(session, new TextMessage(mapper.writeValueAsString(dto)));

        // THEN
        // 1. Ensure setTarget is NEVER called
        verify(sessionRegistry, never()).setTarget(anyString(), anyString());

        // 2. Should send error message
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(messageCaptor.capture());

        String sentMessage = messageCaptor.getValue().getPayload();
        assertTrue(sentMessage.contains("must join"), "Should require user to join first");
    }
}