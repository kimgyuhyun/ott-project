# Build stage
FROM amazoncorretto:21 AS builder
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
RUN ./gradlew build -x test     # 실제 빌드 실행

# Runtime stage
FROM amazoncorretto:21
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

# 환경변수로 프로파일 설정 (여기에 추가)
ENV SPRING_PROFILES_ACTIVE=dev

EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]