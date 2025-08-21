package com.ottproject.ottbackend.repository;

import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * UserRepository
 *
 * 큰 흐름
 * - 사용자 CRUD 및 조회용 파생 메서드를 제공하는 JPA 리포지토리.
 *
 * 메서드 개요
 * - findByEmail: 이메일로 사용자 단건 조회
 * - existsByEmail: 이메일 중복 여부
 * - findByEmailAndAuthProvider: 이메일+제공자 기준 조회(소셜 계정 구분)
 * - findByEmailVerified: 이메일 인증 여부로 목록 조회
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email); // 이메일로 사용자 조회
    boolean existsByEmail(String email); // 이메일 존재 여부
    Optional<User> findByEmailAndAuthProvider(String email, AuthProvider authProvider); // 이메일+제공자 조회
    List<User> findByEmailVerified(boolean emailVerified); // 이메일 인증 여부로 목록
}
