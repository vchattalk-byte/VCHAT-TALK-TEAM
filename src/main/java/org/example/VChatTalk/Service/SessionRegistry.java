package org.example.VChatTalk.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {
    private final ConcurrentHashMap<String, WebSocketSession> sessions= new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(SessionRegistry.class);


    public void addSession(WebSocketSession session){
        if(session != null && session.getId()!=null){
            sessions.put(session.getId(),session);
        }
    }

    public void removeSession(String sessionId) {
        if(sessionId != null){
            sessions.remove(sessionId);
        }
    }

    public WebSocketSession findSessionById(String sessionId){
        if(sessionId == null) {
            return null;
        }
        return sessions.get(sessionId);
    }
    public Collection<WebSocketSession> getAllSessions(){
        return sessions.values();
    }
    public int getSessionCount(){
        return sessions.size();
    }
    public void logActiveSessions(){
        StringBuilder sb = new StringBuilder();
        for (WebSocketSession s : sessions.values()){
            sb.append(s.getId()).append(s.isOpen() ? "[open]":"[close]").append(" ");
        }
        logger.info("Active Sessions ({}): {} ", getSessionCount(),sb.toString().trim()); ;
    }
}
