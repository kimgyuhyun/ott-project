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
@Repository // 스프링 Bean에 Repository로 등록록
public interface UserRepository extends JpaRepository<User, Long> {
    // JpaRepository<User, Long> 타입을 상속받는 userRepository interface임 참고로 interface끼리는 extends임
    // JpaReposotry<User, Long>에 뜻은 이 Repository는 user 엔티티만 다르고 USer 엔티티의 ID 타입이 Long이라는 의미임
    // JpaRepositry<Entity, ID>이렇게 정의되어있고
    // 첫 번째 타입 파라미터에는 이 Repository가 다루는 엔티티 타입을 할당
    // 두 번째 타입 파라미터에는 이 Repository가 다루는 엔티티의 ID 필드 타입을 할당
    Optional<User> findByEmail(String email); // 인터페이스에 fidnByEmail 메서드 선언 JPA가 자동으로 구현을 제공해줌
    // JPA가 메서드 이름을 파싱해 자동으로 쿼리 생성함 (Query Method) 메서드 이름 규칙을 따르면 구현 없이 동작함
    // JPA Query Method의 동작 방식은
    // 1. 메서드 이름 파싱: findByEmail -> find + By + Email / JPA가 "Email 필드로 조회"로 해석
    // 2. 자동 쿼리 생성: SELECT * FROM users WHERE email = ? / ?는 나중에 실제 값이 들어갈 자리
    // 이 쿼리문은 user 테이블에서 email = 파라미터인걸 전부 가져오라는 뜻
    // 가져온 정보를 Optional<User> 객체로 반환함함
    // 3. 구현 자동 제공: MyBatis처럼 필드랑 매핑하면서 직접 쿼리를 작성하지 않아도 JPA가 자동으로 구현을 제공함
    boolean existsByEmail(String email); // 메서드 선언 JPA가 자동으로 구현을 제공
    // 메서드 이름을 파싱해 자동으로 쿼리를 생성함 (Query Method)
    // Email 필드로 존재 여부 확인으로 해석
    // SELECT EXIStS(
    //  SELECT 1
    //  FROM user
    // WHERE email = ?
    //)
    // SELECT EXISTS(...)은 서브쿼리가 결과를 반환하는지 확인하고 행이 하나라도 있으면 true, 없으면 false 반환함
    // SELECT 1은 단순히 행이 있는지만 확인하는것
    // WhERE email = ? 조건으로 파라미터로 들어온 email일로 해당하는 행을 찾고
    // 행이 있으면 SELECT 1이 결과를 반환 / EXISTS는 그 결과가 있으면 true, 없으면 false를 반환하는것
    // 행이 없으면 결과셋이 없고 EXISTS 함수는 결과셋이 있으면 true, 없으면 false임
    // 함수안에 서브쿼리를 날린것
    Optional<User> findByEmailAndAuthProvider(String email, AuthProvider authProvider); // 메서드 선언 JPA가 자동으로 구현을 제공
    // 메서드 이름을 파싱해 자동으로 쿼리를 생성함 (Query Method)
    // Email 필드와 AuthProvider 필드로 조회로 해석
    // SELECT *
    // FROM users
    // WHERE email = ?
    // AND auth_provider = ?
    // 파라미터로 받은 email과 autrhProvider를 각각에 맞는 ? 자리에 조건으로 넣고
    // email에 해당되는 user 값을 찾고 auth_provider까지 일치하는 조건을 user 테이블에서 찾고
    // Optional<User> 타입으로 반환함
    // AND를 쓰는 이유는 이메일은 같아도 소셜 제공자가 다를 수 있어서임 그래서 AND 조건식으로 이메일 + 소셜 제공자 조합인 유저 하나만 찾는것
    List<User> findByEmailVerified(boolean emailVerified); // 메서드 선언 JPA가 자동으로 구현을 제공
    // 다중 조회용 메서드 / 관리자 전용 메서드
    // 메서드 이름을 파싱해 자동으로 쿼리를 생성함 (Query Method)
    // emailVerified 필드가 true 또는 false인 유저들을 전부 조회해서 리스트로 반환함
    // SELECT *
    // FROM users
    // WHERE email_verified = ?;
    // ? 자리에 true 넣으면 -> 이메일 인증 완료된 유저들 목록
    // ? 자리에 false 넣으면 -> 이메일 인증 안 된 유저들 목록
    // 관리자 페이지에서 "이메일 인증 안 한 유저들만 보기"
    // 배치 작업에서 "인증 안 한 유저들에게 리마인드 메일 보내기"같은 용도로 사용함함

}
