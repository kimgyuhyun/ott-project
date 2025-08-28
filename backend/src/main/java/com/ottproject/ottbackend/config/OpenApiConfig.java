package com.ottproject.ottbackend.config; // OpenAPI(Swagger) 전역 설정 패키지

import io.swagger.v3.oas.annotations.OpenAPIDefinition; // 전역 OpenAPI 정의 어노테이션
import io.swagger.v3.oas.annotations.info.Contact; // 정보: 연락처
import io.swagger.v3.oas.annotations.info.Info; // 정보: 제목/버전/설명
import io.swagger.v3.oas.annotations.info.License; // 정보: 라이선스
import io.swagger.v3.oas.annotations.servers.Server; // 서버: 접속 URL
import org.springframework.context.annotation.Configuration; // 스프링 설정 클래스

@Configuration // 스프링 빈 구성 클래스
@OpenAPIDefinition( // 전역 OpenAPI 메타데이터 정의
        info = @Info( // 기본 정보
                title = "OTT Backend API", // 문서 제목
                version = "v1", // 문서 버전
                description = "OTT 서비스 백엔드 API 문서입니다. 인증, 플레이어, 리뷰, 즐겨찾기 등 엔드포인트를 제공합니다.", // 설명
                contact = @Contact(name = "Team OTT", email = "support@example.com"), // 문의 연락처
                license = @License(name = "Apache-2.0", url = "https://www.apache.org/licenses/LICENSE-2.0") // 라이선스
        ),
        servers = {}
)
public class    OpenApiConfig { // 전역 OpenAPI 설정 클래스(빈 생성 불필요, 어노테이션만으로 동작)
}


