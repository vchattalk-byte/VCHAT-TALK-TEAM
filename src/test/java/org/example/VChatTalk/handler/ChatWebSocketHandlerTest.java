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

    @Test
    void testDisconnect_ShouldNotifyFollowersAndClearTarget() throws Exception {
        // --- GIVEN (Chuẩn bị dữ liệu) ---

        // 1. Giả lập người thoát (Alice)
        String leaverSessionId = "session-Alice";
        String leaverName = "Alice";
        WebSocketSession leaverSession = mock(WebSocketSession.class);
        when(leaverSession.getId()).thenReturn(leaverSessionId);

        // 2. Giả lập người đang chat với Alice (Bob - Follower)
        String followerSessionId = "session-Bob";
        WebSocketSession followerSession = mock(WebSocketSession.class);
        when(followerSession.isOpen()).thenReturn(true); // Bob đang online

        // --- MOCKING BEHAVIOR (Giả lập hành vi Registry) ---

        // Khi hỏi tên của session thoát -> Trả về Alice
        when(sessionRegistry.getUsername(leaverSessionId)).thenReturn(leaverName);

        // Khi hỏi "Ai đang target Alice?" -> Trả về list chứa Bob
        // (Lưu ý: Dùng List.of hoặc Collections.singletonList)
        when(sessionRegistry.getSessionsTargeting(leaverName)).thenReturn(java.util.List.of(followerSessionId));

        // Khi Handler tìm session object của Bob -> Trả về mock followerSession
        when(sessionRegistry.findSessionById(followerSessionId)).thenReturn(followerSession);

        // --- WHEN (Thực hiện hành động) ---
        // Gọi hàm ngắt kết nối cho Alice
        handler.afterConnectionClosed(leaverSession, CloseStatus.NORMAL);

        // --- THEN (Kiểm tra kết quả) ---

        // 1. Verify: Phải xóa target của Bob đi (để Bob không chat với "ma" nữa)
        verify(sessionRegistry).removeTarget(followerSessionId);

        // 2. Verify: Phải gửi tin nhắn thông báo cho Bob
        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(followerSession).sendMessage(messageCaptor.capture());

        String sentMessage = messageCaptor.getValue().getPayload();
        // Kiểm tra nội dung tin nhắn (Tiếng Anh như đã sửa)
        assertTrue(sentMessage.contains("Private chat ended"), "Bob phải nhận được thông báo kết thúc chat riêng");
        assertTrue(sentMessage.contains(leaverName), "Thông báo phải chứa tên người vừa thoát");
    }

    @Test
    void testSelect_UnregisteredUser_ShouldSendError() throws Exception {
        // GIVEN
        String command = "/select Alice";

        // [FIX]: Thêm lenient() để tránh lỗi UnnecessaryStubbing
        // (Phòng trường hợp code check user trước khi parse hoặc ngược lại)
        lenient().when(sessionRegistry.isUserRegistered("session-123")).thenReturn(false);

        // [FIX]: Thêm lenient() cho cả dòng parse này luôn cho chắc
        lenient().when(commandParserService.parse(command)).thenReturn(new CommandResult(CommandType.SELECT, "Alice", null));

        // WHEN
        MessageDTO dto = new MessageDTO();
        dto.setType(MessageType.MESSAGE);
        dto.setContent(command);

        handler.handleTextMessage(session, new TextMessage(mapper.writeValueAsString(dto)));

        // THEN
        // (Các verify bên dưới giữ nguyên)
        verify(sessionRegistry, never()).setTarget(anyString(), anyString());

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(messageCaptor.capture());

        String sentMessage = messageCaptor.getValue().getPayload();
        assertTrue(sentMessage.contains("must join"), "Phải yêu cầu user join trước");
    }
}