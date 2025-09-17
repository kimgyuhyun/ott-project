package com.ottproject.ottbackend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 사용자 재생 설정 엔티티
 *
 * 큰 흐름
 * - 자동 스킵/기본 화질/자동 다음 화 설정을 1:1 로 보관한다.
 * - 마지막 수정 시각으로 동기화 시점을 관리한다.
 *
 * 필드 개요
 * - id/user: 식별/소유자(1:1)
 * - autoSkipIntro/autoSkipEnding/defaultQuality/autoNextEpisode: 재생 설정
 * - updatedAt: 최근 갱신 시각
 */
@Entity // 재생 설정 엔티티
@Table(name = "user_settings", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id"})) // 유니크
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class UserSettings { // 사용자 재생 설정

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) // PK
    private Long id; // PK

    @OneToOne(fetch = FetchType.LAZY, optional = false) // 1:1 사용자
    @JoinColumn(name = "user_id", nullable = false) // FK
    private User user; // 사용자

    @Column(nullable = false) // 자동 스킵
    private Boolean autoSkipIntro = true; // 오프닝 자동 스킵

    @Column(nullable = false) // 자동 스킵
    private Boolean autoSkipEnding = true; // 엔딩 자동 스킵

    @Column(nullable = false) // 기본 화질
    private String defaultQuality = "auto"; // auto|480p|720p|1080p

    @Column(nullable = false) // 다음 화 자동재생
    private Boolean autoNextEpisode = true; // 자동 다음 화

    @Column(nullable = true) // 테마 설정 (null=설정안함)
    private String theme = null; // light|dark|null

    @Column(nullable = false) // 언어 설정
    private String language = "ko"; // ko|en|ja

    @Column(nullable = false) // 알림 설정
    private Boolean notifications = true; // 알림 활성화

    @Column(nullable = false) // 관심작품 업데이트 알림
    private Boolean notificationWorkUpdates = true; // 관심작품 업데이트 알림

    @Column(nullable = false) // 커뮤니티 활동 알림
    private Boolean notificationCommunityActivity = true; // 커뮤니티 활동 알림

    @Column(nullable = false) // 자동 재생 설정
    private Boolean autoPlay = false; // 자동 재생

    @LastModifiedDate
    @Column(nullable = false) // 갱신 시각
    private LocalDateTime updatedAt; // 갱신

    // ===== 정적 팩토리 메서드 =====

    /**
     * 기본 설정 생성 (비즈니스 로직 캡슐화)
     * 
     * @param user 사용자
     * @return 생성된 UserSettings 엔티티
     * @throws IllegalArgumentException 사용자가 null인 경우
     */
    public static UserSettings createDefaultSettings(User user) {
        // 필수 필드 검증
        if (user == null) {
            throw new IllegalArgumentException("사용자는 필수입니다.");
        }

        // UserSettings 엔티티 생성
        UserSettings settings = new UserSettings();
        settings.user = user;
        settings.autoSkipIntro = true;
        settings.autoSkipEnding = true;
        settings.defaultQuality = "auto";
        settings.autoNextEpisode = true;
        settings.theme = null; // 기본값은 null
        settings.language = "ko";
        settings.notifications = true;
        settings.notificationWorkUpdates = true;
        settings.notificationCommunityActivity = true;
        settings.autoPlay = false;

        return settings;
    }

    /**
     * 커스텀 설정 생성 (비즈니스 로직 캡슐화)
     * 
     * @param user 사용자
     * @param language 언어 설정 (ko|en|ja)
     * @param theme 테마 설정 (light|dark|null)
     * @param notifications 알림 설정
     * @return 생성된 UserSettings 엔티티
     * @throws IllegalArgumentException 필수 필드가 null이거나 유효하지 않은 경우
     */
    public static UserSettings createCustomSettings(User user, String language, String theme, Boolean notifications) {
        // 필수 필드 검증
        if (user == null) {
            throw new IllegalArgumentException("사용자는 필수입니다.");
        }
        if (language == null || language.trim().isEmpty()) {
            throw new IllegalArgumentException("언어 설정은 필수입니다.");
        }
        if (!isValidLanguage(language)) {
            throw new IllegalArgumentException("지원되지 않는 언어입니다. (ko|en|ja)");
        }
        if (theme != null && !isValidTheme(theme)) {
            throw new IllegalArgumentException("지원되지 않는 테마입니다. (light|dark|null)");
        }
        if (notifications == null) {
            throw new IllegalArgumentException("알림 설정은 필수입니다.");
        }

        // UserSettings 엔티티 생성
        UserSettings settings = new UserSettings();
        settings.user = user;
        settings.autoSkipIntro = true;
        settings.autoSkipEnding = true;
        settings.defaultQuality = "auto";
        settings.autoNextEpisode = true;
        settings.theme = theme;
        settings.language = language.trim();
        settings.notifications = notifications;
        settings.notificationWorkUpdates = true;
        settings.notificationCommunityActivity = true;
        settings.autoPlay = false;

        return settings;
    }

    // ===== 비즈니스 메서드 =====

    /**
     * 언어 설정 변경
     * @param newLanguage 새로운 언어 설정
     * @throws IllegalArgumentException 언어가 유효하지 않은 경우
     */
    public void updateLanguage(String newLanguage) {
        if (newLanguage == null || newLanguage.trim().isEmpty()) {
            throw new IllegalArgumentException("언어 설정은 필수입니다.");
        }
        if (!isValidLanguage(newLanguage)) {
            throw new IllegalArgumentException("지원되지 않는 언어입니다. (ko|en|ja)");
        }

        this.language = newLanguage.trim();
    }

    /**
     * 테마 설정 변경
     * @param newTheme 새로운 테마 설정
     * @throws IllegalArgumentException 테마가 유효하지 않은 경우
     */
    public void updateTheme(String newTheme) {
        if (newTheme != null && !isValidTheme(newTheme)) {
            throw new IllegalArgumentException("지원되지 않는 테마입니다. (light|dark|null)");
        }

        this.theme = newTheme;
    }

    /**
     * 알림 설정 변경
     * @param newNotifications 새로운 알림 설정
     * @throws IllegalArgumentException 알림 설정이 null인 경우
     */
    public void updateNotifications(Boolean newNotifications) {
        if (newNotifications == null) {
            throw new IllegalArgumentException("알림 설정은 필수입니다.");
        }

        this.notifications = newNotifications;
    }

    /**
     * 자동 스킵 설정 변경
     * @param autoSkipIntro 오프닝 자동 스킵
     * @param autoSkipEnding 엔딩 자동 스킵
     * @throws IllegalArgumentException 설정값이 null인 경우
     */
    public void updateAutoSkipSettings(Boolean autoSkipIntro, Boolean autoSkipEnding) {
        if (autoSkipIntro == null) {
            throw new IllegalArgumentException("오프닝 자동 스킵 설정은 필수입니다.");
        }
        if (autoSkipEnding == null) {
            throw new IllegalArgumentException("엔딩 자동 스킵 설정은 필수입니다.");
        }

        this.autoSkipIntro = autoSkipIntro;
        this.autoSkipEnding = autoSkipEnding;
    }

    /**
     * 화질 설정 변경
     * @param newQuality 새로운 화질 설정
     * @throws IllegalArgumentException 화질이 유효하지 않은 경우
     */
    public void updateDefaultQuality(String newQuality) {
        if (newQuality == null || newQuality.trim().isEmpty()) {
            throw new IllegalArgumentException("화질 설정은 필수입니다.");
        }
        if (!isValidQuality(newQuality)) {
            throw new IllegalArgumentException("지원되지 않는 화질입니다. (auto|480p|720p|1080p)");
        }

        this.defaultQuality = newQuality.trim();
    }

    // ===== 유틸리티 메서드 =====

    /**
     * 유효한 언어인지 검증
     * @param language 언어 코드
     * @return 유효한 언어 여부
     */
    private static boolean isValidLanguage(String language) {
        return "ko".equals(language) || "en".equals(language) || "ja".equals(language);
    }

    /**
     * 유효한 테마인지 검증
     * @param theme 테마 코드
     * @return 유효한 테마 여부
     */
    private static boolean isValidTheme(String theme) {
        return "light".equals(theme) || "dark".equals(theme) || theme == null;
    }

    /**
     * 유효한 화질인지 검증
     * @param quality 화질 코드
     * @return 유효한 화질 여부
     */
    private static boolean isValidQuality(String quality) {
        return "auto".equals(quality) || "480p".equals(quality) || 
               "720p".equals(quality) || "1080p".equals(quality);
    }
}