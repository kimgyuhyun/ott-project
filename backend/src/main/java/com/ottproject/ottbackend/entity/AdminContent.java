package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Admin 관리 컨텐츠(FAQ/혜택/CTA) 2단계용 공통 엔티티
 *
 * 큰 흐름
 * - 운영자가 DB로 FAQ/혜택/CTA를 관리한다.
 * - locale/position/published로 노출 제어 및 정렬한다.
 * - CTA는 actionText/actionUrl 로 버튼 렌더링을 지원한다.
 *
 * 필드 개요
 * - id: PK
 * - type: 컨텐츠 유형(FAQ|BENEFIT|CTA)
 * - locale: 언어 코드(ko|en)
 * - position: 노출 순서(오름차순)
 * - published: 공개 여부
 * - title: 제목/질문/CTA 타이틀
 * - content: 본문/답변/설명
 * - actionText: CTA 버튼 텍스트(옵션)
 * - actionUrl: CTA 버튼 URL(옵션)
 * - createdAt/updatedAt: 생성/수정 시각(JPA Auditing)
 */
@Entity
@Table(name = "admin_contents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AdminContent { // 엔티티 시작

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // PK

    @Column(nullable = false, length = 32)
    private String type; // 컨텐츠 유형

    @Column(nullable = false, length = 8)
    private String locale; // 언어 코드

    @Column(nullable = false)
    private Integer position; // 노출 순서(정렬)

    @Column(nullable = false)
    private boolean published; // 공개 여부 플래그

    @Column(nullable = false, length = 128)
    private String title; // 제목/질문/타이틀

    @Column(columnDefinition = "TEXT")
    private String content; // 본문/답변/설명

    @Column(length = 128)
    private String actionText; // CTA 버튼 텍스트

    @Column(length = 512)
    private String actionUrl; // CTA 버튼 URL

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt; // 생성 시각

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt; // 수정 시각
}


