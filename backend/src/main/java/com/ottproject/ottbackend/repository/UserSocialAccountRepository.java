package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.entity.UserSocialAccount;
import com.ottproject.ottbackend.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository // 스프링 컴포넌트 스캔
public interface UserSocialAccountRepository extends JpaRepository<UserSocialAccount, Long> { // 연동 리포지토리
    Optional<UserSocialAccount> findByProviderAndProviderId(AuthProvider provider, String providerId); // 1건 조회
    List<UserSocialAccount> findByUser(User user); // 사용자에 연결된 모든 연동
    boolean existsByUserAndProvider(User user, AuthProvider provider); // 이미 연동 여부
}


