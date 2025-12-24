package org.example.VChatTalk.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Mock leniently để tránh lỗi UnnecessaryStubbingException nếu test fail sớm
        handler = new ChatWebSocketHandler(sessionRegistry, chatService, commandParserService);
        lenient().when(session.getId()).thenReturn("session-123");
    }

    // [QUAN TRỌNG] Dọn dẹp session sau mỗi test để reset Rate Limit
    @AfterEach
    void tearDown() {
        // Hàm này sẽ xóa session-123 khỏi map lastMessageTime
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
        when(sessionRegistry.isUserOnline(targetName)).thenReturn(true);

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
        assertTrue(sentMessage.contains("Switched to private chat"), "Phải chứa thông báo thành công");
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
                "Phải báo lỗi user offline");
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
        assertTrue(messageCaptor.getValue().getPayload().contains("yourself"), "Phải báo lỗi chat với chính mình");
    }
}