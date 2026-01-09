package org.example.VChatTalk.command.impl;

import org.example.VChatTalk.command.CommandResult;
import org.example.VChatTalk.command.CommandType;
import org.example.VChatTalk.command.MessageConstants;
import org.example.VChatTalk.util.SystemResponseSender;
import org.example.VChatTalk.util.UserRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginCommandTest {

    @Mock private UserRegistry userRegistry;
    @Mock private SystemResponseSender responder;
    @Mock private WebSocketSession session;

    private LoginCommand loginCommand;

    @BeforeEach
    void setUp() {
        loginCommand = new LoginCommand(userRegistry, responder);
    }

    @Test
    void testGetType() {
        assertEquals(CommandType.LOGIN, loginCommand.getType());
    }

    @Test
    @DisplayName("Fail if already logged in")
    void testExecute_AlreadyLoggedIn() throws IOException {
        when(session.getId()).thenReturn("s1");
        when(userRegistry.isUserRegistered("s1")).thenReturn(true);
        when(userRegistry.getUsername("s1")).thenReturn("OldName");

        loginCommand.execute(session, new CommandResult(CommandType.LOGIN, "NewName", null));

        verify(responder).sendError(session, String.format(MessageConstants.ERR_ALREADY_LOGGED_IN, "OldName"));
        verify(userRegistry, never()).tryRegisterUser(anyString(), anyString());
    }

    @Test
    @DisplayName("Fail if username argument is missing")
    void testExecute_MissingArg() throws IOException {
        when(session.getId()).thenReturn("s1");
        when(userRegistry.isUserRegistered("s1")).thenReturn(false);

        loginCommand.execute(session, new CommandResult(CommandType.LOGIN, null, null));

        verify(responder).sendError(session, String.format(MessageConstants.ERR_MISSING_ARG_USER, "/login"));
    }

    @Test
    @DisplayName("Fail if username format invalid (special chars)")
    void testExecute_InvalidFormat() throws IOException {
        when(session.getId()).thenReturn("s1");
        when(userRegistry.isUserRegistered("s1")).thenReturn(false);

        // Name contains special characters @#$
        loginCommand.execute(session, new CommandResult(CommandType.LOGIN, "User@123", null));

        verify(responder).sendError(session, MessageConstants.ERR_INVALID_USERNAME);
    }

    @Test
    @DisplayName("Fail if username already taken")
    void testExecute_UsernameTaken() throws IOException {
        when(session.getId()).thenReturn("s1");
        when(userRegistry.isUserRegistered("s1")).thenReturn(false);
        // Simulate tryRegisterUser returning false
        when(userRegistry.tryRegisterUser("s1", "DuplicateName")).thenReturn(false);

        loginCommand.execute(session, new CommandResult(CommandType.LOGIN, "DuplicateName", null));

        verify(responder).sendError(session, String.format(MessageConstants.ERR_USERNAME_TAKEN, "DuplicateName"));
    }

    @Test
    @DisplayName("Success: Register user")
    void testExecute_Success() throws IOException {
        when(session.getId()).thenReturn("s1");
        when(userRegistry.isUserRegistered("s1")).thenReturn(false);
        when(userRegistry.tryRegisterUser("s1", "ValidName")).thenReturn(true);

        loginCommand.execute(session, new CommandResult(CommandType.LOGIN, "ValidName", null));

        verify(responder).sendSystem(session, String.format(MessageConstants.MSG_LOGIN_SUCCESS, "ValidName"));
    }
}