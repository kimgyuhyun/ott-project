package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.entity.SocialAccount;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.AuthProvider;
import com.ottproject.ottbackend.enums.UserRole;
import com.ottproject.ottbackend.repository.SocialAccountRepository;
import com.ottproject.ottbackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * OAuth2UserService
 *
 * 큰 흐름
 * - OAuth2 제공자에서 받은 사용자 정보를 표준 사용자로 변환/연동한다.
 *
 * 메서드 개요
 * - loadUser: 사용자 정보 로드 및 처리(연동/생성)
 * - extractEmail/extractName/extractProviderId: 제공자별 속성 파싱
 * - processOAuth2User: 사용자 연동/생성 처리
 * - createOAuth2User: Spring Security 호환 OAuth2User 생성
 */
@Slf4j // Lombok 로깅 어노테이션 - log 객체 자동 생성
@Service // Spring Bean으로 등록
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성
public class OAuth2UserService extends DefaultOAuth2UserService { // DefaultOAuth2UserService 를 상속받아 커스터마이징

    private final UserRepository userRepository; // 사용자 Repository 주입 (final 로 선언하여 생성자 주입)
    private final SocialAccountRepository socialAccountRepository; // 소셜 연동 리포지토리
    // 요청 처리 중 신규 사용자 생성 여부를 전달하기 위한 ThreadLocal 플래그
    private final ThreadLocal<Boolean> isNewUserFlag = new ThreadLocal<>();

    /**
     * OAuth2 사용자 정보를 로드하고 처리하는 메서드
     * 소셜 로그인 제공자로부터 받은 사용자 정보를 처리하여
     * 애플리케이션에서 사용할 수 있는 형태로 변환
     *
     * @param userRequest OAuth2 사용자 요청 정보
     * @return 처리된 OAuth2User 객체
     * @throws OAuth2AuthenticationException OAuth2 인증 예외
     */
    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException { // OAuth2 인증 예외를 던질 수 있음
        try {
            log.info("OAuth2 사용자 정보 로드 시작 - Provider: {}", userRequest.getClientRegistration().getRegistrationId()); // 로그 출력 - 요청 시작을 알림

            // 기본 OAuth2UserService를 통해 사용자 정보 로드
            OAuth2User oAuth2User = super.loadUser(userRequest); // 부모 클래스의 loadUser 메서드 호출하여 기본 사용자 정보 로드
            log.info("OAuth2 사용자 정보 로드 완료 - Attributes: {}", oAuth2User.getAttributes()); // 로그 출력 - 받은 정보 확인

            // 소셜 로그인 제공자 정보 추출
            String provider = userRequest.getClientRegistration().getRegistrationId(); // 등록된 클라이언트 ID (google, kakao, naver)
            AuthProvider authProvider = AuthProvider.valueOf(provider.toUpperCase()); // String 을 enum 으로 변환 (GOOGLE, KAKAO, NAVER)

            // OAuth2 사용자 정보에서 필요한 데이터 추출
            // 각 소셜 로그인 제공자의 응답 구조가 다르므로 제공자별로 처리
            String email = extractEmail(oAuth2User.getAttributes(), provider); // 이메일 추출
            String name = extractName(oAuth2User.getAttributes(), provider); // 이름 추출
            String providerId = extractProviderId(oAuth2User.getAttributes(), provider); // 소셜 로그인 제공자에서의 고유 ID 추출
            boolean emailVerified = extractEmailVerified(oAuth2User.getAttributes(), provider); // 제공자의 이메일 검증 여부

            // 추출된 정보를 로그로 출력
            log.info("OAuth2 사용자 정보 추출 - Email: {}, Name: {}, Provider: {}, EmailVerified: {}", email, name, providerId, emailVerified); // 로그 출력 - 추출된 정보 확인

            // [#6] 이메일 미제공 차단: 임시 이메일을 만들지 않고 로그인을 실패시킨다.
            // (이메일 동의를 받지 않으면 비밀번호 찾기/중복 식별이 불가능한 깨진 계정이 생기므로)
            if (email == null || email.isBlank()) {
                log.warn("OAuth2 로그인 차단 - 이메일 미제공 (Provider: {})", provider);
                throw new OAuth2AuthenticationException(new OAuth2Error("email_required"),
                        "이메일 제공에 동의해야 로그인할 수 있습니다.");
            }

            // 사용자 정보 처리 (기존 사용자 조회 또는 신규 사용자 생성)
            // 같은 이메일이라도 다른 소셜 로그인으로 가입한 경우를 구분하기 위해 인증 제공자도 함께 확인
            // 기본값
            isNewUserFlag.set(Boolean.FALSE);
            User user = processOAuth2User(email, name, providerId, authProvider, emailVerified); // 사용자 정보 처리 메서드 호출

            // OAuth2User 객체 생성 및 반환 (Spring Security에서 사용할 수 있는 형태로 반환)
            return createOAuth2User(oAuth2User, user); // OAuth2User 객체 생성 메서드 호출

        } catch (Exception e) {
            log.error("OAuth2 사용자 정보 로드 중 오류 발생 - Provider: {}, Error: {}",
                    userRequest.getClientRegistration().getRegistrationId(), e.getMessage(), e); // 에러 로그 출력
            throw e; // 예외를 다시 던져서 OAuth2FailureHandler에서 처리하도록 함
        } finally {
            // ThreadLocal 누수 방지
            isNewUserFlag.remove();
        }
    }

