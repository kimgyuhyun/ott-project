# Build stage
FROM amazoncorretto:21@sha256:540708337af29fda537479f2205d2d219a3780c3f07be5f42be96501789f610b AS builder
WORKDIR /app

# amazoncorretto:21 태그가 Amazon Linux 2 -> 2023으로 올라가며 findutils가 빠졌다.
# Gradle wrapper는 xargs가 없으면 "xargs is not available"로 즉시 죽으므로
# (backend/gradlew 222줄) 직접 넣어준다. COPY 앞에 두어 소스 변경 시 이 레이어는 재사용된다.
RUN yum install -y findutils && yum clean all

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
FROM amazoncorretto:21@sha256:540708337af29fda537479f2205d2d219a3780c3f07be5f42be96501789f610b
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