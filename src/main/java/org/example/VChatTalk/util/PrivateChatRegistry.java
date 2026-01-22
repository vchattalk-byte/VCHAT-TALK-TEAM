package org.example.VChatTalk.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for Private Chat connections.
 * Stores "Who is talking to whom".
 * <p>
 * Optimized with Reverse Index for O(1) lookup on disconnects.
 */
@Slf4j
@Component
public class PrivateChatRegistry {

    // key = sessionId (who is targeting), value = targetUsername (who is being targeted)
    private final ConcurrentHashMap<String, String> sessionTargets = new ConcurrentHashMap<>();

    // Reverse Index: Target Username -> Set of SessionIDs (Senders)
    // Used for O(1) notification when a target user disconnects
    private final ConcurrentHashMap<String, Set<String>> targetToSenders = new ConcurrentHashMap<>();

    public void setTarget(String sessionId, String targetUsername) {
        if (sessionId == null || targetUsername == null) {
            log.debug("setTarget failed: null params");
            return;
        }

        // Remove old target if exists
        removeTarget(sessionId);

        // Add new mapping
        sessionTargets.put(sessionId, targetUsername);

        // Update Reverse Index
        targetToSenders.computeIfAbsent(targetUsername, k -> ConcurrentHashMap.newKeySet()).add(sessionId);

        log.debug("Session [{}] is now targeting user [{}]", sessionId, targetUsername);
    }

    /**
     * Get the username that this session is currently targeting.
     */
    public String getTarget(String sessionId) {
        if (sessionId == null) {
            log.debug("getTarget failed: sessionId is null");
            return null;
        }

        return sessionTargets.get(sessionId);
    }

    /**
     * Removes the private chat target for a session (Switching to Global/Room).
     */
    public void removeTarget(String sessionId) {
        if (sessionId == null) {
            log.debug("removeTarget failed: sessionId is null");
            return;
        }

        // Get current target to clean up reverse index
        String oldTarget = sessionTargets.remove(sessionId);

        if (oldTarget != null) {
            // Remove from Reverse Index
            Set<String> senders = targetToSenders.get(oldTarget);
            if (senders != null) {
                senders.remove(sessionId);
                // Clean up empty sets to prevent memory leaks
                if (senders.isEmpty()) {
                    targetToSenders.remove(oldTarget);
                }
            }
            log.debug("Session [{}] stopped targeting user [{}]", sessionId, oldTarget);
        }
    }

    public Set<String> getSessionsTargeting(String targetUsername) {
        if (targetUsername == null) {
            return Collections.emptySet();
        }

        Set<String> targetingSessions = targetToSenders.get(targetUsername);
        if (targetingSessions == null) {
            return Collections.emptySet();
        }
        // Return unmodifiable to protect the registry
        return Collections.unmodifiableSet(targetingSessions);
    }
}
