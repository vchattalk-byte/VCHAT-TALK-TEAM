package org.example.VChatTalk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.VChatTalk.command.CommandExecutor;
import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
import org.example.VChatTalk.command.MessageConstants;
import org.example.VChatTalk.command.service.CommandParserService;
import org.example.VChatTalk.model.MessageDTO;
import org.example.VChatTalk.model.MessageType;
import org.example.VChatTalk.util.SystemResponseSender;
import org.example.VChatTalk.util.UserRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageProcessorTest {

    @Mock private UserRegistry userRegistry;
    @Mock private ChatService chatService;
    @Mock private CommandParserService commandParserService;
    @Mock private CommandExecutor commandExecutor;
    @Mock private SystemResponseSender systemResponseSender;
    @Mock private BroadcastService broadcastService;
    @Mock private WebSocketSession session;

    // We use a real ObjectMapper to simulate actual JSON parsing
    @Spy
    private ObjectMapper mapper = new ObjectMapper();

    @InjectMocks
    private MessageProcessor messageProcessor;

    private final String SESSION_ID = "sess-999";

    @BeforeEach
    void setUp() {
        lenient().when(session.getId()).thenReturn(SESSION_ID);
    }

    @Test
    @DisplayName("JOIN: Should call chatService.handleJoin and broadcast")
    void processMessage_JoinType() throws IOException {
        String payload = "{\"type\":\"JOIN\", \"sender\":\"Alice\"}";
        MessageDTO systemMsg = MessageDTO.builder().type(MessageType.SYSTEM).content("Joined").build();

        when(chatService.handleJoin(eq(SESSION_ID), any())).thenReturn(systemMsg);

        boolean result = messageProcessor.processMessage(session, payload);

        assertTrue(result);
        verify(chatService).handleJoin(eq(SESSION_ID), any());
        verify(broadcastService).broadcast(systemMsg, null);
        verify(systemResponseSender).sendSystem(session, MessageConstants.MSG_JOINED_SUCCESS);
    }

    @Test
    @DisplayName("COMMAND: Should route '/list' to CommandExecutor")
    void processMessage_CommandRoute() throws IOException {
        String payload = "{\"type\":\"MESSAGE\", \"content\":\"/list\"}";
        CommandResult mockResult = new CommandResult(CommandType.LIST, null, null);

        when(commandParserService.parse("/list")).thenReturn(mockResult);
        when(userRegistry.isUserRegistered(SESSION_ID)).thenReturn(true);

        boolean result = messageProcessor.processMessage(session, payload);

        assertTrue(result);
        verify(commandExecutor).execute(session, mockResult);
        verifyNoInteractions(chatService); // Should not go to chat logic
    }

    @Test
    @DisplayName("CHAT: Should route regular text to ChatService")
    void processMessage_ChatRoute() throws IOException {
        String payload = "{\"type\":\"MESSAGE\", \"content\":\"Hello world\"}";

        when(userRegistry.isUserRegistered(SESSION_ID)).thenReturn(true);

        boolean result = messageProcessor.processMessage(session, payload);

        assertTrue(result);
        verify(chatService).routeMessage(eq(session), any(MessageDTO.class));
        verifyNoInteractions(commandExecutor);
    }

    @Test
    @DisplayName("SECURITY: Should reject message if user not logged in")
    void processMessage_UnregisteredUser() throws IOException {
        String payload = "{\"type\":\"MESSAGE\", \"content\":\"I am a hacker\"}";

        when(userRegistry.isUserRegistered(SESSION_ID)).thenReturn(false);

        boolean result = messageProcessor.processMessage(session, payload);

        assertFalse(result);
        verify(systemResponseSender).sendError(session, MessageConstants.ERR_SEND_MUST_LOGIN);
        verifyNoInteractions(chatService);
    }

    @Test
    @DisplayName("ERROR: Should handle invalid JSON gracefully")
    void processMessage_InvalidJson() throws IOException {
        String payload = "not-a-json-string";

        boolean result = messageProcessor.processMessage(session, payload);

        assertFalse(result);
        verify(systemResponseSender).sendError(session, MessageConstants.ERR_INVALID_JSON);
    }
}