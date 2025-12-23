package org.example.VChatTalk.command;

public enum CommandType {
    SELECT,     // /select username
    UNKNOWN,    // starts with / but invalid
    NONE,        // normal text
    LIST
}