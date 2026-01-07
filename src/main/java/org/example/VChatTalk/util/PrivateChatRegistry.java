package org.example.VChatTalk.util;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PrivateChatRegistry {

    // key = sessionId (who is targeting), value = targetUsername (who is being targeted)
    private final ConcurrentHashMap<String, String> sessionTargets = new ConcurrentHashMap<>();

    public void setTarget(String sessionId, String targetUsername) {
        if (sessionId != null && targetUsername != null) {
            sessionTargets.put(sessionId, targetUsername);
        }
    }

    public String getTarget(String sessionId) {
        if (sessionId == null) return null;
        return sessionTargets.get(sessionId);
    }

    public void removeTarget(String sessionId) {
        if (sessionId != null) {
            sessionTargets.remove(sessionId);
        }
    }

    public List<String> getSessionsTargeting(String targetUsername) {
        if (targetUsername == null) return Collections.emptyList();

        List<String> targetingSessions = new ArrayList<>();
        sessionTargets.forEach((sessionId, target) -> {
            if (target.equals(targetUsername)) {
                targetingSessions.add(sessionId);
            }
        });

        return Collections.unmodifiableList(targetingSessions);
    }
}
