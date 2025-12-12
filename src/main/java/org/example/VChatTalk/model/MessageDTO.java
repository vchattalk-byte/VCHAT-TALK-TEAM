package org.example.VChatTalk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Data Transfer Object for WebSocket Messages
 */
@Data                   // Lombok: Tự tạo Getter, Setter, toString, equals, hashCode
@NoArgsConstructor      // Lombok: Constructor không tham số (Bắt buộc cho Jackson)
@AllArgsConstructor     // Lombok: Constructor full tham số
@Builder                // Lombok: Design Pattern Builder cho dễ khởi tạo
public class MessageDTO {
    // loại tin nhắn: JOIN, MESSAGE, LEAVE
    @JsonProperty("type")
    private MessageType type;

    // Tên người gửi hoặc User ID
    @JsonProperty("sender")
    private String sender;

    // Nội dung chat (Có thể null nếu type là JOIN/LEAVE)
    @JsonProperty("content")
    private String content;

    // Thời gian theo UTC: Instant <-> ISO-8601 String ("2025-12-11T13:00:00Z")
    @JsonProperty("timestamp")
    private Instant timestamp;

    // ID phòng chat
    @JsonProperty("roomId")
    private String roomId;

}
