package com.ottproject.ottbackend.entity; // 엔티티 패키지

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity // JPA 엔티티
@Table( // 테이블 매핑
        name = "ani_favorites", // 테이블명
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id","ani_list_id"}) // 동일 유저-작품 중복 방지
) // 유니크 제약
@Getter // 게터
@Setter // 세터
@Builder
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 전체 필드 생성자
@EntityListeners(AuditingEntityListener.class) // 생성 시각 자동 기록
public class AnimeFavorite { // 애니 찜 엔티티
    @Id // 기본키
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가
    private Long id; // PK

    @ManyToOne(fetch = FetchType.LAZY, optional = false) // 다대일: 사용자
    @JoinColumn(name = "user_id", nullable = false) // FK: user_id
    private User user; // 찜한 사용자

    @ManyToOne(fetch = FetchType.LAZY, optional = false) // 다대일: 애니
    @JoinColumn(name = "ani_id", nullable = false) // FK: ani_id
    private Anime anime; // 대상 애니

    @CreatedDate // 생성 시각 자동
    @Column(name = "created_at", nullable = false, updatable = false) // 불변 컬럼
    private LocalDateTime createdAt; // 찜한 시각
}