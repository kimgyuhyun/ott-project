package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * 구독 플랜 엔티티
 * - 코드/표기명/허용 최대 화질
 */
@Entity
@Table(name = "plans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plan { // 구독 플랜
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // PK

    @Column(nullable = false, unique = true)
    private String code; // e.g., FREE, BASIC, PREMIUM

    @Column(nullable = false)
    private String name; // 표기명

    @Column(nullable = false)
    private String maxQuality; // "720p" / "1080p"
}


