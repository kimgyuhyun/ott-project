package com.ottproject.ottbackend.controller;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

/**
 * 웹 슬라이스(@WebMvcTest) 테스트 공용 설정
 *
 * 메인 클래스의 @MapperScan("...mybatis") 은 웹 슬라이스에서도 매퍼 빈 15개를 등록하려 하는데,
 * @WebMvcTest 에는 SqlSessionFactory 가 없어 "Property 'sqlSessionFactory' or 'sqlSessionTemplate'
 * are required" 로 컨텍스트가 깨진다.
 * 인가 규칙만 보는 테스트는 실제 매퍼 쿼리를 쓰지 않으므로, 등록만 가능한 껍데기 팩토리를 넣어준다.
 *
 * (JPA 슬라이스 쪽 대응물은 repository/JpaSliceTestSupport)
 */
@TestConfiguration
public class WebSliceTestSupport {

    @Bean
    SqlSessionFactory sqlSessionFactory() {
        org.apache.ibatis.session.Configuration cfg = new org.apache.ibatis.session.Configuration();
        // Environment 가 없으면 SqlSessionTemplate 생성 시 getEnvironment().getDataSource() 에서 NPE 가 난다.
        // 쿼리를 실행하지 않으므로 DataSource 는 연결 정보 없는 껍데기로 충분하다.
        cfg.setEnvironment(new Environment("test", new JdbcTransactionFactory(), new SimpleDriverDataSource()));
        return new DefaultSqlSessionFactory(cfg);
    }
}
