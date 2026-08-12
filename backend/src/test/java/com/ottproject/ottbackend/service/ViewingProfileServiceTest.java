package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.ViewingProfileResponseDto;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.entity.ViewingProfile;
import com.ottproject.ottbackend.exception.LastViewingProfileException;
import com.ottproject.ottbackend.exception.ViewingProfileLimitExceededException;
import com.ottproject.ottbackend.exception.ViewingProfileNotFoundException;
import com.ottproject.ottbackend.repository.UserRepository;
import com.ottproject.ottbackend.repository.ViewingProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ViewingProfileService 단위 테스트
 *
 * 지키려는 규칙
 * - 계정당 프로필 상한을 넘겨 만들 수 없다.
 * - 마지막 하나 남은 프로필은 지울 수 없다. 0개가 되면 선택 화면에서 아무것도 못 고른다.
 * - 남의 프로필은 이름 변경·삭제·선택 어느 쪽으로도 손댈 수 없고, "권한 없음"이 아니라
 *   "없음"으로 응답한다(id 존재 여부가 새면 안 된다).
 * - 프로필이 하나도 없는 계정은 목록 조회 시 계정 이름으로 기본 프로필이 생긴다.
 *
 * 개수 규칙만 여기서 검증한다. 이름 규칙(공백·길이)은 엔티티가 갖고 있어 엔티티 테스트가 본다.
 */
@ExtendWith(MockitoExtension.class)
class ViewingProfileServiceTest {

    private static final Long MY_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long PROFILE_ID = 10L;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock private ViewingProfileRepository viewingProfileRepository;
    @Mock private UserRepository userRepository;

    private ViewingProfileService service() {
        return new ViewingProfileService(viewingProfileRepository, userRepository, fixedClock);
    }

    private ViewingProfile profileOf(Long ownerId, String name) {
        return ViewingProfile.create(User.reference(ownerId), name, LocalDateTime.now(fixedClock));
    }

    @Test
    @DisplayName("상한에 도달한 계정은 프로필을 더 만들 수 없다")
    void create_rejectsWhenLimitReached() {
        given(viewingProfileRepository.countByUserId(MY_ID)).willReturn((long) ViewingProfile.MAX_PER_ACCOUNT);

        assertThatThrownBy(() -> service().create(MY_ID, "다섯번째"))
                .isInstanceOf(ViewingProfileLimitExceededException.class);

        verify(viewingProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("상한 직전이면 프로필을 만들 수 있다")
    void create_allowsBelowLimit() {
        given(viewingProfileRepository.countByUserId(MY_ID)).willReturn((long) ViewingProfile.MAX_PER_ACCOUNT - 1);
        given(userRepository.getReferenceById(MY_ID)).willReturn(User.reference(MY_ID));
        given(viewingProfileRepository.save(any(ViewingProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ViewingProfileResponseDto created = service().create(MY_ID, "네번째");

        assertThat(created.getName()).isEqualTo("네번째");
    }

    @Test
    @DisplayName("마지막 하나 남은 프로필은 지울 수 없다")
    void delete_rejectsLastProfile() {
        given(viewingProfileRepository.findById(PROFILE_ID)).willReturn(Optional.of(profileOf(MY_ID, "혼자")));
        given(viewingProfileRepository.countByUserId(MY_ID)).willReturn(1L);

        assertThatThrownBy(() -> service().delete(MY_ID, PROFILE_ID))
                .isInstanceOf(LastViewingProfileException.class);

        verify(viewingProfileRepository, never()).delete(any());
    }

    @Test
    @DisplayName("둘 이상 남아 있으면 프로필을 지운다")
    void delete_removesWhenOthersRemain() {
        ViewingProfile mine = profileOf(MY_ID, "지울것");
        given(viewingProfileRepository.findById(PROFILE_ID)).willReturn(Optional.of(mine));
        given(viewingProfileRepository.countByUserId(MY_ID)).willReturn(2L);

        service().delete(MY_ID, PROFILE_ID);

        verify(viewingProfileRepository).delete(mine);
    }

    @Test
    @DisplayName("남의 프로필은 지울 수 없고 '없음'으로 응답한다")
    void delete_rejectsOtherUsersProfile() {
        given(viewingProfileRepository.findById(PROFILE_ID)).willReturn(Optional.of(profileOf(OTHER_ID, "남의것")));

        assertThatThrownBy(() -> service().delete(MY_ID, PROFILE_ID))
                .isInstanceOf(ViewingProfileNotFoundException.class);

        verify(viewingProfileRepository, never()).delete(any());
    }

    @Test
    @DisplayName("남의 프로필은 이름을 바꿀 수 없다")
    void rename_rejectsOtherUsersProfile() {
        ViewingProfile others = profileOf(OTHER_ID, "남의것");
        given(viewingProfileRepository.findById(PROFILE_ID)).willReturn(Optional.of(others));

        assertThatThrownBy(() -> service().rename(MY_ID, PROFILE_ID, "가로채기"))
                .isInstanceOf(ViewingProfileNotFoundException.class);

        assertThat(others.getName()).isEqualTo("남의것");
    }

    @Test
    @DisplayName("남의 프로필은 선택할 수 없다")
    void requireOwnedProfile_rejectsOtherUsersProfile() {
        given(viewingProfileRepository.findById(PROFILE_ID)).willReturn(Optional.of(profileOf(OTHER_ID, "남의것")));

        assertThatThrownBy(() -> service().requireOwnedProfile(MY_ID, PROFILE_ID))
                .isInstanceOf(ViewingProfileNotFoundException.class);
    }

    @Test
    @DisplayName("프로필이 없는 계정은 목록 조회 때 계정 이름으로 기본 프로필이 생긴다")
    void listProfiles_createsDefaultWhenEmpty() {
        User me = User.createLocalUser("me@example.com", "encoded", "김규현");
        given(viewingProfileRepository.findByUserIdOrderByIdAsc(MY_ID)).willReturn(List.of());
        given(userRepository.findById(MY_ID)).willReturn(Optional.of(me));
        given(viewingProfileRepository.save(any(ViewingProfile.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        List<ViewingProfileResponseDto> profiles = service().listProfiles(MY_ID);

        assertThat(profiles).hasSize(1);
        assertThat(profiles.get(0).getName()).isEqualTo("김규현");
    }

    @Test
    @DisplayName("프로필이 있으면 목록 조회가 새로 만들지 않는다")
    void listProfiles_doesNotCreateWhenPresent() {
        given(viewingProfileRepository.findByUserIdOrderByIdAsc(MY_ID))
                .willReturn(List.of(profileOf(MY_ID, "기존")));

        List<ViewingProfileResponseDto> profiles = service().listProfiles(MY_ID);

        assertThat(profiles).extracting(ViewingProfileResponseDto::getName).containsExactly("기존");
        verify(viewingProfileRepository, never()).save(any());
    }
}
