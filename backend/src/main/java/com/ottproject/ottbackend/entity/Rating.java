package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 사용자의 애니메이션 평점을 저장하는 엔티티
 */
@Entity
@Table(name = "ratings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략
    private Long id;

    @Column(nullable = false)
    private Double score; // 평점(0.0 ~ 5.0)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // 평점을 남긴 사용자

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ani_id") // 통합 애니 FK
    private Anime anime; // 평점이 달린 애니

    
    @CreatedDate
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(); // 등록 일시

    @LastModifiedDate
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now(); // 수정 일시
}
