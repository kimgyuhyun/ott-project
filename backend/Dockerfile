# jar 를 어디서 가져올지 고른다.
#   builder(기본) = 이 파일 하나로 소스에서 앱까지 만든다. docker-compose.dev.yml 의 개발
#                   스택과, 레지스트리를 못 믿을 때 소스에서 다시 만드는 침해 대응 절차
#                   (_incident_2026-06-20)가 이 경로에 의존하므로 기본값으로 남긴다.
#   ctxjar        = CI 전용. 러너가 테스트까지 마친 bootJar 를 빌드 컨텍스트에서 받는다.
#                   컨테이너 안에서 Gradle 을 다시 돌리지 않아 실측 112초가 빠지고,
#                   테스트를 통과한 산출물이 그대로 배포된다(PLATFORM 7절 CI 순서).
# COPY --from=${ARG} 는 buildkit 이 거부한다("variable expansion is not supported for
# --from"). FROM 으로 한 번 받아 별칭을 만드는 것이 공식 우회다.
ARG JAR_SOURCE=builder

# Build stage
FROM amazoncorretto:21@sha256:ff30d87dbb7b21774b003ad16ec233b3173be069e861803008f87ff829a4e706 AS builder
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
# build.gradle 의 spotbugsMain 이 config/spotbugs-exclude.xml 을 입력으로 잡는다.
# 빠지면 컨테이너 안 ./gradlew build 가 입력 파일 없음으로 죽는다.
COPY config config

# 의존성 다운로드 (소스 변경과 무관)
RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon  # 의존성만 먼저 다운로드

# 소스 코드 레이어
COPY src src

# 빌드
RUN ./gradlew build -x test --no-daemon     # 실제 빌드 실행
# 실행 가능한 bootJar만 고정 이름으로 추출 (Spring Boot가 만드는 -plain.jar 제외)
RUN cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar' | head -n1)" /app/app.jar

# CI 경로: 러너가 만든 jar 를 컨텍스트에서 받는다.
# JAR_SOURCE=builder 일 때 buildkit 이 이 스테이지를 통째로 건너뛰므로, 컨텍스트에
# app.jar 가 없어도 기본 빌드는 영향받지 않는다.
FROM amazoncorretto:21@sha256:ff30d87dbb7b21774b003ad16ec233b3173be069e861803008f87ff829a4e706 AS ctxjar
WORKDIR /app
COPY app.jar /app/app.jar

FROM ${JAR_SOURCE} AS jarsrc

# Runtime stage
FROM amazoncorretto:21@sha256:ff30d87dbb7b21774b003ad16ec233b3173be069e861803008f87ff829a4e706
WORKDIR /app
# --chown 으로 복사 시점에 소유권을 정한다. 뒤에서 chown -R 로 훑으면 106MB jar 가
# 통째로 레이어에 복제돼 이미지가 그만큼 커진다(실측: 1.14GB 중 106MB 가 그 중복분).
COPY --from=jarsrc --chown=1001:1001 /app/app.jar app.jar

# 비root 실행 (amazoncorretto:21엔 useradd가 없어 passwd 항목을 직접 추가)
# chown 에 -R 을 쓰지 않는다. 열어야 하는 것은 디렉터리 /app 하나다 - 앱이 그 안에
# logs/ 를 만들어야 하기 때문이고(application-prod.yml 의 logs/ott-backend.log 는
# 워킹디렉터리 기준 상대경로), 그 안의 jar 는 읽기만 되면 된다.
# prod 는 read_only + /app/logs tmpfs 라 이 소유권이 필요 없지만, e2e·dev 스택은
# read_only 가 없어 컨테이너 쓰기 레이어에 로그를 쓰므로 여기서 열어둬야 한다.
RUN printf 'spring:x:1001:1001::/home/spring:/sbin/nologin\n' >> /etc/passwd \
    && mkdir -p /home/spring \
    && chown 1001:1001 /app /home/spring

# 환경변수로 프로파일 설정 (여기에 추가)
ENV SPRING_PROFILES_ACTIVE=dev HOME=/home/spring

USER 1001
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]