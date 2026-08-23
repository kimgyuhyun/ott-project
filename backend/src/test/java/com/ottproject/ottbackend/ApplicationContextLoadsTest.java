package com.ottproject.ottbackend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 애플리케이션 컨텍스트가 실제로 조립되는지 검증한다.
 *
 * 왜 이 테스트가 필요한가
 * - 나머지 테스트는 전부 목이거나 슬라이스다. 목 기반 테스트는 스프링을 띄우지 않으므로 빈 배선을
 *   지나가지 않고, 슬라이스는 필요한 조각만 띄우므로 그 밖의 빈이 조립되는지 말하지 않는다.
 *   그래서 빈 순환 참조, @Value 프로퍼티 오타, 설정 클래스 충돌, 자동설정 조건 불일치가
 *   테스트 전량 초록인 채로 남아 배포 시점에야 드러난다.
 * - Flyway 를 켠 채로 실제 PostgreSQL 에 붙이므로 마이그레이션이 처음부터 끝까지 적용되는지도
 *   함께 확인된다. 이건 슬라이스가 못 하는 검증이다 — 슬라이스는 각자 flyway.enabled=false 로
 *   끄고 ddl-auto 로 엔티티에서 스키마를 만들기 때문에 마이그레이션 파일을 한 줄도 실행하지 않는다.
 *   (2026-08-23 실측: flyway_schema_history 62행 = db/migration 의 파일 62개와 일치.)
 *
 * 왜 하나뿐인가
 * - "조립되는가"는 한 번 확인하면 끝나는 종류의 질문이다. 같은 설정으로 열 개를 만들어도 같은 것을
 *   열 번 확인할 뿐이고, 설정이 갈리면 컨텍스트 캐시 키가 갈려 부팅만 그만큼 늘어난다.
 *
 * 왜 본문이 비어 있는가
 * - 컨텍스트 조립은 테스트 메서드가 실행되기 전에 일어난다. 조립이 실패하면 메서드에 도달하지 못하고
 *   깨지므로, 이 메서드가 실행됐다는 사실 자체가 검증 결과다. 여기에 억지로 단언을 넣으면
 *   무엇을 검증하는 테스트인지 오히려 흐려진다.
 * - 마이그레이션도 단언 없이 덮인다. 마이그레이션이 깨지면 Flyway 가 던져 조립이 멈추고,
 *   Flyway 자체가 빠지면 dev 프로파일의 ddl-auto=validate 가 없는 테이블을 잡아낸다.
 *   장치를 빼고 실제로 확인했다(15절): spring.flyway.enabled=false 로 돌리니
 *   SchemaManagementException 으로 실패한다. 통과만 보고 넘긴 것이 아니다.
 *
 * 실행 환경 메모
 * - 브로커는 띄우지 않는다. 리스너 두 개(@KafkaListener, @RabbitListener)의 auto-startup 만 끄면
 *   프로듀서/템플릿 쪽은 지연 연결이라 컨텍스트 조립을 막지 않는다. 브로커까지 컨테이너로 띄우는 것은
 *   이 테스트가 답하려는 질문("빈이 조립되는가")과 무관하게 비용만 올린다.
 * - 스케줄러는 TaskScheduler 를 목으로 갈아끼워 통째로 막는다. @Scheduled 는 등록되지만 목이
 *   아무것도 실행하지 않으므로 백그라운드 작업이 뜨지 않는다.
 *   간격 프로퍼티만 밀어두는 방법은 충분하지 않았다. 그 방식은 프로퍼티로 주입되는 둘(아웃박스 2초,
 *   진행률 10초)만 막고 하드코딩된 크론 다섯은 그대로 남는데, 이것들이 테스트가 끝나고 컨테이너가
 *   내려간 뒤에도 계속 뜬다. 스프링 컨텍스트는 캐시되어 JVM 종료까지 살아있기 때문이다.
 *   실측: 죽은 컨테이너를 향한 커넥션 획득이 30초 타임아웃을 끝까지 태우고
 *   "1 bean still running after timeout: [taskScheduler]" 로 끝나 실행 시간이 1분 10초에서
 *   1분 42초로 늘었다. 15절이 create-drop 을 막은 것과 같은 종류의 낭비다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Tag("testcontainers") // testFast 가 제외하는 태그. 컨테이너와 전체 컨텍스트를 띄우는 값이 비싸다.
@TestPropertySource(
        properties = {
            // 리스너만 끈다. 브로커에 실제로 붙으려 하는 것은 이 둘뿐이다.
            "spring.kafka.listener.auto-startup=false",
            "spring.rabbitmq.listener.simple.auto-startup=false",
            // OAuth2 등록 셋은 client-id 가 비면 OAuth2ClientProperties 가 조립을 거부한다
            // (dev 프로파일의 기본값이 빈 문자열이라 환경변수 없이는 앱이 아예 뜨지 않는다).
            // 값이 유효할 필요는 없다 - 인가 코드 흐름을 타지 않고 빈만 만든다.
            "spring.security.oauth2.client.registration.google.client-id=test",
            "spring.security.oauth2.client.registration.google.client-secret=test",
            "spring.security.oauth2.client.registration.kakao.client-id=test",
            "spring.security.oauth2.client.registration.kakao.client-secret=test",
            "spring.security.oauth2.client.registration.naver.client-id=test",
            "spring.security.oauth2.client.registration.naver.client-secret=test",
        })
class ApplicationContextLoadsTest {

    @MockitoBean
    TaskScheduler taskScheduler; // @Scheduled 배치가 실제로 뜨지 않게 한다 - 클래스 주석 "실행 환경 메모" 참고

    @Container
    @SuppressWarnings("resource") // 컨테이너 수명은 Testcontainers 가 관리한다
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    /**
     * 세션 저장소가 Redis 라서(spring.session.redis.namespace=ott:session) 실제로 붙어야 한다.
     * 띄우지 않아도 컨텍스트는 조립되지만, 연결 실패 스택트레이스가 매 실행마다 40줄씩 찍힌다.
     * 그 노이즈는 출력을 읽지 않는 습관을 만들고, 그러면 진짜 경고도 같이 묻힌다.
     * 이미지 태그는 docker-compose.yml 의 redis 서비스와 맞춘다.
     */
    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Test
    @DisplayName("애플리케이션 컨텍스트가 조립되고 마이그레이션이 적용된다")
    void contextLoads() {
        // 본문이 비어 있는 것이 맞다. 위 주석 "왜 본문이 비어 있는가" 참고.
    }
}
