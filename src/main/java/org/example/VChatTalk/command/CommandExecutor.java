package org.example.VChatTalk.command;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CommandExecutor {
    private final Map<CommandType, IChatCommand> commandRegistry;

    public CommandExecutor(List<IChatCommand> commands) {
        this.commandRegistry = commands.stream().collect(Collectors.toMap(IChatCommand::getType, Function.identity()));
    }
    public void execute(WebSocketSession session, CommandResult result)throws IOException{
        commandRegistry.getOrDefault(result.getType(),commandRegistry.get(CommandType.UNKNOWN)).execute(session,result);
    }
    public IChatCommand getCommand(CommandType type){
        return commandRegistry.get(type);
    }
}
