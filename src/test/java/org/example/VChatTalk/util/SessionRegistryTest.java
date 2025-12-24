package org.example.VChatTalk.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionRegistryTest {

    private SessionRegistry registry;

    @BeforeEach
    void setUp() {
        // Khởi tạo mới Registry trước mỗi bài test để dữ liệu sạch sẽ
        registry = new SessionRegistry();
    }

    // 1. Test vòng đời cơ bản: Set -> Get -> Remove
    @Test
    void testTargetLifecycle() {
        String sessionId = "s1";
        String targetName = "Alice";

        // Ban đầu chưa có gì
        assertNull(registry.getTarget(sessionId));

        // SET
        registry.setTarget(sessionId, targetName);
        assertEquals(targetName, registry.getTarget(sessionId), "Sau khi set, get phải ra đúng tên");

        // REMOVE
        registry.removeTarget(sessionId);
        assertNull(registry.getTarget(sessionId), "Sau khi remove, get phải ra null");
    }

    // 2. Test Reverse Lookup (Tìm ngược): Ai đang nhắm vào user này?
    @Test
    void testGetSessionsTargeting() {
        // Kịch bản: s1 và s2 cùng chat với Alice, s3 chat với Bob
        registry.setTarget("s1", "Alice");
        registry.setTarget("s2", "Alice");
        registry.setTarget("s3", "Bob");

        // Kiểm tra danh sách người đang chat với Alice
        List<String> followersAlice = registry.getSessionsTargeting("Alice");

        assertEquals(2, followersAlice.size(), "Phải có 2 người đang chat với Alice");
        assertTrue(followersAlice.contains("s1"));
        assertTrue(followersAlice.contains("s2"));

        // Kiểm tra danh sách người đang chat với Bob
        List<String> followersBob = registry.getSessionsTargeting("Bob");
        assertEquals(1, followersBob.size());
        assertTrue(followersBob.contains("s3"));

        // Kiểm tra người không ai chat cùng
        List<String> followersNobody = registry.getSessionsTargeting("Charlie");
        assertTrue(followersNobody.isEmpty());
    }

    // 3. Test check Online/Offline
    @Test
    void testIsUserOnline() {
        String sessionId = "s1";
        String username = "Alice";

        // Ban đầu offline
        assertFalse(registry.isUserOnline(username));

        // Đăng ký user (Sử dụng hàm tryRegisterUser mà bạn vừa thêm)
        registry.tryRegisterUser(sessionId, username);

        // Bây giờ phải Online
        assertTrue(registry.isUserOnline(username));

        // Check tên lung tung -> False
        assertFalse(registry.isUserOnline("Ghost"));
    }

    // 4. Test Edge Cases (Null Safety) - Quan trọng để tránh NullPointerException
    @Test
    void testEdgeCases_NullInputs() {
        // Set null -> Không được lỗi
        assertDoesNotThrow(() -> registry.setTarget(null, "Alice"));
        assertDoesNotThrow(() -> registry.setTarget("s1", null));

        // Remove null -> Không được lỗi
        assertDoesNotThrow(() -> registry.removeTarget(null));

        // Get null -> Ra null
        assertNull(registry.getTarget(null));

        // Get targeting null -> Ra list rỗng (không được null)
        List<String> result = registry.getSessionsTargeting(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // Check online null -> False
        assertFalse(registry.isUserOnline(null));
    }
}