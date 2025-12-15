package org.example.VChatTalk.Service;

import org.example.VChatTalk.config.WebSocketConfig;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class SessionRegistry {
    private ConcurrentHashMap<String, WebSocketSession> sessions= new ConcurrentHashMap<>();

    public void addSession(WebSocketSession session){
        if(session != null && session.getId()!=null){
            sessions.put(session.getId(),session);
        }
    }

    public void removeSessions(String sessionId) {
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
        StringBuilder sb = new StringBuilder("Active Sessions (" + getSessionCount()+ "): ");
        for (WebSocketSession s : sessions.values()){
            sb.append(s.getId()).append(s.isOpen() ? "[open]":"[close]").append(" ");
        }
        System.out.println(sb.toString().trim()) ;
    }
}
