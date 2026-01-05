package org.example.VChatTalk.command;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommandResult {

    private CommandType type;

    private String argument;

    private String error;
}
