package org.example.VChatTalk.model;

import java.io.IOException;

public interface ChatSession {
    String getId();
    String getUsername() throws IOException;
    boolean isOpen();
    void sendMessage(MessageDTO message) throws IOException;
}