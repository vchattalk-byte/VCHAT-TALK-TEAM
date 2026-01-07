package org.example.VChatTalk.command;

import org.example.VChatTalk.util.AnsiColor;

public class MessageConstants {
    // --- CONSTANTS ---
    public static final String USER_ANONYMOUS = "Anonymous";

    // Special Character
    public static final char BELL = '\u0007';

    // --- ERROR MESSAGES ---
    public static final String ERR_NOT_LOGGED_IN =
            AnsiColor.RED + "You must login first! Use /login <username>" + AnsiColor.RESET;

    public static final String ERR_ALREADY_LOGGED_IN =
            AnsiColor.RED + "You are already logged in as '%s'." + AnsiColor.RESET;

    public static final String ERR_USERNAME_TAKEN =
            AnsiColor.RED + "Username '%s' is already taken. Please choose another." + AnsiColor.RESET;

    public static final String ERR_INVALID_USERNAME =
            AnsiColor.RED + "Invalid username! Must be 3-20 characters (a-z, 0-9, _, -)." + AnsiColor.RESET;

    public static final String ERR_USERNAME_CONTAIN_SPACE =
            AnsiColor.RED + "Invalid username format. Username cannot contain spaces." + AnsiColor.RESET;

    public static final String ERR_SELF_CHAT =
            AnsiColor.RED + "You cannot chat with yourself!" + AnsiColor.RESET;

    public static final String ERR_USER_OFFLINE =
            AnsiColor.RED + "User '%s' is offline or does not exist." + AnsiColor.RESET;

    public static final String ERR_MISSING_ARG_USER =
            AnsiColor.RED + "Usage: %s <username> (Missing username)" + AnsiColor.RESET;

    public static final String ERR_ALREADY_IN_GLOBAL =
            AnsiColor.RED + "Already in global chat." + AnsiColor.RESET;

    public static final String ERR_RATE_LIMIT = AnsiColor.RED + "You are sending messages too quickly. Please slow down." + AnsiColor.RESET;

    public static final String ERR_SEND_MUST_LOGIN = AnsiColor.RED + "You must join the chat before sending messages. Use /login <name>" + AnsiColor.RESET;

    public static final String ERR_INVALID_JSON = AnsiColor.RED + "Invalid message format." + AnsiColor.RESET;

    // --- SYSTEM MESSAGES ---
    public static final String MSG_LOGIN_SUCCESS =
            AnsiColor.CYAN + "Welcome %s! You have joined the chat server." + AnsiColor.RESET;

    public static final String MSG_GOODBYE =
            AnsiColor.CYAN + "Goodbye, %s!" + AnsiColor.RESET;

    public static final String MSG_ONLINE_USERS =
            AnsiColor.YELLOW + "Online users (%d): %s" + AnsiColor.RESET;

    public static final String MSG_NO_USERS =
            AnsiColor.YELLOW + "No users online" + AnsiColor.RESET;

    public static final String MSG_WELCOME = AnsiColor.GREEN +
            "Connected! You are currently " + USER_ANONYMOUS + ".\n" +
            "Please use /login <username> to join chat." + AnsiColor.RESET;

    public static final String MSG_DISCONNECT_NOTIFY = "User %s has disconnected. Private chat ended.";

    public static final String MSG_JOINED_SUCCESS = AnsiColor.GREEN + "You joined the chat successfully." + AnsiColor.RESET;

    // --- PRIVATE CHAT MESSAGES ---
    public static final String MSG_PRIVATE_CHAT_START =
            AnsiColor.GREEN + "Switched to private chat with: %s" + BELL + AnsiColor.RESET;

    public static final String MSG_PRIVATE_CHAT_LEAVE =
            AnsiColor.GREEN + "You left private chat. Now in global chat." + BELL + AnsiColor.RESET;
    private MessageConstants() {}
}
