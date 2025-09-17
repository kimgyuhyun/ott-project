package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.NotificationDto;
import com.ottproject.ottbackend.service.NotificationService;
import com.ottproject.ottbackend.util.SecurityUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * NotificationController
 *
 * 큰 흐름
 * - 사용자 알림 조회 및 관리 API를 제공한다.
 * - 읽지 않은 알림 개수 조회, 알림 목록 조회, 읽음 처리 등을 지원한다.
 *
 * 엔드포인트 개요
 * - GET /api/notifications: 알림 목록 조회
 * - GET /api/notifications/unread-count: 읽지 않은 알림 개수
 * - PUT /api/notifications/{id}/read: 개별 알림 읽음 처리
 * - PUT /api/notifications/read-all: 전체 알림 읽음 처리
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final SecurityUtil securityUtil;

    /**
     * 알림 목록 조회
     */
    @Operation(summary = "알림 목록 조회", description = "현재 로그인 사용자의 알림 목록을 페이징으로 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<Page<NotificationDto>> getNotifications(
            @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size,
            HttpSession session) {
        
        Long userId = securityUtil.requireCurrentUserId(session);
        Pageable pageable = PageRequest.of(page, size);
        
        Page<NotificationDto> notifications = notificationService.getNotifications(userId, pageable);
        return ResponseEntity.ok(notifications);
    }

    /**
     * 읽지 않은 알림 개수 조회
     */
    @Operation(summary = "읽지 않은 알림 개수", description = "현재 로그인 사용자의 읽지 않은 알림 개수를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount(HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(count);
    }

    /**
     * 개별 알림 읽음 처리
     */
    @Operation(summary = "알림 읽음 처리", description = "특정 알림을 읽음 처리합니다.")
    @ApiResponse(responseCode = "204", description = "처리 완료")
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @Parameter(description = "알림 ID") @PathVariable Long id,
            HttpSession session) {
        
        Long userId = securityUtil.requireCurrentUserId(session);
        notificationService.markAsRead(userId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 전체 알림 읽음 처리
     */
    @Operation(summary = "전체 알림 읽음 처리", description = "현재 로그인 사용자의 모든 알림을 읽음 처리합니다.")
    @ApiResponse(responseCode = "204", description = "처리 완료")
    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(HttpSession session) {
        Long userId = securityUtil.requireCurrentUserId(session);
        notificationService.markAllAsRead(userId);
        return ResponseEntity.noContent().build();
    }
}
