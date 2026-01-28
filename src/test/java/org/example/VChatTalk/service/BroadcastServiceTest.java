package org.example.VChatTalk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.VChatTalk.model.MessageDTO;
import org.example.VChatTalk.model.MessageType;
import org.example.VChatTalk.util.SessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BroadcastServiceTest {

    @Mock private SessionRegistry sessionRegistry;

    // Use a Spy for ObjectMapper to test real serialization
    @Spy private ObjectMapper mapper = new ObjectMapper();

    @InjectMocks
    private BroadcastService broadcastService;

    private WebSocketSession sessionA;
    private WebSocketSession sessionB;
    private MessageDTO messageDto;

    @BeforeEach
    void setUp() {
        sessionA = mock(WebSocketSession.class);
        sessionB = mock(WebSocketSession.class);

        lenient().when(sessionA.getId()).thenReturn("id-a");
        lenient().when(sessionB.getId()).thenReturn("id-b");
        lenient().when(sessionA.isOpen()).thenReturn(true);
        lenient().when(sessionB.isOpen()).thenReturn(true);

        messageDto = MessageDTO.builder()
                .sender("Alice")
                .content("Hello")
                .type(MessageType.MESSAGE)
                .build();
    }

    @Test
    @DisplayName("Broadcast - Should send to everyone except sender")
    void broadcast_ExcludeSender() throws IOException {
        // Arrange: Alice (sessionA) sends a message
        when(sessionRegistry.getAllSessions()).thenReturn(List.of(sessionA, sessionB));

        // Act
        broadcastService.broadcast(messageDto, sessionA);

        // Assert: sessionB receives it, sessionA does not
        verify(sessionB, times(1)).sendMessage(any(TextMessage.class));
        verify(sessionA, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("Broadcast - System message should be sent to everyone")
    void broadcast_SystemMessage() throws IOException {
        // Arrange: System message (sender is null)
        when(sessionRegistry.getAllSessions()).thenReturn(List.of(sessionA, sessionB));

        // Act
        broadcastService.broadcast(messageDto, null);

        // Assert: Both sessions receive it
        verify(sessionA, times(1)).sendMessage(any(TextMessage.class));
        verify(sessionB, times(1)).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("Broadcast - Should skip closed sessions")
    void broadcast_SkipClosedSession() throws IOException {
        // Arrange: Session B is closed
        when(sessionB.isOpen()).thenReturn(false);
        when(sessionRegistry.getAllSessions()).thenReturn(List.of(sessionA, sessionB));

        // Act: System broadcast
        broadcastService.broadcast(messageDto, null);

        // Assert: Only A gets it
        verify(sessionA, times(1)).sendMessage(any(TextMessage.class));
        verify(sessionB, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("sendToSession - Should send message to specific user")
    void sendToSession_Success() throws IOException {
        // Act
        broadcastService.sendToSession(sessionA, messageDto);

        // Assert
        verify(sessionA, times(1)).sendMessage(any(TextMessage.class));
        // Verify JSON content (optional check)
        String expectedJson = mapper.writeValueAsString(messageDto);
        verify(sessionA).sendMessage(new TextMessage(expectedJson));
    }

    @Test
    @DisplayName("Broadcast - Should continue if one session fails")
    void broadcast_FailureInOneSession() throws IOException {
        // Arrange: Session A throws error, but session B should still work
        when(sessionRegistry.getAllSessions()).thenReturn(List.of(sessionA, sessionB));
        doThrow(new IOException("Network error")).when(sessionA).sendMessage(any(TextMessage.class));

        // Act
        broadcastService.broadcast(messageDto, null);

        // Assert
        verify(sessionA).sendMessage(any(TextMessage.class)); // Attempted
        verify(sessionB).sendMessage(any(TextMessage.class)); // Still executed
    }
    @Test
    @DisplayName("broadcastToRoom - should send to all room members except sender")
    void broadcastToRoom_excludeSender() throws IOException {
        WebSocketSession memberA = mock(WebSocketSession.class);
        WebSocketSession memberB = mock(WebSocketSession.class);

        when(memberA.isOpen()).thenReturn(true);
        when(memberB.isOpen()).thenReturn(true);

        when(sessionRegistry.findSessionById("A")).thenReturn(memberA);
        when(sessionRegistry.findSessionById("B")).thenReturn(memberB);

        MessageDTO msg = MessageDTO.builder()
                .type(MessageType.MESSAGE)
                .sender("Alice")
                .content("Hello Room")
                .build();

        broadcastService.broadcastToRoom(
                msg,
                Set.of("sender", "A", "B"),
                "sender"
        );

        verify(memberA).sendMessage(any());
        verify(memberB).sendMessage(any());
    }

    @Test
    @DisplayName("broadcastToRoom - should do nothing when room has no members")
    void broadcastToRoom_emptyRoom() throws IOException {
        MessageDTO msg = MessageDTO.builder()
                .type(MessageType.MESSAGE)
                .content("Hello")
                .build();

        broadcastService.broadcastToRoom(msg, Set.of(), "sender");

        verifyNoInteractions(sessionRegistry);
    }

    @Test
    @DisplayName("broadcastToRoom - should skip closed sessions")
    void broadcastToRoom_skipClosedSession() throws IOException {
        WebSocketSession member = mock(WebSocketSession.class);

        when(member.isOpen()).thenReturn(false);
        when(sessionRegistry.findSessionById("A")).thenReturn(member);

        MessageDTO msg = MessageDTO.builder()
                .type(MessageType.MESSAGE)
                .content("Hello")
                .build();

        broadcastService.broadcastToRoom(
                msg,
                Set.of("A"),
                "sender"
        );

        verify(member, never()).sendMessage(any());
    }

    @Test
    @DisplayName("broadcastToRoom - should continue if one session fails")
    void broadcastToRoom_continueOnFailure() throws IOException {
        WebSocketSession memberA = mock(WebSocketSession.class);
        WebSocketSession memberB = mock(WebSocketSession.class);

        when(memberA.isOpen()).thenReturn(true);
        when(memberB.isOpen()).thenReturn(true);

        when(sessionRegistry.findSessionById("A")).thenReturn(memberA);
        when(sessionRegistry.findSessionById("B")).thenReturn(memberB);

        doThrow(new IOException("IO error"))
                .when(memberA).sendMessage(any());

        MessageDTO msg = MessageDTO.builder()
                .type(MessageType.MESSAGE)
                .content("Hello")
                .build();

        broadcastService.broadcastToRoom(
                msg,
                Set.of("A", "B"),
                "sender"
        );

        verify(memberA).sendMessage(any());
        verify(memberB).sendMessage(any());
    }

    // ========== US-6.5 ADDITIONAL TESTS ==========
    @Test
    @DisplayName("broadcastToTargets - should send formatted room message to all target sessions")
    void broadcastToTargets_sendToAllTargets() throws IOException {
        WebSocketSession session1 = mock(WebSocketSession.class);
        WebSocketSession session2 = mock(WebSocketSession.class);

        when(session1.isOpen()).thenReturn(true);
        when(session2.isOpen()).thenReturn(true);
        when(sessionRegistry.findSessionById("S1")).thenReturn(session1);
        when(sessionRegistry.findSessionById("S2")).thenReturn(session2);

        MessageDTO dto = MessageDTO.builder()
                .type(MessageType.MESSAGE)
                .sender("UserA")
                .content("Hello team")
                .roomId("#team-room")
                .build();

        broadcastService.broadcastToTargets(dto, Set.of("S1", "S2"));

        String expectedJson = mapper.writeValueAsString(
                MessageDTO.builder()
                        .type(MessageType.MESSAGE)
                        .sender("UserA")
                        .content("[#team-room] UserA: Hello team")
                        .roomId("#team-room")
                        .build()
        );

        verify(session1).sendMessage(new TextMessage(expectedJson));
        verify(session2).sendMessage(new TextMessage(expectedJson));
    }

    @Test
    @DisplayName("broadcastToTargets - should skip closed sessions and continue sending others")
    void broadcastToTargets_skipClosedSessions() throws IOException {
        WebSocketSession closedSession = mock(WebSocketSession.class);
        WebSocketSession openSession = mock(WebSocketSession.class);

        when(closedSession.isOpen()).thenReturn(false);
        when(openSession.isOpen()).thenReturn(true);
        when(sessionRegistry.findSessionById("S1")).thenReturn(closedSession);
        when(sessionRegistry.findSessionById("S2")).thenReturn(openSession);

        MessageDTO dto = MessageDTO.builder()
                .type(MessageType.MESSAGE)
                .sender("UserB")
                .content("Message to active sessions")
                .roomId("#dev-room")
                .build();

        broadcastService.broadcastToTargets(dto, Set.of("S1", "S2"));

        verify(closedSession, never()).sendMessage(any());
        verify(openSession, times(1)).sendMessage(any());
    }

    @Test
    @DisplayName("broadcastToTargets - should continue even if one session throws IOException")
    void broadcastToTargets_continueWhenIOExceptionOccurs() throws IOException {
        WebSocketSession faultySession = mock(WebSocketSession.class);
        WebSocketSession validSession = mock(WebSocketSession.class);

        when(faultySession.isOpen()).thenReturn(true);
        when(validSession.isOpen()).thenReturn(true);
        when(sessionRegistry.findSessionById("A")).thenReturn(faultySession);
        when(sessionRegistry.findSessionById("B")).thenReturn(validSession);

        doThrow(new IOException("Simulated failure")).when(faultySession).sendMessage(any());

        MessageDTO dto = MessageDTO.builder()
                .type(MessageType.MESSAGE)
                .sender("UserC")
                .content("Testing broadcast with one failure")
                .roomId("#test-room")
                .build();

        broadcastService.broadcastToTargets(dto, Set.of("A", "B"));

        verify(faultySession).sendMessage(any()); // attempted
        verify(validSession).sendMessage(any());  // still delivered
    }

    @Test
    @DisplayName("broadcastToTargets - should do nothing when sessionIds list is empty")
    void broadcastToTargets_emptySessionList() {
        MessageDTO dto = MessageDTO.builder()
                .type(MessageType.MESSAGE)
                .sender("System")
                .content("No sessions to send")
                .roomId("#empty-room")
                .build();

        broadcastService.broadcastToTargets(dto, Set.of());

        verifyNoInteractions(sessionRegistry);
    }

}