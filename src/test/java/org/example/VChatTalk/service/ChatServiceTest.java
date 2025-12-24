package org.example.VChatTalk.service;

import org.example.VChatTalk.model.MessageDTO;
import org.example.VChatTalk.util.SessionRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private SessionRegistry sessionRegistry;

    @InjectMocks
    private ChatService chatService;

    // TEST CASE 1: Đăng ký trùng tên (Race Condition Logic) -> Phải báo lỗi
    @Test
    void testHandleJoin_DuplicateUsername_ShouldThrowException() {
        // GIVEN
        String sessionId = "session-new-user";
        String duplicateName = "Alice";

        MessageDTO joinDto = new MessageDTO();
        joinDto.setSender(duplicateName);

        // 1. Giả lập user chưa join (hợp lệ ở bước check đầu)
        when(sessionRegistry.isUserRegistered(sessionId)).thenReturn(false);

        // 2. [QUAN TRỌNG] Giả lập tryRegisterUser trả về FALSE (tức là tên đã bị trùng)
        when(sessionRegistry.tryRegisterUser(sessionId, duplicateName)).thenReturn(false);

        // WHEN & THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            chatService.handleJoin(sessionId, joinDto);
        });

        assertEquals("Username 'Alice' is already taken. Please choose another.", exception.getMessage());
    }

    // TEST CASE 2: Đăng ký thành công (Happy Path)
    @Test
    void testHandleJoin_ValidUsername_ShouldSuccess() {
        // GIVEN
        String sessionId = "session-123";
        String newName = "Bob";
        MessageDTO joinDto = new MessageDTO();
        joinDto.setSender(newName);

        when(sessionRegistry.isUserRegistered(sessionId)).thenReturn(false);
        when(sessionRegistry.tryRegisterUser(sessionId, newName)).thenReturn(true);

        // [FIX CHÍNH XÁC TẠI ĐÂY]
        // Vì hàm getAllSessions() trả về Collection, ta dùng Collections.singletonList
        // để tạo ra một Collection có size = 1 (chứa 1 session giả).
        Collection<WebSocketSession> mockSessions = Collections.singletonList(mock(WebSocketSession.class));

        // Bây giờ kiểu dữ liệu khớp hoàn toàn (Collection vs Collection)
        when(sessionRegistry.getAllSessions()).thenReturn(mockSessions);

        // WHEN
        MessageDTO result = chatService.handleJoin(sessionId, joinDto);

        // THEN
        verify(sessionRegistry).tryRegisterUser(sessionId, newName);
        assertEquals("Bob has joined the chat. (Total: 1)", result.getContent());
    }
}