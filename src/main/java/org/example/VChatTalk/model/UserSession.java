package org.example.VChatTalk.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.With;
import org.springframework.web.socket.WebSocketSession;

import java.util.Objects;

@Setter
@Getter
@Builder
public class UserSession {
    private final String sessionId;
    @With
    private final String username;        // null nếu chưa đăng ký
    @With
    private final String targetUser;      // chat riêng
    private final WebSocketSession session;
    @With
    private final String currentRoomId;   // cho Sprint 6

    // equals & hashCode dựa trên sessionId
    @Override
    public boolean equals(Object o) {
        if(this == o) return true;
        if(!(o instanceof UserSession)) return false;
        return sessionId.equals(((UserSession) o).sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }
}
