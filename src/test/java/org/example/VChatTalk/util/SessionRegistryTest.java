package org.example.VChatTalk.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionRegistryTest {

    private SessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SessionRegistry();
    }

    @Test
    void testSessionLifecycle() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s1");

        assertNull(registry.findSessionById("s1"));

        registry.addSession(session);
        assertEquals(session, registry.findSessionById("s1"));

        registry.removeSession("s1");
        assertNull(registry.findSessionById("s1"));
    }

    @Test
    void testGetAllSessions() {
        WebSocketSession s1 = mock(WebSocketSession.class);
        WebSocketSession s2 = mock(WebSocketSession.class);

        when(s1.getId()).thenReturn("s1");
        when(s2.getId()).thenReturn("s2");

        registry.addSession(s1);
        registry.addSession(s2);

        Collection<WebSocketSession> sessions = registry.getAllSessions();

        assertEquals(2, sessions.size());
        assertTrue(sessions.contains(s1));
        assertTrue(sessions.contains(s2));
    }

    @Test
    void testRemoveNonExistentSession() {
        assertDoesNotThrow(() -> registry.removeSession("unknown"));
    }

    @Test
    void testEdgeCases_NullInputs() {
        assertDoesNotThrow(() -> registry.addSession(null));
        assertDoesNotThrow(() -> registry.removeSession(null));
        assertNull(registry.findSessionById(null));
        assertNotNull(registry.getAllSessions());
        assertTrue(registry.getAllSessions().isEmpty());
    }

    @Test
    void testAddSession_WithNullSessionId() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(null);

        registry.addSession(session);

        assertTrue(registry.getAllSessions().isEmpty());
    }

    @Test
    void testGetAllSessions_IsUnmodifiable() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s1");

        registry.addSession(session);

        Collection<WebSocketSession> sessions = registry.getAllSessions();

        assertThrows(UnsupportedOperationException.class, sessions::clear);
    }

    @Test
    void testAddSession_SameSessionIdOverrides() {
        WebSocketSession s1 = mock(WebSocketSession.class);
        WebSocketSession s2 = mock(WebSocketSession.class);

        when(s1.getId()).thenReturn("s1");
        when(s2.getId()).thenReturn("s1");

        registry.addSession(s1);
        registry.addSession(s2);

        assertEquals(s2, registry.findSessionById("s1"));
    }
}
