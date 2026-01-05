package org.example.VChatTalk.command.implement_command;

import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
import org.example.VChatTalk.util.SystemResponseSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UnknownCommandTest {

    @Mock
    private SystemResponseSender responder;

    @Mock
    private WebSocketSession session;

    private UnknownCommand unknownCommand;

    @BeforeEach
    void setUp() {
        unknownCommand = new UnknownCommand(responder);
    }

    @Test
    @DisplayName("Should return UNKNOWN type")
    void testGetType() {
        assertEquals(CommandType.UNKNOWN, unknownCommand.getType());
    }

    @Test
    @DisplayName("Execute with specific error message from Parser")
    void testExecute_WithSpecificError() throws IOException {
        // The Parser emulator returns the following specific error: "Invalid command format."
        String errorMsg = "Invalid command format.";
        CommandResult result = new CommandResult(CommandType.UNKNOWN, null, errorMsg);

        unknownCommand.execute(session, result);

        // Verify: You must send the exact error to the client.
        verify(responder).sendError(session, errorMsg);
    }

    @Test
    @DisplayName("Execute with null error message -> Use Default Message")
    void testExecute_WithNullError() throws IOException {
        // Simulate a null error scenario (rare but needs handling)
        CommandResult result = new CommandResult(CommandType.UNKNOWN, null, null);

        unknownCommand.execute(session, result);

        // Verify: Send default notifications
        verify(responder).sendError(session, "Unknown command. Type /help for assistance.");
    }

    @Test
    @DisplayName("Execute with empty error message -> Use Default Message")
    void testExecute_WithEmptyError() throws IOException {
        CommandResult result = new CommandResult(CommandType.UNKNOWN, null, "");

        unknownCommand.execute(session, result);

        verify(responder).sendError(session, "Unknown command. Type /help for assistance.");
    }
}