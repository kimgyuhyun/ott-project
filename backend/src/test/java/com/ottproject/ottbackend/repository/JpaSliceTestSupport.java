package com.ottproject.ottbackend.repository;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/**
 * 리포지토리 슬라이스 테스트 공용 설정
 *
 * 메인 클래스의 @MapperScan("...mybatis") 은 @DataJpaTest 슬라이스에서도 매퍼 빈을 등록하려 하는데,
 * 슬라이스에는 MyBatis 자동설정이 없어 SqlSessionFactory 부재로 컨텍스트가 깨진다.
 * JPQL 만 검증하는 테스트들은 실제 매퍼 쿼리를 쓰지 않으므로, 등록만 가능한 껍데기 팩토리를 넣어준다.
 *
 * 함께 필요한 프로퍼티(각 테스트에서 @TestPropertySource 로 지정)
 * - spring.flyway.enabled=false : 마이그레이션이 PostgreSQL 전용 문법이라 H2 에서 깨진다
 * - spring.jpa.hibernate.ddl-auto=create-drop : 엔티티로 H2 스키마를 만든다
 *
 * ⚠ JpaAuditingConfig 를 슬라이스에 임포트하지 말 것
 * - @EnableJpaAuditing 은 AspectJ 로 위빙된 AnnotationBeanConfigurerAspect 를 끌어오는데,
 *   이 애스펙트는 aspectOf() 싱글턴이라 JVM 전역이고 내부에 BeanFactory 참조를 static 으로 붙든다.
 * - 스프링 테스트 컨텍스트는 캐싱되어 닫히지 않으므로 그 참조가 계속 살아남는다. 그 결과 Auditing 을
 *   켠 적도 없고 관련 빈이 0개인 '다른' 슬라이스에서도 AuditingEntityListener 가 남의 BeanFactory 로부터
 *   핸들러를 주입받아 @CreatedDate/@LastModifiedDate 를 저장 시각으로 덮어쓴다.
 * - 즉 픽스처에 심은 시각이 살아남는지가 '테스트 실행 순서'에 따라 달라진다.
 *   (실제로 UserRepositorySignupCountTest 가 이 이유로 순서에 따라 깨졌다)
 * - 그래서 슬라이스에서는 Auditing 을 켜지 않고, not-null 인 시각 컬럼은 픽스처가 직접 채운다.
 *   시각 자체를 검증해야 한다면 픽스처 상수가 아니라 저장 직후 DB 값을 기준으로 비교할 것.
 */
// 하위 패키지(repository.curation)의 슬라이스 테스트도 임포트하므로 public 이어야 한다.
@TestConfiguration
public class JpaSliceTestSupport {

    @Bean
    SqlSessionFactory sqlSessionFactory() {
        org.apache.ibatis.session.Configuration cfg = new org.apache.ibatis.session.Configuration();
        // Environment 가 없으면 SqlSessionTemplate 생성 시 getEnvironment().getDataSource() 에서 NPE 가 난다.
        // 쿼리를 실행하지 않으므로 DataSource 는 연결 정보 없는 껍데기로 충분하다.
        cfg.setEnvironment(new Environment("test", new JdbcTransactionFactory(), new SimpleDriverDataSource()));
        return new DefaultSqlSessionFactory(cfg);
    }
}
