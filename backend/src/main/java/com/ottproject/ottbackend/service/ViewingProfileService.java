package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.ViewingProfileResponseDto;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.entity.ViewingProfile;
import com.ottproject.ottbackend.exception.LastViewingProfileException;
import com.ottproject.ottbackend.exception.ViewingProfileLimitExceededException;
import com.ottproject.ottbackend.exception.ViewingProfileNotFoundException;
import com.ottproject.ottbackend.repository.UserRepository;
import com.ottproject.ottbackend.repository.ViewingProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 시청 프로필 서비스
 *
 * 큰 흐름
 * - 계정 하나가 가지는 시청 프로필의 목록·생성·이름변경·삭제와 소유 확인을 담당한다.
 * - 개수 규칙(상한, 마지막 하나)은 여러 행에 걸친 규칙이라 엔티티가 아니라 여기에 둔다.
 *   이름 규칙(공백·길이)은 한 행 안에서 판정되므로 엔티티가 갖는다.
 *
 * 메서드 개요
 * - listProfiles: 목록 조회. 하나도 없으면 계정 이름으로 기본 프로필을 만들어 준다.
 * - create/rename/delete: 프로필 관리
 * - requireOwnedProfile: 선택 요청이 지목한 프로필이 내 것인지 확인
 */
@Service
@RequiredArgsConstructor
public class ViewingProfileService {

    private final ViewingProfileRepository viewingProfileRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    /**
     * 계정의 프로필 목록을 반환한다.
     *
     * 하나도 없으면 계정 이름으로 기본 프로필을 하나 만든다. 기존 가입자에게도 프로필이 생겨야 하고
     * 신규 가입 경로를 건드리지 않으려면 여기가 유일한 생성 지점이 된다. 그래서 읽기 전용이 아니다.
     *
     * @param userId 현재 로그인한 계정
     * @return 만든 순서대로 정렬된 프로필 목록(최소 1개)
     */
    @Transactional
    public List<ViewingProfileResponseDto> listProfiles(Long userId) {
        List<ViewingProfile> profiles = viewingProfileRepository.findByUserIdOrderByIdAsc(userId);
        if (profiles.isEmpty()) {
            profiles = List.of(createDefaultProfile(userId));
        }
        return profiles.stream().map(ViewingProfileService::toDto).toList();
    }

    /**
     * 프로필을 만든다.
     *
     * @param userId 현재 로그인한 계정
     * @param name   표시 이름
     * @return 만들어진 프로필
     * @throws ViewingProfileLimitExceededException 계정당 상한을 넘긴 경우
     */
    @Transactional
    public ViewingProfileResponseDto create(Long userId, String name) {
        if (viewingProfileRepository.countByUserId(userId) >= ViewingProfile.MAX_PER_ACCOUNT) {
            throw new ViewingProfileLimitExceededException(
                    "프로필 상한 초과: userId=" + userId + ", 상한=" + ViewingProfile.MAX_PER_ACCOUNT);
        }
        User owner = userRepository.getReferenceById(userId); // FK 만 걸면 되므로 실제 조회는 하지 않는다
        ViewingProfile saved = viewingProfileRepository.save(
                ViewingProfile.create(owner, name, LocalDateTime.now(clock)));
        return toDto(saved);
    }

    /**
     * 프로필 이름을 바꾼다.
     *
     * @param userId    현재 로그인한 계정
     * @param profileId 대상 프로필
     * @param name      새 표시 이름
     * @return 바뀐 프로필
     * @throws ViewingProfileNotFoundException 없거나 내 것이 아닌 경우
     */
    @Transactional
    public ViewingProfileResponseDto rename(Long userId, Long profileId, String name) {
        ViewingProfile profile = findOwned(userId, profileId);
        profile.rename(name, LocalDateTime.now(clock));
        return toDto(profile);
    }

    /**
     * 프로필을 지운다.
     *
     * @param userId    현재 로그인한 계정
     * @param profileId 대상 프로필
     * @throws ViewingProfileNotFoundException 없거나 내 것이 아닌 경우
     * @throws LastViewingProfileException     마지막 하나 남은 프로필인 경우
     */
    @Transactional
    public void delete(Long userId, Long profileId) {
        ViewingProfile profile = findOwned(userId, profileId);
        if (viewingProfileRepository.countByUserId(userId) <= 1) {
            throw new LastViewingProfileException("마지막 프로필 삭제 시도: userId=" + userId);
        }
        viewingProfileRepository.delete(profile);
    }

    /**
     * 선택 요청이 지목한 프로필이 내 것인지 확인한다.
     *
     * 클라이언트가 보낸 식별자를 그대로 믿지 않고 소유자를 서버가 조회해 판정한다.
     *
     * @param userId    현재 로그인한 계정
     * @param profileId 선택하려는 프로필
     * @return 확인된 프로필
     * @throws ViewingProfileNotFoundException 없거나 내 것이 아닌 경우
     */
    @Transactional(readOnly = true)
    public ViewingProfileResponseDto requireOwnedProfile(Long userId, Long profileId) {
        return toDto(findOwned(userId, profileId));
    }

    private ViewingProfile createDefaultProfile(Long userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ViewingProfileNotFoundException("계정 없음: userId=" + userId));
        return viewingProfileRepository.save(
                ViewingProfile.create(owner, owner.getName(), LocalDateTime.now(clock)));
    }

    private ViewingProfile findOwned(Long userId, Long profileId) {
        ViewingProfile profile = viewingProfileRepository.findById(profileId)
                .orElseThrow(() -> new ViewingProfileNotFoundException("프로필 없음: profileId=" + profileId));
        if (!profile.isOwnedBy(userId)) {
            // 남의 프로필이어도 "없음"으로 응답한다. 권한 없음으로 구분하면 id 존재 여부가 새어 나간다.
            throw new ViewingProfileNotFoundException(
                    "다른 계정의 프로필: profileId=" + profileId + ", userId=" + userId);
        }
        return profile;
    }

    private static ViewingProfileResponseDto toDto(ViewingProfile profile) {
        return ViewingProfileResponseDto.builder()
                .id(profile.getId())
                .name(profile.getName())
                .build();
    }
}
