package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.SocialAccount;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.AuthProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * SocialAccountRepository
 *
 * 큰 흐름
 * - 사용자와 소셜 계정의 연동 정보를 관리하는 JPA 리포지토리.
 *
 * 메서드 개요
 * - findByProviderAndProviderId: (제공자, 외부ID)로 단건 조회
 * - findByUser: 사용자 연동 목록 조회
 * - existsByUserAndProvider: 제공자 연동 여부 확인
 * - deleteByUser: 사용자 연동 전체 삭제(탈퇴)
 */
@Repository // 스프링 컴포넌트 스캔
public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> { // 연동 리포지토리
    Optional<SocialAccount> findByProviderAndProviderId(AuthProvider provider, String providerId); // 1건 조회

    List<SocialAccount> findByUser(User user); // 사용자에 연결된 모든 연동

    boolean existsByUserAndProvider(User user, AuthProvider provider); // 이미 연동 여부

    void deleteByUser(User user); // 탈퇴 시 연동 해제 — 남겨두면 같은 소셜 계정으로 탈퇴한 계정에 다시 로그인된다
}
