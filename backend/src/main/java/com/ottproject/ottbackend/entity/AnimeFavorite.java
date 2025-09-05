package com.ottproject.ottbackend.entity; // 엔티티 패키지

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 애니 보고싶다 엔티티
 *
 * 큰 흐름
 * - 사용자와 애니 간의 보고싶다(보관함) 관계를 보관한다.
 * - 동일 사용자-작품 한 건만 허용(복합 유니크)으로 중복 추가를 방지한다.
 *
 * 필드 개요
 * - id: PK
 * - user/anime: 소유 사용자/대상 애니
 * - createdAt: 보고싶다 추가 시각(Auditing)
 */

@Entity // JPA 엔티티
@Table( // 테이블 매핑
        name = "ani_favorites", // 테이블명
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id","ani_id"}) // 동일 유저-작품 중복 방지
) // 유니크 제약
@Getter // 게터
@Setter // 세터
@NoArgsConstructor // 기본 생성자
@AllArgsConstructor // 전체 필드 생성자
@EntityListeners(AuditingEntityListener.class) // 생성 시각 자동 기록
public class AnimeFavorite { // 애니 보고싶다 엔티티
    @Id // 기본키
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가
    private Long id; // PK

    @ManyToOne(fetch = FetchType.LAZY, optional = false) // 다대일: 사용자
    @JoinColumn(name = "user_id", nullable = false) // FK: user_id
    private User user; // 보고싶다 추가한 사용자

    @ManyToOne(fetch = FetchType.LAZY, optional = false) // 다대일: 애니
    @JoinColumn(name = "ani_id", nullable = false) // FK: ani_id
    private Anime anime; // 대상 애니

    @CreatedDate // 생성 시각 자동
    @Column(name = "created_at", nullable = false, updatable = false) // 불변 컬럼
    private LocalDateTime createdAt; // 보고싶다 추가한 시각

    // ===== 정적 팩토리 메서드 =====

    /**
     * 애니메이션 즐겨찾기 생성 (비즈니스 로직 캡슐화)
     * 
     * @param user 사용자
     * @param anime 애니메이션
     * @return 생성된 AnimeFavorite 엔티티
     * @throws IllegalArgumentException 필수 필드가 null인 경우
     */
    public static AnimeFavorite createFavorite(User user, Anime anime) {
        // 필수 필드 검증
        if (user == null) {
            throw new IllegalArgumentException("사용자는 필수입니다.");
        }
        if (anime == null) {
            throw new IllegalArgumentException("애니메이션은 필수입니다.");
        }

        // AnimeFavorite 엔티티 생성
        AnimeFavorite favorite = new AnimeFavorite();
        favorite.user = user;
        favorite.anime = anime;

        return favorite;
    }
}