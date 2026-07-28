package com.jetmenu.notification;

import com.jetmenu.auth.AuthHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthHelper authHelper;

    public NotificationController(NotificationService notificationService, AuthHelper authHelper) {
        this.notificationService = notificationService;
        this.authHelper = authHelper;
    }

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> findAll(Authentication auth, @PageableDefault(size = 20) Pageable pageable) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(notificationService.findAll(merchantId, pageable));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(merchantId)));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(Authentication auth, @PathVariable UUID id) {
        UUID merchantId = authHelper.getMerchantId(auth);
        notificationService.markRead(merchantId, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> dismiss(Authentication auth, @PathVariable UUID id) {
        UUID merchantId = authHelper.getMerchantId(auth);
        notificationService.dismiss(merchantId, id);
        return ResponseEntity.noContent().build();
    }
}
