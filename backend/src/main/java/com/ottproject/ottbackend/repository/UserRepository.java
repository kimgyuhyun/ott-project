package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/*
사용자 데이터베이스 접근을 위한 Repository
JpaRepository 를 상속받아 기본 CRUD 기능 제공
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail (String email); // 이메일로 사용자 조회
    // 로그인할 때 사용자 정보 조회 ex) 사용자가 로그인 시도 -> 이메일로 사용자 찾기
    boolean existsByEmail (String email); // 이메일 존재 여부 확인
    // 회원가입할 때 이메일 중복 확인 ex) -> 새 사용자가 회원가입 -> 이미 가입된 이메일인지 확인
    Optional<User> findByEmailAndAuthProvider(String email, String authProvider); // 이메일과 인증 제공자로 사용자 조회 (소셜 로그인용)
    // 소셜 로그인할 때 사용 ex) 구글로 로그인한 사용자가 네이버로도 같은 이메일로 가입하려고 할 때 구분
    List<User> findByEmailVerified(boolean emailVerified); // 이메일 인증 여부로 사용자 조회
    // 관리자 기능 또는 이메일 인증 관리 ex) 관리자가 인증되지 않은 사용자 목록 조회
    // 이메일 인증이 필요한 사용자들에게 알림 발송, 인증 완료된 사용자만 특정 기능 허용
}
