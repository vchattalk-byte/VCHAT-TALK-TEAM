package org.example.VChatTalk.model;

import lombok.Builder;
import lombok.Getter;
import lombok.With;
import org.springframework.web.socket.WebSocketSession;

import java.util.Objects;


@Getter
@Builder
public class UserSession {
    private final String sessionId;
    @With
    private final String username;
    @With
    private final String targetUser;
    private final WebSocketSession session;
    @With
    private final String currentRoomId;


    @Override
    public boolean equals(Object o) {
        if(this == o) return true;
        if(!(o instanceof UserSession)) return false;
        return Objects.equals(this.sessionId, ((UserSession) o).sessionId); // fixed it
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }
}
