package io.github.amir01mn.parking.notification;

import io.github.amir01mn.parking.auth.CurrentUser;
import io.github.amir01mn.parking.common.PageResponse;
import io.github.amir01mn.parking.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "The signed-in user's in-app notifications")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService notificationService, CurrentUser currentUser) {
        this.notificationService = notificationService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "List my notifications", description = "Newest first; optionally only unread ones.")
    public PageResponse<NotificationResponse> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return notificationService.list(currentUser.id(), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Count my unread notifications")
    public NotificationResponse.UnreadCount unreadCount() {
        return new NotificationResponse.UnreadCount(notificationService.unreadCount(currentUser.id()));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark a notification read", description = "Idempotent.")
    @ApiResponse(responseCode = "404", description = "No such notification for the current user")
    public NotificationResponse markRead(@PathVariable UUID id) {
        return notificationService.markRead(currentUser.id(), id);
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all my notifications read", description = "Returns how many were unread.")
    public NotificationResponse.MarkedRead markAllRead() {
        return new NotificationResponse.MarkedRead(notificationService.markAllRead(currentUser.id()));
    }
}
