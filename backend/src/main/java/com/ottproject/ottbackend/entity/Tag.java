package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;

/**
 * 태그 마스터 엔티티
 *
 * 큰 흐름
 * - 작품에 부여되는 태그 마스터를 관리한다.
 * - Anime 과 다대다 연관을 맺으며, 소유자는 Anime 쪽이다.
 *
 * 필드 개요
 * - id/name/color: 식별/명칭/색상
 * - animes: 역방향 연관(다대다)
 */
@Entity
@Table(name = "tags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Tag { // 태그 마스터

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // PK (DB에서 자동 생성)

    @Column(nullable = false, unique = true)
    private String name; // 태그 이름(고유)

    @Column
    private String color; // 배지 색상(선택)
    
    @Column(nullable = false)
    private Boolean isActive = true; // 활성화 여부

    /**
     * Anime 와의 다대다 관계(역방향) // NEW
     * - 소유자는 Anime.tags(@JoinTable 사용)
     */
    @ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY)
    private java.util.Set<Anime> animes = new HashSet<>(); // 역방향 참조
    
    // ===== Getter 메서드 =====
    public String getName() {
        return name;
    }
    
    // ===== 정적 팩토리 메서드 =====
    /**
     * 태그 생성
     * 
     * @param name 태그명 (필수)
     * @param color 태그 색상 (선택)
     * @return 생성된 Tag 엔티티
     */
    public static Tag createTag(String name, String color) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("태그명은 필수입니다.");
        }
        
        Tag tag = new Tag();
        tag.name = name.trim();
        tag.color = color != null ? color.trim() : null;
        tag.isActive = true;
        
        return tag;
    }
}