    /**
     * 소셜 로그인 제공자별 이메일 추출
     * 각 소셜 로그인 제공자의 응답 구조가 다르므로 제공자별로 처리
     *
     * @param attributes 소셜 로그인 제공자에서 받은 사용자 정보 (Map 형태)
     * @param provider 소셜 로그인 제공자 (google, kakao, naver)
     * @return 추출된 이메일
     */
    private String extractEmail(Map<String, Object> attributes, String provider) { // private 메서드 - 클래스 내부에서만 사용
        // [#6] 임시 이메일을 생성하지 않는다. 이메일을 받지 못하면 null 을 반환하고 호출부에서 로그인을 실패시킨다.
        try {
            switch (provider.toLowerCase()) { // 소셜 로그인 제공자를 소문자로 변환하여 비교
                case "google": // Google 은 바로 email 필드에 이메일이 있음
                    return (String) attributes.get("email");
                case "kakao": // Kakao 는 kakao_account 안의 email 필드에서 이메일 추출
                    Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
                    return kakaoAccount == null ? null : (String) kakaoAccount.get("email");
                case "naver": // Naver 는 response 객체 안에 정보가 있음
                    Map<String, Object> response = (Map<String, Object>) attributes.get("response");
                    return response == null ? null : (String) response.get("email");
                default: // 지원하지 않는 소셜 로그인 제공자인 경우
                    throw new IllegalArgumentException("지원하지 않는 소셜 로그인 제공자: " + provider); // 예외 발생
            }
        } catch (Exception e) {
            log.error("이메일 추출 중 오류 발생 - Provider: {}, Error: {}", provider, e.getMessage()); // 에러 로그 출력
            return null; // 추출 실패 시 null (호출부에서 로그인 실패 처리)
        }
    }

