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
 * 애니메이션 제작사 정보를 제장하는 엔티티
 * 라프텔의 제작사 정보 기준
 */
@Entity
@Table(name = "studios")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Studio {

    @Id // 기본키 설정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략
    private Long id; // 제작사 고유 ID

    @Column(nullable = false, unique = true) // null 불허, 고유값
    private String name; // 제작사명

    @Column(nullable = true) // null 허용
    private String nameEn; // 제작사명 (영어)

    @Column(nullable = true)
    private String nameJp; // 제작사명 (일본어)

    @Column(length = 1000)
    private String description;

    @Column(nullable = true)
    private String logoUrl; // 로고 이미지 URL

    @Column(nullable = true)
    private String websiteUrl; // 공식 웹사이트 URL

    @Column(nullable = false)
    private String country; // 제작사 쇚국

    @Column(nullable = false)
    private Boolean isActive; // 활성화 여부

    @CreatedDate // 생성일시 자동 설정
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now(); // 생성일시

    @LastModifiedDate // 수정일시 자동 업데이트
    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now(); // 수정 일시

    // ===== 연관관계 매핑 =====

    /**
     * AMniList 와의 다대다 관계
     * 하나의 제작사는 여러 애니를 제작할 수 있고 하나의 애니는 여러 제작사가 참여할수있음
     * mappedBy : AnimeList 엔티티에서 관리하는 관계 필드명
     */
    @ManyToMany(mappedBy = "studios", fetch = FetchType.LAZY) // 다대다 관계, 지연로딩
    @Builder.Default
    private Set<AnimeList> animeLists = new HashSet<>(); // 해당 제작사가 제작한 애니 목록

}
