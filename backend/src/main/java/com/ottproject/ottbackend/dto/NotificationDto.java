package com.ottproject.ottbackend.dto;

import com.ottproject.ottbackend.enums.NotificationType;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 알림 DTO
 *
 * 큰 흐름
 * - 알림 정보를 API 응답으로 전달한다.
 * - 메타데이터는 파싱된 형태로 제공한다.
 *
 * 필드 개요
 * - id/type/title/content: 기본 알림 정보
 * - data: 파싱된 메타데이터 객체
 * - isRead/createdAt: 읽음 상태/생성 시각
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private Long id; // 알림 ID
    private NotificationType type; // 알림 타입
    private String title; // 알림 제목
    private String content; // 알림 내용
    private NotificationDataDto data; // 파싱된 메타데이터
    private Boolean isRead; // 읽음 여부
    private LocalDateTime createdAt; // 생성 시각
}

