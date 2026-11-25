package com.ottproject.ottbackend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 수정본 없는 spring-webmvc 6.2.19 취약 기능을 운영 코드가 쓰지 않는지 검증
 *
 * 왜 이 테스트가 필요한가
 * - CVE-2026-47890(SSE 로 뷰 프래그먼트 렌더링 시 스트림 손상)과 CVE-2026-47884(XsltView 원격 코드 실행)는
 *   수정본이 7.0.9(Spring Boot 4)뿐이고, Boot 4 로는 올리지 않기로 했다.
 * - 그래서 backend/.trivyignore 예외와 Dependabot 경고 dismiss 의 근거가 "이 앱은 그 기능을 안 쓴다" 하나다.
 *   이 테스트가 그 근거를 지킨다. 누가 SSE 나 XSLT 뷰를 쓰기 시작하면 여기서 빌드가 깨지고,
 *   그때는 예외를 지우고 위험을 다시 판단해야 한다.
 * - 검사는 단순 이름(토큰)으로 한다. 와일드카드 import 나 FQN 직접 사용도 이름은 소스에 남기 때문이다.
 *
 * 컨테이너를 쓰지 않아 testFast 에 포함된다.
 */
class UnpatchedSpringFeatureGuardTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");

    // SSE 전반(SseEmitter·ServerSentEvent·text/event-stream), 뷰 프래그먼트, XSLT 뷰
    private static final Pattern FORBIDDEN = Pattern.compile("\\b(SseEmitter|ResponseBodyEmitter|ServerSentEvent"
            + "|FragmentsRendering|Xslt\\w*|TEXT_EVENT_STREAM\\w*)\\b|text/event-stream");

    @Test
    @DisplayName("운영 코드에 SSE·뷰 프래그먼트·XSLT 뷰 사용이 없다")
    void mainSourcesDoNotUseUnpatchedFeatures() throws IOException {
        List<Path> javaFiles;
        try (Stream<Path> walk = Files.walk(MAIN_SOURCES)) {
            javaFiles = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
        // 경로가 어긋나 0개를 검사하고 통과하는 것을 막는다.
        assertThat(javaFiles).hasSizeGreaterThan(100);

        List<String> hits = new ArrayList<>();
        for (Path file : javaFiles) {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                Matcher m = FORBIDDEN.matcher(lines.get(i));
                if (m.find()) {
                    hits.add(MAIN_SOURCES.relativize(file) + ":" + (i + 1) + " " + m.group());
                }
            }
        }

        assertThat(hits)
                .as("spring-webmvc 6.2.19 의 수정본 없는 CVE 경로. backend/.trivyignore 예외를 다시 판단할 것")
                .isEmpty();
    }
}
