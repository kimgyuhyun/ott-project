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
 * 제작사(Studio) 엔티티
 *
 * 큰 흐름
 * - 작품 제작사 마스터를 관리한다.
 * - Anime 과 다대다 연관을 맺는다.
 *
 * 필드 개요
 * - id/name/nameEn/nameJp: 식별/다국어 명칭
 * - description/logoUrl/websiteUrl: 소개/브랜드/링크
 * - country/isActive: 운영 메타
 * - createdAt/updatedAt: 생성/수정 시각
 * - animes: 역방향 연관(다대다)
 */
@Entity
@Table(name = "studios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Studio {

    @Id // 기본키 설정
    @GeneratedValue(strategy = GenerationType.IDENTITY) // 자동 증가 전략
    private Long id; // 제작사 고유 ID (DB에서 자동 생성)

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
    private LocalDateTime createdAt; // 생성일시

    @LastModifiedDate // 수정일시 자동 업데이트
    @Column(nullable = false)
    private LocalDateTime updatedAt; // 수정 일시

    // ===== 연관관계 매핑 =====

    /**
     * Anime 와의 다대다 관계
     * 하나의 제작사는 여러 애니를 제작할 수 있고 하나의 애니는 여러 제작사가 참여할수있음
     * mappedBy : Anime 엔티티에서 관리하는 관계 필드명
     */
    @ManyToMany(mappedBy = "studios", fetch = FetchType.LAZY) // 다대다 관계, 지연로딩
    private Set<Anime> animes = new HashSet<>(); // 해당 제작사가 제작한 애니 목록
    
    // ===== 정적 팩토리 메서드 =====
    /**
     * 제작사 생성
     * 
     * @param name 제작사명 (필수)
     * @param nameEn 영어명 (선택)
     * @param nameJp 일본어명 (선택)
     * @param description 설명 (선택)
     * @param logoUrl 로고 URL (선택)
     * @param websiteUrl 웹사이트 URL (선택)
     * @param country 제작국 (필수)
     * @return 생성된 Studio 엔티티
     */
    public static Studio createStudio(String name, String nameEn, String nameJp, 
                                    String description, String logoUrl, String websiteUrl, String country) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("제작사명은 필수입니다.");
        }
        if (country == null || country.trim().isEmpty()) {
            throw new IllegalArgumentException("제작국은 필수입니다.");
        }
        
        Studio studio = new Studio();
        studio.name = name.trim();
        studio.nameEn = nameEn != null ? nameEn.trim() : null;
        studio.nameJp = nameJp != null ? nameJp.trim() : null;
        studio.description = description != null ? description.trim() : "";
        studio.logoUrl = logoUrl != null ? logoUrl.trim() : null;
        studio.websiteUrl = websiteUrl != null ? websiteUrl.trim() : null;
        studio.country = country.trim();
        studio.isActive = true;
        studio.createdAt = LocalDateTime.now();
        studio.updatedAt = LocalDateTime.now();
        
        return studio;
    }
}
