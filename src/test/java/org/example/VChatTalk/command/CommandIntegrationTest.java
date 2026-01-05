package org.example.VChatTalk.command;

import org.example.VChatTalk.command.implement_command.*;
import org.example.VChatTalk.command.service.CommandParserService;
import org.example.VChatTalk.util.SessionRegistry;
import org.example.VChatTalk.util.SystemResponseSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommandIntegrationTest {

    // --- Real Components (The Core Engine) ---
    private SessionRegistry sessionRegistry;
    private CommandParserService commandParser;
    private CommandExecutor commandExecutor;

    // --- Mocks (External Boundaries only) ---
    @Mock
    private SystemResponseSender responder;

    @BeforeEach
    void setUp() {
        // 1. Instantiate Real Components
        // We use the REAL SessionRegistry logic you just uploaded.
        sessionRegistry = new SessionRegistry();
        commandParser = new CommandParserService();

        // 2. Wire up the Commands with the Real Registry
        List<IChatCommand> commands = Arrays.asList(
                new LoginCommand(sessionRegistry, responder),
                new SelectCommand(sessionRegistry, responder),
                new ListCommand(sessionRegistry, responder),
                new HelpCommand(responder),
                new LeaveCommand(sessionRegistry, responder),
                new ExitCommand(sessionRegistry, responder),
                new UnknownCommand(responder)
        );

        // 3. Initialize Executor
        commandExecutor = new CommandExecutor(commands);
    }

    @Test
    @DisplayName("E2E Flow: Connect -> Login -> Select -> Private Chat -> Leave")
    void testHappyPath() throws IOException {
        // --- Phase 1: Connection (Simulate WebSocket Handshake) ---
        WebSocketSession sessionAlice = mock(WebSocketSession.class);
        when(sessionAlice.getId()).thenReturn("sess-alice");

        WebSocketSession sessionBob = mock(WebSocketSession.class);
        when(sessionBob.getId()).thenReturn("sess-bob");

        // Important: Handler usually does this, so we simulate it here
        sessionRegistry.addSession(sessionAlice);
        sessionRegistry.addSession(sessionBob);

        // --- Phase 2: Login ---
        execute("/login Alice", sessionAlice);

        // Verify Real Registry State
        assertEquals("Alice", sessionRegistry.getUsername("sess-alice"));
        assertTrue(sessionRegistry.isUserOnline("Alice"));

        execute("/login Bob", sessionBob);
        assertTrue(sessionRegistry.isUserOnline("Bob"));

        // --- Phase 3: Private Chat Selection ---
        execute("/select Bob", sessionAlice);

        // Verify Target is set in Real Registry
        assertEquals("Bob", sessionRegistry.getTarget("sess-alice"));

        // Verify Output (FR-12 Check)
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(responder, atLeastOnce()).sendSystem(eq(sessionAlice), captor.capture());
        String successMsg = captor.getValue();
        assertTrue(successMsg.contains("Bob")); // Message confirms target

        // --- Phase 4: Leave Chat ---
        execute("/leave", sessionAlice);

        // Verify Target is removed in Real Registry
        assertNull(sessionRegistry.getTarget("sess-alice"));
        verify(responder).sendSystem(eq(sessionAlice), contains("left private chat"));
    }

    @Test
    @DisplayName("E2E Flow: Error Handling & Validation")
    void testValidationLogic() throws IOException {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("sess-error-test");
        sessionRegistry.addSession(session);

        // 1. Select before login
        execute("/select Alice", session);
        // FIX: Changed "must join" to "login" to match your actual code's error message
        verify(responder).sendError(eq(session), contains("login"));

        // 2. Login
        execute("/login Tester", session);

        // 3. Select self
        execute("/select Tester", session);
        verify(responder).sendError(eq(session), contains("cannot chat with yourself"));

        // 4. Select offline user
        execute("/select Ghost", session);
        verify(responder).sendError(eq(session), contains("offline"));

        // 5. Duplicate login
        execute("/login Tester", session);
        verify(responder).sendError(eq(session), contains("already logged in"));
    }

    @Test
    @DisplayName("E2E Flow: Concurrency Check (Registry Synchronization)")
    void testDuplicateLoginAttempt() throws IOException {
        // Create two sessions trying to claim the same name
        WebSocketSession s1 = mock(WebSocketSession.class);
        when(s1.getId()).thenReturn("s1");
        sessionRegistry.addSession(s1);

        WebSocketSession s2 = mock(WebSocketSession.class);
        when(s2.getId()).thenReturn("s2");
        sessionRegistry.addSession(s2);

        // s1 logs in first
        execute("/login Admin", s1);
        assertTrue(sessionRegistry.isUserOnline("Admin"));

        // s2 tries to take "Admin"
        execute("/login Admin", s2);

        // Verify s2 got an error and is NOT registered as Admin
        verify(responder).sendError(eq(s2), contains("already taken"));
        assertNotEquals("Admin", sessionRegistry.getUsername("s2"));
    }

    // Helper for cleaner test code
    private void execute(String commandText, WebSocketSession session) throws IOException {
        CommandResult result = commandParser.parse(commandText);
        commandExecutor.execute(session, result);
    }
}