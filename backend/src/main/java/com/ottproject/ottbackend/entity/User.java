package com.ottproject.ottbackend.entity;

import com.ottproject.ottbackend.enums.AuthProvider;
import com.ottproject.ottbackend.enums.UserRole;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 사용자 엔티티
 *
 * 큰 흐름
 * - 로컬/소셜 로그인 사용자 계정을 저장한다.
 * - 권한/활성/이메일 인증 여부 등 보안 메타를 함께 보관한다.
 * - 생성/수정 시각은 Auditing 으로 자동 관리한다.
 *
 * 필드 개요
 * - id/email/password/name: 기본 계정 정보
 * - role: 권한(USER/ADMIN)
 * - authProvider/providerId: 인증 제공자/외부 식별자
 * - emailVerified/enabled: 인증/활성 상태
 * - createdAt/updatedAt: 생성/수정 시각
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 기본 생성자
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 사용자 고유 ID (DB에서 자동 생성)

    @Column(unique = true, nullable = false)
    private String email; // 이메일 (고유값, 필수)

    @Column(nullable = true) // 소셜 로그인은 비밀번호 없음
    private String password; // 비밀번호 (자체 로그인용)

    @Column(nullable = false)
    private String name; // 사용자 이름 (필수)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER; // 사용자 권한 (기본값: USER)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider authProvider = AuthProvider.LOCAL; // 인증 제공자 (기본값: LOCAL)

    @Column(nullable = true)
    private String providerId; // 소셜 로그인 ID (소셜 로그인용)

    @Column(nullable = false)
    private boolean emailVerified = false; // 이메일 인증 여부 (기본값: false)

    @Column(nullable = false)
    private boolean enabled = true; // 계정 활성화 여부 (기본값: true)

    @Column(name = "profile_image")
    private String profileImage; // 프로필 이미지 URL(옵션)

    @CreatedDate
    @Column(nullable = false)
    private LocalDateTime createdAt; // 생성일시 (자동 생성)

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt; // 수정일시 (자동 업데이트)

    // ===== 정적 팩토리 메서드 =====
    /**
     * FK 바인딩 전용 참조 — id 만 채운 비영속 인스턴스를 만든다.
     * 연관 컬럼에 사용자 id 를 넣으려고 행 전체를 읽어오는 것을 피하는 자리에만 쓴다.
     * 나머지 필드는 비어 있으므로 이 인스턴스를 읽거나 저장 대상으로 삼지 않는다.
     *
     * @param id 사용자 PK
     */
    public static User reference(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        User user = new User();
        user.id = id;
        return user;
    }

    /**
     * 로컬 사용자 생성 (이메일/비밀번호 기반)
     *
     * @param email 이메일 (필수)
     * @param password 비밀번호 (필수)
     * @param name 사용자 이름 (필수)
     * @return 생성된 User 엔티티
     */
    public static User createLocalUser(String email, String password, String name) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("비밀번호는 필수입니다.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("사용자 이름은 필수입니다.");
        }

        User user = new User();
        user.email = normalizeEmail(email);
        user.password = password;
        user.name = name.trim();
        user.role = UserRole.USER;
        user.authProvider = AuthProvider.LOCAL;
        user.providerId = null;
        user.emailVerified = false;
        user.enabled = true;
        user.profileImage = null;
        user.createdAt = LocalDateTime.now();
        user.updatedAt = LocalDateTime.now();

        return user;
    }

    /**
     * 소셜 사용자 생성 (OAuth 기반)
     *
     * @param email 이메일 (필수)
     * @param name 사용자 이름 (필수)
     * @param authProvider 인증 제공자 (필수)
     * @param providerId 외부 제공자 ID (필수)
     * @param profileImage 프로필 이미지 URL (선택)
     * @return 생성된 User 엔티티
     */
    public static User createSocialUser(
            String email, String name, AuthProvider authProvider, String providerId, String profileImage) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("사용자 이름은 필수입니다.");
        }
        if (authProvider == null) {
            throw new IllegalArgumentException("인증 제공자는 필수입니다.");
        }
        if (providerId == null || providerId.trim().isEmpty()) {
            throw new IllegalArgumentException("제공자 ID는 필수입니다.");
        }

        User user = new User();
        user.email = normalizeEmail(email);
        user.password = null; // 소셜 로그인은 비밀번호 없음
        user.name = name.trim();
        user.role = UserRole.USER;
        user.authProvider = authProvider;
        user.providerId = providerId.trim();
        user.emailVerified = true; // 소셜 로그인은 이메일 인증 완료로 간주
        user.enabled = true;
        user.profileImage = profileImage != null ? profileImage.trim() : null;
        user.createdAt = LocalDateTime.now();
        user.updatedAt = LocalDateTime.now();

        return user;
    }

    /**
     * 관리자 사용자 생성
     *
     * @param email 이메일 (필수)
     * @param password 비밀번호 (필수)
     * @param name 사용자 이름 (필수)
     * @return 생성된 User 엔티티
     */
    public static User createAdminUser(String email, String password, String name) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("비밀번호는 필수입니다.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("사용자 이름은 필수입니다.");
        }

        User user = new User();
        user.email = normalizeEmail(email);
        user.password = password;
        user.name = name.trim();
        user.role = UserRole.ADMIN;
        user.authProvider = AuthProvider.LOCAL;
        user.providerId = null;
        user.emailVerified = true; // 관리자는 이메일 인증 완료로 간주
        user.enabled = true;
        user.profileImage = null;
        user.createdAt = LocalDateTime.now();
        user.updatedAt = LocalDateTime.now();

        return user;
    }

    // ===== 상태 전이 =====
    /**
     * 회원탈퇴 — 계정을 비활성화하고 식별 정보를 익명화한다.
     *
     * 행을 지우지 않는 이유: users 를 참조하는 외래 키가 20개 테이블(결제/구독/댓글 등)에 걸쳐 있어
     * 하드 삭제는 결제 이력까지 함께 날린다. 대신 개인정보만 지우고 행은 남긴다.
     *
     * 이메일을 바꾸는 이유: enabled=false 만으로는 email 유니크 제약이 그 주소를 영구 점유해서
     * 같은 주소로 재가입할 수 없다. 예약 TLD(.invalid, RFC 2606)라 실제 주소로 존재할 수 없고,
     * PK 를 붙여 다른 탈퇴 계정과도 충돌하지 않는다.
     *
     * providerId 도 함께 비운다. 소셜 재로그인이 이 계정을 다시 붙잡으면 탈퇴가 무효가 된다
     * (연동 행 삭제는 다른 테이블이라 서비스가 맡는다).
     */
    public void withdraw() {
        if (id == null) {
            throw new IllegalStateException("저장되지 않은 사용자는 탈퇴할 수 없습니다.");
        }
        this.email = "withdrawn-" + id + "@withdrawn.invalid";
        this.name = "탈퇴한 사용자";
        this.password = null;
        this.providerId = null;
        this.profileImage = null;
        this.emailVerified = false;
        this.enabled = false;
    }

    /**
     * 이메일 정규화 (계정 동일성 기준)
     *
     * 계정은 이 규칙으로 저장되므로(위 팩토리들), 이메일로 계정을 찾는 모든 경로(중복확인/로그인/조회)도
     * 반드시 같은 기준으로 정규화해야 한다. 규칙이 흩어져 한 경로만 원본으로 조회하면, 대소문자만 다른
     * 입력이 계정을 못 찾는 버그가 난다(로그인 실패, 중복가입 우회 등). 그래서 규칙을 여기 한 곳에 둔다.
     */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
