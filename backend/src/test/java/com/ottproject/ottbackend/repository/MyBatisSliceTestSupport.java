package com.ottproject.ottbackend.repository;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * MyBatis 매퍼 XML 을 실제 DB 에 실행하는 슬라이스용 설정
 *
 * JpaSliceTestSupport 와의 차이
 * - 그쪽은 컨텍스트를 띄우기 위한 '껍데기' SqlSessionFactory 다. 쿼리를 실행하지 않는 전제라
 *   DataSource 도 연결 정보가 없다. 매퍼 XML 의 SQL 이 틀려도 아무 테스트도 깨지지 않는다.
 * - 이쪽은 컨테이너 DataSource 에 매퍼 XML 을 실제로 물린다. 손으로 쓴 SQL 의 WHERE 조건을
 *   검증하려면 이 설정을 임포트한다(둘은 같은 이름의 빈을 정의하므로 함께 임포트하지 않는다).
 *
 * 운영 설정(application-*.yml 의 mybatis 절)과 맞춰야 하는 값
 * - mapper-locations: classpath*:/mappers/**\/*.xml
 * - map-underscore-to-camel-case: true
 * 슬라이스에는 MyBatis 자동설정이 없어 yml 이 읽히지 않으므로 여기서 같은 값을 직접 세운다.
 */
@TestConfiguration
public class MyBatisSliceTestSupport {

    @Bean
    SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath*:/mappers/**/*.xml"));
        org.apache.ibatis.session.Configuration cfg = new org.apache.ibatis.session.Configuration();
        cfg.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(cfg);
        return factory.getObject();
    }
}
