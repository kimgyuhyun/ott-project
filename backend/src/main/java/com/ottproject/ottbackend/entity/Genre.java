package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 장르 엔티티
 *
 * 큰 흐름
 * - 작품이 소속되는 장르 마스터를 관리한다.
 * - Anime 와 다대다 연관을 맺는다.
 *
 * 필드 개요
 * - id/name/description/color/isActive: 마스터 속성
 * - createdAt/updatedAt: 생성/수정 시각
 * - animes: 역방향 연관(다대다)
 */

@Entity
@Table(name = "genres")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Genre {

    @Id // 기본키 지정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략
    private Long id; // 장르 고유 ID

    @Column(nullable = false, unique = true) // null 불허, 고유값
    private String name; // 장르명 (예: 액션, 로맨스, 판타지등

    @Column(nullable = true) // null 허용
    private String description; // 장르 설명

    @Column(nullable = false)
    private String color; // 장르 색상 (UI 에서 사용)

    @Column(nullable = false)
    private Boolean isActive; // 활성화 여부

    @CreatedDate // 생성일시 자동 설정
    @Column(nullable = false)
    @Builder.Default // 빌더 패턴에서 기본값 설정
    private LocalDateTime createdAt = LocalDateTime.now(); // 생성일시

    @LastModifiedDate // 수정일시 자동 업데이트
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now(); // 수정일시

    // ===== 연관관계 매핑

    /**
     * Anime 와의 다대다 관계
     * 하나의 장르는 여러 애니에 속할 수 있고, 하나의 애니는 여러 장르를 가질 수 있음
     * mappedBy: Anime 엔티티에서 관리하는 관계 필드명
     */
    @ManyToMany(mappedBy = "genres", fetch = FetchType.LAZY) // 다대다 관계, 지연 로딩
    @Builder.Default
    private Set<Anime> animes = new HashSet<>(); // 해당 장르를 가진 애니 목록
}
