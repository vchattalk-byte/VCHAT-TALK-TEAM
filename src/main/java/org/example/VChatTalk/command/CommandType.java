package org.example.VChatTalk.command;

public enum CommandType {
    NONE,       // normal text
    HELP,
    SELECT,     // /select username
    LIST,
    UNKNOWN     // starts with / but invalid
}