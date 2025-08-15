package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;

/**
 * 태그 마스터 엔티티
 * - 정규화된 태그 스키마(tags)와 매핑
 * - 현재는 읽기 중심(필요 시 관리자 CRUD로 확장 가능)
 */
@Entity
@Table(name = "tags")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tag { // 태그 마스터

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // PK

    @Column(nullable = false, unique = true)
    private String name; // 태그 이름(고유)

    @Column
    private String color; // 배지 색상(선택)

    /**
     * AnimeList 와의 다대다 관계(역방향)
     * - 소유자는 AnimeList.tags(@JoinTable 사용)
     */
    @ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY)
    @Builder.Default
    private java.util.Set<AnimeList> animeLists = new HashSet<>(); // 역방향 참조
}


