package org.example.VChatTalk.command.impl;

import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HelpCommandTest {

    @Mock
    private SystemResponseSender responder;

    @Mock
    private WebSocketSession session;

    private HelpCommand helpCommand;

    @BeforeEach
    void setUp() {
        helpCommand = new HelpCommand(responder);
    }

    @Test
    @DisplayName("Should return HELP type")
    void testGetType() {
        assertEquals(CommandType.HELP, helpCommand.getType());
    }

    @Test
    @DisplayName("Execute should send system message with all available commands")
    void testExecute() throws IOException {
        // Mock input
        CommandResult result = new CommandResult(CommandType.HELP, null, null);

        // Execute
        helpCommand.execute(session, result);

        // Capture the content of the send message for verification
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        // Verify: responder.sendSystem must be called once.
        verify(responder).sendSystem(org.mockito.ArgumentMatchers.eq(session), messageCaptor.capture());

        String sentMessage = messageCaptor.getValue();

        // Check message content
        // Must have a title
        assertTrue(sentMessage.contains("Available commands:"));

        // Must contain important commands (Randomly check a few commands)
        assertTrue(sentMessage.contains("/help"));
        assertTrue(sentMessage.contains("/join")); // sprint 6
        assertTrue(sentMessage.contains("/select"));
        assertTrue(sentMessage.contains("/list"));
        assertTrue(sentMessage.contains("/exit"));

        // Must contain a description of the command.
        assertTrue(sentMessage.contains("Private chat with user"));
    }
}