# Build stage
FROM amazoncorretto:26 AS builder
WORKDIR /app

# Gradle 캐시 레이어 (의존성만)
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# 의존성 다운로드 (소스 변경과 무관)
RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon  # 의존성만 먼저 다운로드

# 소스 코드 레이어
COPY src src

# 빌드
RUN ./gradlew build -x test --no-daemon     # 실제 빌드 실행
# 실행 가능한 bootJar만 고정 이름으로 추출 (Spring Boot가 만드는 -plain.jar 제외)
RUN cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar' | head -n1)" /app/app.jar

# Runtime stage
FROM amazoncorretto:26
WORKDIR /app
COPY --from=builder /app/app.jar app.jar

# 비root 실행 (amazoncorretto:21엔 useradd가 없어 passwd 항목을 직접 추가)
RUN printf 'spring:x:1001:1001::/home/spring:/sbin/nologin\n' >> /etc/passwd \
    && mkdir -p /home/spring \
    && chown -R 1001:1001 /app /home/spring

# 환경변수로 프로파일 설정 (여기에 추가)
ENV SPRING_PROFILES_ACTIVE=dev HOME=/home/spring

USER 1001
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]