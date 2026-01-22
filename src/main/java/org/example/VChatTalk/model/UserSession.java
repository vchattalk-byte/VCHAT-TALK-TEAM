package org.example.VChatTalk.model;

import lombok.Builder;
import lombok.Getter;
import lombok.With;

import java.util.Objects;


@Getter
@Builder(toBuilder = true)
public class UserSession {
    private final String sessionId;

    @With
    private final String username;

    @With
    private final ChatContext context;

    /**
     * Factory method to create a fresh anonymous session.
     * Default context is GLOBAL.
     */
    public static UserSession create(String sessionId) {
        return UserSession.builder()
                .sessionId(sessionId)
                .username("Anonymous")
                .context(ChatContext.GLOBAL)
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) return true;
        if(!(o instanceof UserSession)) return false;
        return Objects.equals(this.sessionId, ((UserSession) o).sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }
}
