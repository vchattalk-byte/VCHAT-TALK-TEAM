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

    // Test Case 1: Duplicate username registration (Race Condition Logic) -> Should throw exception
    @Test
    void testHandleJoin_DuplicateUsername_ShouldThrowException() {
        // GIVEN
        String sessionId = "session-new-user";
        String duplicateName = "Alice";

        MessageDTO joinDto = new MessageDTO();
        joinDto.setSender(duplicateName);

        // Mock: User is not registered yet
        when(sessionRegistry.isUserRegistered(sessionId)).thenReturn(false);

        // Mock: tryRegisterUser returns FALSE (simulating race condition or taken name)
        when(sessionRegistry.tryRegisterUser(sessionId, duplicateName)).thenReturn(false);

        // WHEN & THEN
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            chatService.handleJoin(sessionId, joinDto);
        });

        assertEquals("Username 'Alice' is already taken. Please choose another.", exception.getMessage());
    }

    // Test Case 2: Successful registration (Happy Path)
    @Test
    void testHandleJoin_ValidUsername_ShouldSucceed() {
        // GIVEN
        String sessionId = "session-123";
        String newName = "Bob";
        MessageDTO joinDto = new MessageDTO();
        joinDto.setSender(newName);

        when(sessionRegistry.isUserRegistered(sessionId)).thenReturn(false);
        when(sessionRegistry.tryRegisterUser(sessionId, newName)).thenReturn(true);

        // Mock getAllSessions returning a Collection (size = 1)
        Collection<WebSocketSession> mockSessions = Collections.singletonList(mock(WebSocketSession.class));
        when(sessionRegistry.getAllSessions()).thenReturn(mockSessions);

        // WHEN
        MessageDTO result = chatService.handleJoin(sessionId, joinDto);

        // THEN
        verify(sessionRegistry).tryRegisterUser(sessionId, newName);
        assertEquals("Bob has joined the chat. (Total: 1)", result.getContent());
    }
}