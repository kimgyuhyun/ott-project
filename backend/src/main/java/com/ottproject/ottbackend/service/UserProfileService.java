package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.UserProfileDto;
import com.ottproject.ottbackend.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UserProfileService
 *
 * 큰 흐름
 * - 로그인한 사용자 자신의 프로필을 조회한다.
 *
 * 왜 SettingsService 에 넣지 않았는가
 * - 그쪽은 재생 환경 설정(UserSettings) 전용이라 사용자 계정 정보를 넣으면 이름이 어긋난다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final UserRepository userRepository;

    /**
     * 사용자 프로필 조회.
     *
     * @param userId 사용자 ID
     * @return 사용자가 있으면 프로필 DTO, 없으면 빈 값
     */
    @Transactional(readOnly = true)
    public Optional<UserProfileDto> findProfile(Long userId) {
        return userRepository.findById(userId).map(UserProfileDto::from);
    }

    /**
     * 이메일로 사용자 프로필 조회.
     * 인증 주체의 이름(=이메일)만 들고 있는 경로에서 DB 의 최신 표시명을 얻을 때 쓴다.
     *
     * @param email 사용자 이메일
     * @return 사용자가 있으면 프로필 DTO, 없으면 빈 값
     */
    @Transactional(readOnly = true)
    public Optional<UserProfileDto> findProfileByEmail(String email) {
        return userRepository.findByEmail(email).map(UserProfileDto::from);
    }

    /**
     * 표시명(닉네임) 변경.
     * 형식 검증(길이·공백)은 호출자가 끝낸 값이 들어온다고 본다.
     *
     * 변경 전 이름은 여기서 로그로 남긴다. 반환값으로 넘기면 이름이 null 인 사용자에서
     * Optional 이 빈 값으로 접혀 "사용자 없음"과 구분되지 않는다.
     *
     * @param userId 사용자 ID
     * @param newNickname 새 표시명
     * @return 변경된 프로필. 사용자가 없으면 빈 값이고 아무것도 바꾸지 않는다
     */
    @Transactional
    public Optional<UserProfileDto> updateNickname(Long userId, String newNickname) {
        return userRepository.findById(userId).map(user -> {
            String oldNickname = user.getName();
            // TODO(E-2): User 엔티티의 세터를 걷어낼 때 의도가 드러나는 도메인 메서드로 바꾼다
            user.setName(newNickname);
            userRepository.save(user);
            log.info("닉네임 업데이트 완료 - 사용자ID: {}, 기존: {}, 신규: {}", userId, oldNickname, newNickname);
            return UserProfileDto.from(user);
        });
    }
}
