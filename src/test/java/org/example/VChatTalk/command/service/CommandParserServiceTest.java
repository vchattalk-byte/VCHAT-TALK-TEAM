package org.example.VChatTalk.command.service;

import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandParserServiceTest {

    private CommandParserService parser;

    @BeforeEach
    void setUp() {
        parser = new CommandParserService();
    }

    // --- HAPPY CASES (Trường hợp đúng) ---

    @Test
    @DisplayName("Parse valid command with argument: /select Alice")
    void testParseValidCommandWithArgument() {
        CommandResult result = parser.parse("/select Alice");

        assertEquals(CommandType.SELECT, result.getType());
        assertEquals("Alice", result.getArgument());
        assertNull(result.getError());
    }

    @Test
    @DisplayName("Parse valid command without argument: /list")
    void testParseValidCommandNoArgument() {
        CommandResult result = parser.parse("/list");

        assertEquals(CommandType.LIST, result.getType());
        assertNull(result.getArgument()); // Tham số phải null
        assertNull(result.getError());
    }

    @Test
    @DisplayName("Parse mixed case command: /SeLeCt Bob -> Handled as SELECT")
    void testParseMixedCaseCommand() {
        // Hệ thống hỗ trợ case-insensitive cho tên lệnh
        CommandResult result = parser.parse("/SeLeCt Bob");

        assertEquals(CommandType.SELECT, result.getType());
        assertEquals("Bob", result.getArgument());
    }

    @Test
    @DisplayName("Parse argument with spaces: /send Hello World")
    void testParseArgumentWithSpaces() {
        // Regex phải lấy trọn vẹn phần sau khoảng trắng đầu tiên
        // Giả sử ta thêm lệnh /send sau này, ở đây test cơ chế parser
        // Dùng /login làm ví dụ dù login thường không có dấu cách

        // Test với input thực tế của parser
        CommandResult result = parser.parse("/select User Name With Spaces");

        assertEquals(CommandType.SELECT, result.getType());
        assertEquals("User Name With Spaces", result.getArgument());
    }

    // --- EDGE CASES (Trường hợp biên/Lỗi) ---

    @Test
    @DisplayName("Parse empty input -> NONE")
    void testParseEmptyInput() {
        CommandResult result = parser.parse("");
        assertEquals(CommandType.NONE, result.getType());
    }

    @Test
    @DisplayName("Parse null input -> NONE")
    void testParseNullInput() {
        CommandResult result = parser.parse(null);
        assertEquals(CommandType.NONE, result.getType());
    }

    @Test
    @DisplayName("Parse normal chat (no slash) -> NONE")
    void testParseNormalChat() {
        CommandResult result = parser.parse("Hello world");
        assertEquals(CommandType.NONE, result.getType());
    }

    @Test
    @DisplayName("Parse unknown command -> UNKNOWN")
    void testParseUnknownCommand() {
        CommandResult result = parser.parse("/abcdef");

        assertEquals(CommandType.UNKNOWN, result.getType());
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("Unknown command"));
    }

    @Test
    @DisplayName("Parse invalid format (only slash) -> UNKNOWN")
    void testParseInvalidFormatOnlySlash() {
        CommandResult result = parser.parse("/");

        // Regex yêu cầu /[a-zA-Z0-9]+ nên "/" đứng một mình sẽ không khớp
        assertEquals(CommandType.UNKNOWN, result.getType());
        assertEquals("Invalid command format.", result.getError());
    }

    @Test
    @DisplayName("Parse extra spaces handling")
    void testParseExtraSpaces() {
        // "/select   Alice" -> Parser sẽ trim khoảng trắng thừa giữa lệnh và tham số
        CommandResult result = parser.parse("/select    Alice");

        assertEquals(CommandType.SELECT, result.getType());
        assertEquals("Alice", result.getArgument());
    }
}