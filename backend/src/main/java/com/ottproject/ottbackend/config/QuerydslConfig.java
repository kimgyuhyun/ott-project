package com.ottproject.ottbackend.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QueryDSL 설정
 *
 * 큰 흐름
 * - JPAQueryFactory 를 빈으로 등록해 타입 안전한 동적 쿼리를 조립할 수 있게 한다.
 *
 * 사용 범위(의도적으로 좁다)
 * - 관리자 애니 큐레이션의 동적 검색/벌크 수정에만 쓴다(AnimeCurationQueryRepository).
 * - 사용자향 읽기는 MyBatis 프로젝션, 일반 쓰기는 JPA 를 그대로 유지한다.
 *   조건이 런타임에 자유 조합되는 곳은 현재 관리자 검색뿐이고, 그 한 곳이 QueryDSL 이 실제로 이기는 지점이다.
 *
 * 트랜잭션 주의
 * - @PersistenceContext 로 주입되는 EntityManager 는 실제 EntityManager 가 아니라 공유 프록시다.
 *   호출 시점의 트랜잭션에 묶인 진짜 EntityManager 로 위임하므로, 이 팩토리를 싱글턴 빈으로 두어도
 *   요청/트랜잭션 간에 영속성 컨텍스트가 섞이지 않는다.
 */
@Configuration
public class QuerydslConfig {

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}
