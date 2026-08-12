package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 시청 프로필 엔티티
 *
 * 큰 흐름
 * - 계정(User) 하나가 여러 시청 프로필을 가진다. 로그인 후 사용할 프로필을 고른다.
 * - 이번 단계에서 프로필이 가지는 것은 표시 이름뿐이다. 시청기록·찜·별점은 아직 계정 단위라
 *   프로필을 바꿔도 보이는 데이터는 같다.
 *
 * 필드 개요
 * - id/user: 식별/소유 계정
 * - name: 프로필 표시 이름
 * - createdAt/updatedAt: 생성·갱신 시각
 *
 * 시각을 파라미터로 받는 이유
 * - 엔티티가 현재 시각을 직접 읽으면 테스트에서 시간을 고정할 수 없다. 호출자가 넘긴다.
 * - Auditing 애노테이션을 쓰지 않는 이유도 같다. 슬라이스 테스트는 Auditing 설정을 올리지 않아
 *   NOT NULL 인 시각 컬럼이 비어 INSERT 가 깨진다.
 */
@Entity
@Table(name = "viewing_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ViewingProfile {

    /** 이름 길이 상한. 마이그레이션의 VARCHAR(20) 과 같은 값이어야 한다. */
    public static final int NAME_MAX_LENGTH = 20;

    /**
     * 계정당 프로필 상한.
     * 개수 판정 자체는 여러 행을 세야 하므로 서비스가 하고, 값은 도메인 규칙이라 여기에 둔다.
     */
    public static final int MAX_PER_ACCOUNT = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 소유 계정

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name; // 표시 이름

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 프로필 생성
     *
     * @param user 소유 계정
     * @param name 표시 이름(공백 불가, 20자 이하)
     * @param now  생성 시각
     * @throws IllegalArgumentException 소유자가 없거나 이름이 규칙에 어긋나는 경우
     */
    public static ViewingProfile create(User user, String name, LocalDateTime now) {
        if (user == null) {
            throw new IllegalArgumentException("소유 계정은 필수입니다.");
        }
        if (now == null) {
            throw new IllegalArgumentException("생성 시각은 필수입니다.");
        }
        String normalized = normalizeName(name);

        ViewingProfile profile = new ViewingProfile();
        profile.user = user;
        profile.name = normalized;
        profile.createdAt = now;
        profile.updatedAt = now;
        return profile;
    }

    /**
     * 이름 변경. 이름과 갱신 시각은 항상 함께 바뀐다.
     *
     * @param name 새 표시 이름
     * @param now  갱신 시각
     * @throws IllegalArgumentException 이름이 규칙에 어긋나는 경우
     */
    public void rename(String name, LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("갱신 시각은 필수입니다.");
        }
        this.name = normalizeName(name);
        this.updatedAt = now;
    }

    /**
     * 이 프로필이 해당 계정의 것인지 판정한다.
     *
     * @param userId 판정할 계정 식별자
     * @return 소유자가 맞으면 true
     */
    public boolean isOwnedBy(Long userId) {
        return userId != null && user != null && userId.equals(user.getId());
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("프로필 이름은 비워둘 수 없습니다.");
        }
        String trimmed = name.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("프로필 이름은 " + NAME_MAX_LENGTH + "자를 넘을 수 없습니다.");
        }
        return trimmed;
    }
}
