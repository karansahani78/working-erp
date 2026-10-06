package com.educationerp.portal;

import com.educationerp.communication.CommunicationDtos;
import com.educationerp.communication.CommunicationService;
import com.educationerp.common.api.ApiResponse;
import com.educationerp.common.api.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The inbox, shared by all three portals.
 *
 * <p>There is no recipient in any path here. The inbox is whatever belongs to the signed-in
 * account, so a message addressed to somebody else cannot be opened by guessing its id: the
 * lookup is by id *and* recipient.
 */
@RestController
@RequestMapping("/api/v1/portal/notifications")
@Tag(name = "Notifications")
@RequiredArgsConstructor
public class NotificationPortalController {

    private final CommunicationService communication;
    private final PortalService portals;

    @GetMapping
    public ApiResponse<PageResponse<CommunicationDtos.NotificationResponse>> inbox(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(communication.inbox(unreadOnly, pageable));
    }

    /** Notices for the caller's own portal, whichever kind of account it is. */
    @GetMapping("/notices")
    public ApiResponse<List<PortalDtos.NoticeRow>> notices() {
        return ApiResponse.ok(portals.myNotices());
    }

    @GetMapping("/unread-count")
    public ApiResponse<CommunicationDtos.UnreadCountResponse> unreadCount() {
        return ApiResponse.ok(communication.unreadCount());
    }

    @PostMapping("/{id}/read")
    public ApiResponse<CommunicationDtos.NotificationResponse> markRead(@PathVariable UUID id) {
        return ApiResponse.ok(communication.markRead(id));
    }

    @PostMapping("/read-all")
    public ApiResponse<CommunicationDtos.UnreadCountResponse> markAllRead() {
        return ApiResponse.ok(communication.markAllRead());
    }
}
