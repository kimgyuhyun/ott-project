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
     * Anime 와의 다대다 관계(역방향) // NEW
     * - 소유자는 Anime.tags(@JoinTable 사용)
     */
    @ManyToMany(mappedBy = "tags", fetch = FetchType.LAZY)
    @Builder.Default
    private java.util.Set<Anime> animes = new HashSet<>(); // 역방향 참조
}