    /**
     * 소셜 로그인 제공자별 이메일 검증 여부 추출
     * - 제공자가 "이 이메일의 소유권이 검증되었다"고 단언하는지 확인한다.
     * - 검증되지 않은 이메일을 기존 계정에 자동 연동하면 계정 탈취 위험이 있으므로 [#7] 에서 사용한다.
     *
     * @param attributes 소셜 로그인 제공자에서 받은 사용자 정보
     * @param provider 소셜 로그인 제공자 (google, kakao, naver)
     * @return 제공자가 이메일을 검증했으면 true
     */
    private boolean extractEmailVerified(Map<String, Object> attributes, String provider) {
        try {
            switch (provider.toLowerCase()) {
                case "google": // Google 은 email_verified 플래그 제공
                    Object googleVerified = attributes.get("email_verified");
                    return Boolean.TRUE.equals(googleVerified) || "true".equalsIgnoreCase(String.valueOf(googleVerified));
                case "kakao": // Kakao 는 kakao_account.is_email_verified 플래그 제공
                    Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
                    if (kakaoAccount == null) return false;
                    Object kakaoVerified = kakaoAccount.get("is_email_verified");
                    return Boolean.TRUE.equals(kakaoVerified) || "true".equalsIgnoreCase(String.valueOf(kakaoVerified));
                case "naver": // Naver 는 별도 플래그가 없으며 계정 이메일은 검증된 것으로 간주
                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            log.error("이메일 검증 여부 추출 중 오류 - Provider: {}, Error: {}", provider, e.getMessage());
            return false; // 불확실하면 미검증으로 간주(보수적)
        }
    }

    /**
     * 소셜 로그인 제공자별 이름 추출
     * 각 소셜 로그인 제공자의 응답 구조가 다르므로 제공자별로 처리
     *
     * @param attributes 소셜 로그인 제공자에서 받은 사용자 정보 (Map 형태)
     * @param provider 소셜 로그인 제공자 (google, kakao, naver)
     * @return 추출된 이름
     */
    private String extractName(Map<String, Object> attributes, String provider) { // private 메서드 - 클래스 내부에서만 사용
        try {
            switch (provider.toLowerCase()) { // 소셜 로그인 제공자를 소문자로 변환하여 비교
                case "google": // Google 의 경우
                    String name = (String) attributes.get("name"); // Google 은 바로 name 필드에 이름이 있음
                    if (name == null) { // 이름이 null인 경우 처리
                        log.warn("Google에서 이름 정보를 받지 못했습니다."); // 경고 로그 출력
                        return "Google User"; // 기본 이름 반환
                    }
                    return name; // 정상적인 이름 반환
                case "kakao": // Kakao 의 경우
                    Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account"); // Kakao 는 kakao_account 객체 안에 정보가 있음
                    if (kakaoAccount == null) { // kakao_account가 null인 경우 처리
                        log.warn("Kakao에서 kakao_account 정보를 받지 못했습니다."); // 경고 로그 출력
                        return "Kakao User"; // 기본 이름 반환
                    }
                    Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile"); // kakao_account 안의 profile 객체에서 프로필 정보 가져옴
                    if (profile == null) { // profile이 null인 경우 처리
                        log.warn("Kakao에서 profile 정보를 받지 못했습니다."); // 경고 로그 출력
                        return "Kakao User"; // 기본 이름 반환
                    }
                    String nickname = (String) profile.get("nickname"); // profile 안의 nickname 필드에서 이름 추출 (Kakao 는 nickname 을 사용)
                    if (nickname == null) { // nickname이 null인 경우 처리
                        log.warn("Kakao에서 nickname 정보를 받지 못했습니다."); // 경고 로그 출력
                        return "Kakao User"; // 기본 이름 반환
                    }
                    return nickname; // 정상적인 이름 반환
                case "naver": // Naver 의 경우
                    Map<String, Object> response = (Map<String, Object>) attributes.get("response"); // Naver 는 response 객체 안에 정보가 있음
                    if (response == null) { // response가 null인 경우 처리
                        log.warn("Naver에서 response 정보를 받지 못했습니다."); // 경고 로그 출력
                        return "Naver User"; // 기본 이름 반환
                    }
                    String naverName = (String) response.get("name"); // response 안의 name 필드에서 이름 추출
                    if (naverName == null) { // 이름이 null인 경우 처리
                        log.warn("Naver에서 이름 정보를 받지 못했습니다."); // 경고 로그 출력
                        return "Naver User"; // 기본 이름 반환
                    }
                    return naverName; // 정상적인 이름 반환
                default: // 지원하지 않는 소셜 로그인 제공자인 경우
                    throw new IllegalArgumentException("지원하지 않는 소셜 로그인 제공자: " + provider); // 예외 발생
            }
        } catch (Exception e) {
            log.error("이름 추출 중 오류 발생 - Provider: {}, Error: {}", provider, e.getMessage()); // 에러 로그 출력
            return provider + " User"; // 기본 이름 반환
        }
    }

    /**
     * 소셜 로그인 제공자별 ID 추출
     * 각 소셜 로그인 제공자의 응답 구조가 다르므로 제공자별로 처리
     *
     * @param attributes 소셜 로그인 제공자에서 받은 사용자 정보 (Map 형태)
     * @param provider 소셜 로그인 제공자 (google, kakao, naver)
     * @return 추출된 소셜 로그인 제공자에서의 고유 ID
     */
    private String extractProviderId(Map<String, Object> attributes, String provider) { // private 메서드 - 클래스 내부에서만 사용
        try {
            switch (provider.toLowerCase()) { // 소셜 로그인 제공자를 소문자로 변환하여 비교
                case "google": // Google 의 경우
                    String sub = (String) attributes.get("sub"); // Google 은 sub 필드에 고유 ID가 있음 (subject 의 줄임말)
                    if (sub == null) { // sub가 null인 경우 처리
                        log.warn("Google에서 sub 정보를 받지 못했습니다."); // 경고 로그 출력
                        return "google_" + System.currentTimeMillis(); // 임시 ID 생성
                    }
                    return sub; // 정상적인 ID 반환
                case "kakao": // Kakao 의 경우
                    Object id = attributes.get("id"); // Kakao 는 id 필드에 고유 ID가 있음 (Long 타입이므로 String 으로 변환)
                    if (id == null) { // id가 null인 경우 처리
                        log.warn("Kakao에서 id 정보를 받지 못했습니다."); // 경고 로그 출력
                        return "kakao_" + System.currentTimeMillis(); // 임시 ID 생성
                    }
                    return String.valueOf(id); // String으로 변환하여 반환
                case "naver": // Naver 의 경우
                    Map<String, Object> response = (Map<String, Object>) attributes.get("response"); // Naver 는 response 객체 안에 정보가 있음
                    if (response == null) { // response가 null인 경우 처리
                        log.warn("Naver에서 response 정보를 받지 못했습니다."); // 경고 로그 출력
                        return "naver_" + System.currentTimeMillis(); // 임시 ID 생성
                    }
                    String naverId = (String) response.get("id"); // response 안의 id 필드에서 고유 ID 추출
                    if (naverId == null) { // id가 null인 경우 처리
                        log.warn("Naver에서 id 정보를 받지 못했습니다."); // 경고 로그 출력
                        return "naver_" + System.currentTimeMillis(); // 임시 ID 생성
                    }
                    return naverId; // 정상적인 ID 반환
                default: // 지원하지 않는 소셜 로그인 제공자인 경우
                    throw new IllegalArgumentException("지원하지 않는 소셜 로그인 제공자: " + provider); // 예외 발생
            }
        } catch (Exception e) {
            log.error("ID 추출 중 오류 발생 - Provider: {}, Error: {}", provider, e.getMessage()); // 에러 로그 출력
            return provider + "_" + System.currentTimeMillis(); // 임시 ID 생성
        }
    }

    /**
     * OAuth2 사용자 정보 처리 (기존 사용자 조회 또는 신규 사용자 생성)
     * 같은 이메일로 다른 소셜 로그인 제공자로 가입한 경우를 처리
     *
     * @param email 사용자 이메일
     * @param name 사용자 이름
     * @param providerId 소셜 로그인 제공자에서의 고유 ID
     * @param authProvider 인증 제공자 (GOOGLE, KAKAO, NAVER)
     * @return 처리된 사용자 정보 (기존 사용자 또는 신규 생성된 사용자)
     */
    @Transactional
    public User processOAuth2User(String email, String name, String providerId, AuthProvider authProvider, boolean emailVerified) {
        // 1) (provider, providerId)로 연동 우선 조회
        Optional<SocialAccount> linked =
                socialAccountRepository.findByProviderAndProviderId(authProvider, providerId);
        if (linked.isPresent()) { // 이미 연동된 계정 → 해당 사용자 반환
            Long userId = linked.get().getUser().getId();
            User managed = userRepository.findById(userId).orElseThrow();
            // 사용자 정보 최소 업데이트(이름은 사용자 지정값 우선, 덮어쓰지 않음)
            if (managed.getName() == null || managed.getName().isBlank()) {
                managed.setName(name);
            }
            isNewUserFlag.set(Boolean.FALSE);
            return userRepository.save(managed);
        }

        // 2) 이메일 기준 기존 사용자 존재 → 자동 연동
        Optional<User> existingUserByEmail = userRepository.findByEmail(email);
        if (existingUserByEmail.isPresent()) {
            User user = existingUserByEmail.get();
            // [#7] 미검증 이메일 자동 연동 차단:
            // 공격자가 피해자의 이메일로 소셜 계정을 만들어 기존 계정에 자동 연동되면 계정 탈취가 가능하다.
            // 제공자가 이메일 소유권을 검증한 경우에만 자동 연동을 허용한다.
            if (!socialAccountRepository.existsByUserAndProvider(user, authProvider)) {
                if (!emailVerified) {
                    log.warn("OAuth2 자동 연동 차단 - 미검증 이메일 (email: {}, provider: {})", email, authProvider);
                    throw new OAuth2AuthenticationException(new OAuth2Error("email_not_verified"),
                            "이미 가입된 이메일입니다. 소셜 제공자에서 이메일이 검증되지 않아 자동 연동할 수 없습니다.");
                }
                SocialAccount account = SocialAccount.createSocialAccount(
                        user,
                        authProvider,
                        providerId,
                        email
                );
                socialAccountRepository.save(account);
            }
            // 사용자 프로필 최소 업데이트(닉네임은 사용자 지정값 우선, 덮어쓰지 않음)
            if (user.getName() == null || user.getName().isBlank()) {
                user.setName(name);
            }
            user.setEmailVerified(true); // 검증된 이메일로 연동되었으므로 true
            isNewUserFlag.set(Boolean.FALSE);
            return userRepository.save(user);
        }

        // 3) 신규 사용자 + 연동 생성
        User newUser = User.createSocialUser(
                email,
                name,
                authProvider,
                providerId,
                null // profileImage는 null로 설정
        );
        newUser.setEmailVerified(emailVerified); // 제공자의 실제 검증 여부를 반영(기본 팩토리는 true 로 설정함)
        User saved = userRepository.save(newUser);
        SocialAccount firstLink = SocialAccount.createSocialAccount(
                saved,
                authProvider,
                providerId,
                email
        );
        socialAccountRepository.save(firstLink);
        
        log.info("신규 소셜 사용자 생성됨 - ID: {}, 이메일: {}", saved.getId(), saved.getEmail());
        isNewUserFlag.set(Boolean.TRUE);
        return saved;
    }

    /**
     * OAuth2User 객체 생성
     * Spring Security 에서 사용할 수 있는 형태로 OAuth2User 객체를 생성
     * 기존 OAuth2User 의 정보에 애플리케이션에서 추가한 사용자 정보를 포함
     *
     * @param oAuth2User 소셜 로그인 제공자로부터 받은 원본 OAuth2User 객체
     * @param user 애플리케이션에서 처리한 사용자 정보
     * @return Spring Security 에서 사용할 수 있는 OAuth2User 객체
     */
    // 테스트에서 직접 검증할 수 있도록 package-private (loadUser 전체를 태우려면 실제 OAuth2 요청이 필요함)
    OAuth2User createOAuth2User(OAuth2User oAuth2User, User user) {
        // 기존 OAuth2User 의 attributes 에 사용자 정보 추가
        // 소셜 로그인 제공자로부터 받은 정보 + 애플리케이션에서 추가한 정보를 모두 포함
        Map<String, Object> attributes = new HashMap<>(oAuth2User.getAttributes()); // 원본 attributes 복사
        attributes.put("userId", user.getId()); // 애플리케이션에서의 사용자 ID 추가
        attributes.put("userEmail", user.getEmail()); // 사용자 이메일 추가
        attributes.put("userName", user.getName()); // 사용자 이름 추가
        attributes.put("userRole", user.getRole().name()); // 사용자 권한 추가 (USER, ADMIN 등)
        attributes.put("authProvider", user.getAuthProvider().name()); // 인증 제공자 정보 추가

        // 신뢰 가능한 신규 사용자 플래그 설정(ThreadLocal)
        boolean isNew = Boolean.TRUE.equals(isNewUserFlag.get());
        attributes.put("isNewUser", isNew); // 신규 사용자 여부 추가

        // DB 의 역할(USER/ADMIN)을 Spring Security 권한(ROLE_*)으로 부여한다.
        // 주의: attributes 의 "userRole" 은 단순 데이터라 인가에 쓰이지 않는다.
        // 이게 없으면 DB 가 ADMIN 이어도 authorities 에 ROLE_ADMIN 이 없어
        // /api/admin/** (hasRole("ADMIN")) 이 403 이 된다(프론트는 attributes 의 role 을 보므로 화면은 열림).
        // 이메일 로그인 경로(SessionAuthenticationFilter/LocalUserDetailsService)와 동일한 규칙을 맞춘다.
        List<GrantedAuthority> authorities = new ArrayList<>(oAuth2User.getAuthorities()); // 제공자 기본 권한 유지
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())); // DB 역할 추가

        // 새로운 OAuth2User 객체 생성 및 반환
        // Spring Security에서 사용할 수 있는 DefaultOAuth2User 객체 생성
        return new org.springframework.security.oauth2.core.user.DefaultOAuth2User(
                authorities, // 제공자 기본 권한 + DB 역할(ROLE_USER/ROLE_ADMIN)
                attributes, // 사용자 속성 정보 (소셜 로그인 정보 + 애플리케이션 정보)
                "userEmail" // nameAttributeKey - Spring Security 에서 사용자 식별에 사용할 키 (이메일 사용)
        );
    }

    /**
     * 신규 생성된 사용자인지 확인
     * 사용자가 방금 생성되었는지 여부를 판단 (생성 시간 기준)
     *
     * @param user 확인할 사용자 객체
     * @return 신규 사용자 여부 (true: 신규, false: 기존)
     */
    private boolean isNewlyCreatedUser(User user) {
        // 더 이상 시간 기반 판별을 사용하지 않음. 하위 호환을 위해 false 반환.
        return false;
    }
}