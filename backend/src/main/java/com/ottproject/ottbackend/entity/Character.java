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
 * 캐릭터 엔티티
 *
 * 큰 흐름
 * - 애니메이션 캐릭터 정보를 저장한다.
 * - 애니메이션과 다대다, 성우와 다대다 관계를 가진다.
 * - Auditing으로 생성/수정 시각을 관리한다.
 *
 * 필드 개요
 * - id/name/nameEn/nameJp: 식별/다국어 이름
 * - imageUrl/description: 이미지/설명
 * - isActive/createdAt/updatedAt: 운영/감사 정보
 */
@Entity
@Table(name = "characters")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Character {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 캐릭터 고유 ID (DB에서 자동 생성)

    @Column(nullable = false, unique = true)
    private String name; // 캐릭터 이름 (한글)

    @Column(nullable = true)
    private String nameEn; // 캐릭터 이름 (영어)

    @Column(nullable = true)
    private String nameJp; // 캐릭터 이름 (일본어)

    @Column(nullable = true)
    private String imageUrl; // 캐릭터 이미지 URL

    @Column(columnDefinition = "TEXT")
    private String description; // 캐릭터 설명

    @Column(nullable = false)
    private Boolean isActive = true; // 활성화 여부

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt; // 생성일시

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt; // 수정일시

    // ===== 애니메이션 연관 =====
    @ManyToMany(mappedBy = "characters", fetch = FetchType.LAZY)
    private Set<Anime> animes = new HashSet<>(); // 캐릭터가 나온 애니메이션 목록

    // ===== 성우 연관 =====
    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
        name = "character_voice_actors", // 조인 테이블
        joinColumns = @JoinColumn(name = "character_id", referencedColumnName = "id"), // 현재 FK
        inverseJoinColumns = @JoinColumn(name = "voice_actor_id", referencedColumnName = "id") // 대상 FK
    )
    private Set<VoiceActor> voiceActors = new HashSet<>(); // 캐릭터를 연기한 성우 목록

    // ===== 편의 메서드 =====
    public void addAnime(Anime anime) {
        this.animes.add(anime);
        anime.getCharacters().add(this);
    }

    public void removeAnime(Anime anime) {
        this.animes.remove(anime);
        anime.getCharacters().remove(this);
    }

    public void addVoiceActor(VoiceActor voiceActor) {
        this.voiceActors.add(voiceActor);
        voiceActor.getCharacters().add(this);
    }

    public void removeVoiceActor(VoiceActor voiceActor) {
        this.voiceActors.remove(voiceActor);
        voiceActor.getCharacters().remove(this);
    }
    
    // ===== 정적 팩토리 메서드 =====
    /**
     * 캐릭터 생성
     * 
     * @param name 캐릭터 이름 (필수)
     * @param nameEn 영어 이름 (선택)
     * @param nameJp 일본어 이름 (선택)
     * @param imageUrl 이미지 URL (선택)
     * @param description 설명 (선택)
     * @return 생성된 Character 엔티티
     */
    public static Character createCharacter(String name, String nameEn, String nameJp, 
                                          String imageUrl, String description) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("캐릭터 이름은 필수입니다.");
        }
        
        Character character = new Character();
        character.name = name.trim();
        character.nameEn = nameEn != null ? nameEn.trim() : null;
        character.nameJp = nameJp != null ? nameJp.trim() : null;
        character.imageUrl = imageUrl != null ? imageUrl.trim() : null;
        character.description = description != null ? description.trim() : null;
        character.isActive = true;
        character.createdAt = LocalDateTime.now();
        character.updatedAt = LocalDateTime.now();
        
        return character;
    }
}
