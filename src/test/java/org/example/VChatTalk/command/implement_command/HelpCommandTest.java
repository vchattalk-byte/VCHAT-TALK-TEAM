package org.example.VChatTalk.command.implement_command;

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
        // Mock input (Result không quan trọng với HelpCommand)
        CommandResult result = new CommandResult(CommandType.HELP, null, null);

        // Thực thi
        helpCommand.execute(session, result);

        // Capture (bắt) lại nội dung tin nhắn đã gửi để kiểm tra
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);

        // Verify: responder.sendSystem phải được gọi 1 lần
        verify(responder).sendSystem(org.mockito.ArgumentMatchers.eq(session), messageCaptor.capture());

        String sentMessage = messageCaptor.getValue();

        // Kiểm tra nội dung tin nhắn
        // 1. Phải có tiêu đề
        assertTrue(sentMessage.contains("Available commands:"));

        // 2. Phải chứa các lệnh quan trọng (Check ngẫu nhiên vài lệnh)
        assertTrue(sentMessage.contains("/help"));
        assertTrue(sentMessage.contains("/join"));
        assertTrue(sentMessage.contains("/select"));
        assertTrue(sentMessage.contains("/list"));
        assertTrue(sentMessage.contains("/exit"));

        // 3. Phải chứa mô tả (Description) của lệnh
        // Ví dụ lệnh SELECT có mô tả "Private chat with user" trong Enum
        assertTrue(sentMessage.contains("Private chat with user"));
    }
}