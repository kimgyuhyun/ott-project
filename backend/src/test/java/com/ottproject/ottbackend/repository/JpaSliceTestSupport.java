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
 */
@TestConfiguration
class JpaSliceTestSupport {

    @Bean
    SqlSessionFactory sqlSessionFactory() {
        org.apache.ibatis.session.Configuration cfg = new org.apache.ibatis.session.Configuration();
        // Environment 가 없으면 SqlSessionTemplate 생성 시 getEnvironment().getDataSource() 에서 NPE 가 난다.
        // 쿼리를 실행하지 않으므로 DataSource 는 연결 정보 없는 껍데기로 충분하다.
        cfg.setEnvironment(new Environment("test", new JdbcTransactionFactory(), new SimpleDriverDataSource()));
        return new DefaultSqlSessionFactory(cfg);
    }
}
