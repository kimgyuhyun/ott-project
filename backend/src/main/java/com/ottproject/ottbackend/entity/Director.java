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
 * 감독 엔티티
 *
 * 큰 흐름
 * - 애니메이션 감독 정보를 저장한다.
 * - 애니메이션과 다대다 관계를 가진다.
 * - Auditing으로 생성/수정 시각을 관리한다.
 *
 * 필드 개요
 * - id/name/nameEn/nameJp: 식별/다국어 이름
 * - profileUrl/description: 프로필/설명
 * - isActive/createdAt/updatedAt: 운영/감사 정보
 */
@Entity
@Table(name = "directors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Director {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 감독 고유 ID (DB에서 자동 생성)

    @Column(nullable = false, unique = true)
    private String name; // 감독 이름 (한글)

    @Column(nullable = true)
    private String nameEn; // 감독 이름 (영어)

    @Column(nullable = true)
    private String nameJp; // 감독 이름 (일본어)

    @Column(nullable = true)
    private String profileUrl; // 프로필 이미지 URL

    @Column(columnDefinition = "TEXT")
    private String description; // 감독 설명

    @Column(nullable = false)
    private Boolean isActive = true; // 활성화 여부

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt; // 생성일시

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt; // 수정일시

    // ===== 애니메이션 연관 =====
    @ManyToMany(mappedBy = "directors", fetch = FetchType.LAZY)
    private Set<Anime> animes = new HashSet<>(); // 감독이 만든 애니메이션 목록

    // ===== 편의 메서드 =====
    public void addAnime(Anime anime) {
        this.animes.add(anime);
        anime.getDirectors().add(this);
    }

    public void removeAnime(Anime anime) {
        this.animes.remove(anime);
        anime.getDirectors().remove(this);
    }
    
    // ===== Getter 메서드 =====
    public Set<Anime> getAnimes() {
        return animes;
    }
    
    // ===== 정적 팩토리 메서드 =====
    /**
     * 감독 생성
     * 
     * @param name 감독 이름 (필수)
     * @param nameEn 영어 이름 (선택)
     * @param nameJp 일본어 이름 (선택)
     * @param profileUrl 프로필 URL (선택)
     * @param description 설명 (선택)
     * @return 생성된 Director 엔티티
     */
    public static Director createDirector(String name, String nameEn, String nameJp, 
                                        String profileUrl, String description) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("감독 이름은 필수입니다.");
        }
        
        Director director = new Director();
        director.name = name.trim();
        director.nameEn = nameEn != null ? nameEn.trim() : null;
        director.nameJp = nameJp != null ? nameJp.trim() : null;
        director.profileUrl = profileUrl != null ? profileUrl.trim() : null;
        director.description = description != null ? description.trim() : null;
        director.isActive = true;
        director.createdAt = LocalDateTime.now();
        director.updatedAt = LocalDateTime.now();
        
        return director;
    }
}
