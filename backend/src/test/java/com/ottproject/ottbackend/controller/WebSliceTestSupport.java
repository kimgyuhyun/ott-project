package com.ottproject.ottbackend.controller;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

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

    /**
     * 모든 MockMvc 요청에 허용된 Origin 을 기본으로 붙인다.
     *
     * SecurityConfig 를 가져오는 슬라이스는 OriginValidationFilter 도 함께 돈다. 이 필터는 출처 헤더가
     * 없는 POST/PUT/PATCH/DELETE 를 403 으로 막으므로, 헤더 없이 보내면 "일반 사용자는 403" 테스트가
     * 권한 규칙이 아니라 출처 필터 때문에 통과한다(실측: hasRole("ADMIN") 을 없애도 8개가 초록으로 남았다).
     * 값은 APP_CORS_ALLOWED_ORIGINS 가 없을 때 SecurityConfig 가 쓰는 기본 허용 목록의 http://localhost 다.
     */
    @Bean
    MockMvcBuilderCustomizer allowedOriginByDefault() {
        return builder -> builder.defaultRequest(MockMvcRequestBuilders.get("/").header("Origin", "http://localhost"));
    }
}
