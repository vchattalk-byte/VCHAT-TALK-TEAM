package org.example.VChatTalk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageDTO {
    // message type: JOIN, MESSAGE, LEAVE
    @JsonProperty("type")
    private MessageType type;

    @JsonProperty("sender")
    private String sender;

    @JsonProperty("content")
    private String content;

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("roomId")
    private String roomId;

}
