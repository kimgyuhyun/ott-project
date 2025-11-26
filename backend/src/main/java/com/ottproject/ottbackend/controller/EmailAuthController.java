package com.ottproject.ottbackend.controller;

import com.ottproject.ottbackend.dto.AuthLoginRequestDto;
import com.ottproject.ottbackend.dto.ChangePasswordRequestDto;
import com.ottproject.ottbackend.dto.AuthRegisterRequestDto;
import com.ottproject.ottbackend.dto.UserResponseDto;
import com.ottproject.ottbackend.service.EmailAuthService;
import com.ottproject.ottbackend.service.VerificationEmailService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

/**
 * EmailAuthController
 *
 * 큰 흐름
 * - 이메일 기반 회원가입/중복확인/로그인/로그아웃/비밀번호 변경/탈퇴와 이메일 인증(코드 발송/검증)을 제공한다.
 *
 * 엔드포인트 개요
 * - POST /api/auth/register: 회원가입
 * - GET /api/auth/check-email: 이메일 중복 확인
 * - GET /api/auth/health: 헬스체크
 * - POST /api/auth/login: 로그인(세션)
 * - POST /api/auth/logout: 로그아웃(세션 무효화)
 * - POST /api/auth/send-verification-code: 인증 코드 발송
 * - POST /api/auth/verify-code: 인증 코드 검증
 * - DELETE /api/auth/withdraw: 회원탈퇴(세션 사용자)
 * - PUT /api/auth/change-password: 비밀번호 변경(세션 사용자)
 */

/* 
 프론트에서 백엔드로로 JSON을 보내면 컨트롤러에서 DTO로 받고 서비스에서 DTO 내용을 사용해 ENTITY로 생성/조회하고
 service 레이어에서 엔티티를 저장하고 저장된 엔티티를 DTO로 변환하고 백엔드는 이걸 JSON으로 바꿔서 프론트에 넘겨줌
 이걸 직렬화/역직렬화라고함
 직렬화는 객체 -> JSON 문자열로 변환히는것
 역직렬화는 JSON 문자열을 객체로 변환하는것
*/
@RestController // REST API 컨트롤러로 지정 GET, POST, PUIT, DELETE 등 HTTP 메서드 사용
@RequestMapping("/api/auth") // 모든 엔드포인트의 기본 경로를 /api/auth로 설정함 예: /register -> /api/auth/register
@RequiredArgsConstructor // final 필드만 생성자 파라미터로 받는 생성자를 자동 생성함
public class EmailAuthController {
    private final EmailAuthService emailAuthService; // 인증 관련 비즈니스 로직 (회원가입,로그인 등) 주입
    private final VerificationEmailService verificationEmailService;  // 이메일 인증 코드 발송/검증 주입

    @Operation(summary = "회원가입", description = "이메일/비밀번호/프로필 정보로 신규 계정을 생성합니다.")
    @ApiResponse(responseCode = "200", description = "성공",
            content = @Content(schema = @Schema(implementation = UserResponseDto.class))) // Swagger 문서에 표시할 내용들
    @PostMapping("/register") // POST 요청을 처리하는 엔드포인트 /api/auth/register 
    public ResponseEntity<UserResponseDto> register(@Valid @RequestBody AuthRegisterRequestDto requestDto) {
        // ResponseEntity는 HTTP 응답을 감싸는 객체임 이 객체는 제네릭타입으로 선언되어있고
        // userResponseDto를 타입 매게변수로 넘기면 register 메서드는 ResponseEntity<UserResponseDto) 타입만 반환가능
        // @Valid는 입력 데이터 유효성 검증 에너테이션임 스프링이 검증을 수행해줌
        // @RequesBody는 요청 본문(request body)을 받는 어노테이션이고 요청 본문의 JSON을 AuthRegisterReuqestDto 객체로 변환해서
        // 파라미터에 넣어주는 표시임        
        // requestDto 파라미터에 위에 설명한 2개의 에너테이션을 부여하고 AuthRegisterRequestDto 타입을 지정해줌
        UserResponseDto responseDto = emailAuthService.register(requestDto);
        // emailService에 register 메서드를 호출해서 요청 본문의 JSON을 AuthRegisterReuqest 객체로 변환한걸 인자로 태워보냄
        // 그러면 서비스에서 db까지 접근해서 회원가입 처리 후 UserResponseDto를 반환하고 그 값은 responseDto에 저장됨
        return ResponseEntity.ok(responseDto); // UserResponseDto 객체를 응답 본문에 담아서 보냄
        // JSON 문자열이 아니라 Java 객체임
        // ResponseEntity.ok(responseDto) 여기 구조는 HTTP 상태 코드 200 OK와 응답 본문이 있고
        // 응답 본문은 responsetDto 객체를 JSON 문자열로 변환함 / 헤더는 기본 헤더들이있음
        // 참고로 userResponseDto는 Java 객체인데 스프링이 자동으로 JSON 문자열로 변환해서 HTTP 응답 본문에 넣어서 전송해줌

        // 그러니까 사용자입장에 프론트엔드부터 흐름을 정리하면
        // 프론트엔드에서 유저가 폼에 입력한 값을 Javascript 객체로 만들고 바로 JSON 문자열로 변환함 그리고 이걸 요청 본문에담음
        // 그럼 요청본문에는 json이 담겨져있고 이게 백엔드 api로 전달됨 그러면 @RequestBody가 요청 본문에서 json을
        // 파라미터에 정의되어있는 객체로 변환해서 파라미터로 넣어줌 그러면 이 파라미터를  Di로 주입해둔 서비스로 태워보내면면
        // service에 메서드가가 DB까지 접근해서 회원가입 처리를하고 그 리턴값을 responseDto에 저장함
        // register 메서드는 requestDto를 넘기면 responseDto를 반환해줌
        // 그다음 ResponseEntity.ok(responseDto)를 리턴해주는데 이때 상태코드로 200이 들어가고
        // 응답 본문에는 자바객체가 들어있는데 스프링이 이걸 자동으로 JSON 문자열로 변환해서 응답 본문에 넣고 전송해줌
        // 그럼 프론트쪽에서 이 응답본문을 받을테고 response에 저장됨 그럼 response.json()을 호출해서
        // 응답 본문에 있는 json 문자열을 Javascript 객체로 변환해서 사용함
        // 이렇게 젤 바깥 입장에서 보면 둘다 json 문자열을 보내고 반환하기에 json 상하차라고부름
    }

    @Operation(summary = "이메일 중복 확인", description = "주어진 이메일이 사용 중인지 여부 반환")
    @ApiResponse(responseCode = "200", description = "중복 여부 반환") // Swagger 문서에 표시할 내용들
    @GetMapping("/check-email") // GET 요청을 처리하는 엔드포인트 /api/auth/check-email 
    public ResponseEntity<Boolean> checkEmailDuplicate(@Parameter(description = "이메일") @RequestParam String email) {
        // ResponseEntity는 HTTP 응답을 감싸는 객체임 이 객체는 제네릭타입으로 선언되어있고
        // Boolean을 타입 매게변수로 넘기면 checkEmailDuplicate 메서드는 ResponseEntity<Boolean) 타입만 반환가능 true/false
        // @Parameter(description = "이메일")은 Swagger 문서용 어노테이션임 APi 문서에 파라미터 설명을 표시함
        // @RequestParam이 쿼리 파라미터를 받는 어노테이션임
        // 우선 GET 요청이니까 요청본문은없고 기본 Headers에 url에 쿼리파라미터로 정보를 전달했을꺼고
        // 스프링이 URL에서 쿼리 파라미터를 추출해서 email 파라미터에 넣어줌
        boolean isDuplicate = emailAuthService.checkEmailDuplicate(email);
        // service에 checkEmailDuplicate 메서드를 호출해서 쿼리 파라미터를 추출한 값을 넘기면
        // DB까지 접근해서 중복 여부 확인하고 true 혹은 false를 리턴해줌
        return ResponseEntity.ok(isDuplicate); // boolean 값을 ResponseEntity로 감싸써 보내면
        // 안에 상태 코드 200이랑 응답 본문이 들어있고
        // 응답 본문은 JSON 문자열로 반한되서 들어가고 전송됨
    }

    @Operation(summary = "헬스체크", description = "인증 API 상태 확인")
    @ApiResponse(responseCode = "200", description = "정상") // Swagger 문서에 표시할 내용들
    @GetMapping("/health") // GET 요청을 처리하는 엔드포인트 /api/auth/health 
    public ResponseEntity<String> health() {
        // ResponseEntity는 HTTP 응답을 감싸는 객체임 이 객체는 제네릭타입으로 선언되어있고
        // String을 타입 매게변수로 넘기면 health 메서드는 ResponseEntity<String) 타입만 반환가능
        // GET 요청이니까 요청본문은 없을꺼고 인증 API 상태만하는거라 인자도 안받음
        return ResponseEntity.ok("Auth API is running!");
        // 서버가 정상적으로 동작하고 있고 API 찌르면 응답을 잘하는지 확인하는 메서드
    }

    @Operation(summary = "로그인", description = "세션 기반 로그인 수행")
    @ApiResponse(responseCode = "200", description = "성공", 
            content = @Content(schema = @Schema(implementation = UserResponseDto.class))) // Swagger 문서에 표시할 내용들
    @PostMapping("/login") // POST 요청을 처리하는 엔드포인트 /api/auth/login 
    public ResponseEntity<UserResponseDto> login(@Valid @RequestBody AuthLoginRequestDto requestDto, HttpSession session) {
        // ResponseEntity는 HTTP 응답을 감싸는 객체임 이 객체는 제네릭타입으로 선언되어있고
        // UserResponseDto를 타입 매게변수로 넘기면 login 메서드는 ResponseEntity<UserResponseDto) 타입만 반환가능
        // @Valid는 입력 데이터 유효성 검증 에너테이션임 스프링이 검증을 수행해줌
        // @RequestBody는 요청 본문(request body)을 받는 어노테이션이고 요청 본문의 JSON을 AuthLoginRequestDto 객체로 변환해서
        // 파라미터에 넣어주는 표시임
        // requestDto 파라미터에 위에 설명한 2개의 에너테이션을 부여하고 AuthLoginRequestDto 타입을 지정해줌
        // HttpSession은 세션 데이터 저장/조회를함 프론트에서 credentials: 'include' 옵션으로 쿠키를 보내면
        // 스프링이 자동으로 세션을 찾아서 주입해줌 그게 session 파라미터에 할당됨
        // 정확히 쿠키에서 세션 ID를 읽어서 -> 서버에서 저장된 세션을 찾아서 -> HttpSession 객체로 주입해줌
        // 그니까 프론트에서 보내준 쿠키에서 세션 ID를 읽고 그걸로 서버에 저장된 세션을 찾아서 HttpSession 객체로 주입해준다는거임
        // 세션은 브라우저에서 웹서버에 연결될때 자동으로 생성되고 연결이 끊기지 않는한 유지
        // 세션 종료 조건은 타임아웃, 브라우저 종료, 명시적 무효화가 있음
        UserResponseDto responseDto = emailAuthService.login(requestDto.getEmail(), requestDto.getPassword());
        // service에 login 함수에 requestDto객체에 Email값과 Password 값을 넘기면 DB까지 가서 로그인 처리를하고
        // UserResponseDto 객체를 반환하고 그 값을 responseDto에 저장함
        session.setAttribute("userEmail", requestDto.getEmail());
        // setAttribute 메서드는 세션에 키와 값을 저장하는 메서드임
        // setAttribue 메서드에 인자로 키 "userEmail" 에 값으로로 요청본문에 json을 Java 객체로 바꿔서넣은 변수에서 Email을 값으로 태워보냄
        // 그럼 session에 키와값이 설정됨
        // 로그인에 성공하면 로그인한 email이 UserEmail에 키에 값으로 저장됨
        // 이후 요청은 세션에서 이메일을 조회해서 처리함
        // 또 이이걸로 사용자 식별해서 회원탈퇴/비밀번호 변경 등 처리가 가능
        // 그러니까 로그인 시 요청 본문에서 이메일을 받아서 세션에 저장해두면 이후 요청 시 세션에서 이메일을 가져와서 사용가능함
        return ResponseEntity.ok(responseDto);
        // ResponseEntity.ok(responseDto)를 리턴해주는데 이때 상태코드로 200이 들어가고
        // 응답 본문에는 responseDto 객체를 JSON 문자열로 변환해서 넣어줌 이걸 전송

    }

    @Operation(summary = "로그아웃", description = "세션 무효화")
    @ApiResponse(responseCode = "200", description = "성공") // Swagger 문서에 표시할 내용들
    @PostMapping("/logout") // POST 요청을 처리하는 엔드포인트 /api/auth/logout 
    public ResponseEntity<String> logout(HttpSession session) {
        // ResponseEntity는 HTTP 응답을 감싸는 객체임 이 객체는 제네릭타입으로 선언되어있고
        // String을 타입 매게변수로 넘기면 logout 메서드는 ResponseEntity<String) 타입만 반환가능
        // HttpSession은 세션 데이터 저장/조회를함 프론트에서 credentials: 'include' 옵션으로 쿠키를 보내면
        // 스프링이 자동으로 세션을 찾아서 주입해줌 그게 session 파라미터에 할당됨
        // 본문이 없는데 POST 요청을 사용한 이유는 상태 변경 작업이고 세션 무효화를 해서 서버 상태를 변경해서임
        // GET은 브라우저 히스토리에 남거나 캐시될 수 있고 POST는 상태 변경 작업에 더 안전함
        // 로그인/로그아웃 같은 인증 작업은 보통 POST 사용함
        // POST는 변경이나 생성 작업을함
        session.invalidate(); // 프론트에서 전달받은 세션을 무효화해서 로그아웃 처리함
        return ResponseEntity.ok("로그아웃되었습니다.");
        // ResponseEntity.ok("로그아웃되었습니다.")를 리턴해주는데 이때 상태코드로 200이 들어가고
        // 응답 본문에는 "로그아웃되었습니다." 문자열이 들어있고 전송됨
    }


    @Operation(summary = "이메일 인증코드 발송", description = "입력 이메일로 인증코드 전송")
    @ApiResponse(responseCode = "200", description = "발송됨") // Swagger 문서에 표시할 내용들
    @PostMapping("/send-verification-code") // POST 요청을 처리하는 엔드포인트 /api/auth/send-verification-code 
    public ResponseEntity<String> sendVerificationCode(@Parameter(description = "이메일") @RequestParam String email) {
        // ResponseEntity는 HTTP 응답을 감싸는 객체임 이 객체는 제네릭타입으로 선언되어있고
        // String을 타입 매게변수로 넘기면 sendVerificationCode 메서드는 ResponseEntity<String) 타입만 반환가능
        // @Parameter(description = "이메일")은 Swagger 문서용 어노테이션임 APi 문서에 파라미터 설명을 표시함
        // @RequestParam이 쿼리 파라미터를 받는 어노테이션임
        // 스프링이 HTTP 요청에 URL에서 쿼리 파라미터를 추출해서 email 파라미터에 넣어줌
        verificationEmailService.sendVerificationEmail(email);
        // HTTP 요청에서 추출한 이메일을 verificationEmailService에 sendVerificationEmail 메서드에 태워보냄
        // sendVerificationEmail 메서드는 이메일을 받아서 6자리 인증 코드를 생성하고 이메일과 코드를 매핑해서 메모리에 저장, 이메일 발송함
        // 여기서 메모리에 저장한다는거는 JVM에 힙 메모리 영역을 말함 서버 애플리케이션이 실행되는 서버의 메모리임 재시작하면 사라짐
        // 실제 운영시에는 Redis에 저장하는게 좋다고함 서버 재시작 시 데이터 유지, 여러 서버 인스터간 공유 가능, TTL(만료 시간)설정 가능하다함
        return ResponseEntity.ok("인증 코드가 발송되었습니다. 이메일을 확인해주세요.");
        // ResponseEntity.ok("인증 코드가 발송되었습니다. 이메일을 확인해주세요.")를 리턴해주는데 이때 상태코드로 200이 들어가고
        // 응답 본문에는 "인증 코드가 발송되었습니다. 이메일을 확인해주세요." 문자열이 들어있고 전송됨
    }

    @Operation(summary = "인증코드 검증", description = "이메일/코드로 인증 여부 확인") //
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "인증 성공"),
            @ApiResponse(responseCode = "400", description = "실패") // Swagger 문서에 표시할 내용들
    })
    @PostMapping("/verify-code") // POST 요청을 처리하는 엔드포인트 /api/auth/verify-code 
    public ResponseEntity<String> verifyCode(@Parameter(description = "이메일") @RequestParam String email,
                                             @Parameter(description = "인증코드") @RequestParam String code) {
         // ResponseEntity는 HTTP 응답을 감싸는 객체임 이 객체는 제네릭타입으로 선언되어있고
         // String을 타입 매게변수로 넘기면 verifyCode 메서드는 ResponseEntity<String) 타입만 반환가능
         // @Parameter는 Swaager 설명용임
         // 파라미터러 email,과 code를 받고 여기에 @RequestParam 을 부여해놔서 스프링이 자동으로 HTTP 요청에 URL에서
         // 쿼리 파라미터만 추출해서 각각 해당하는 파라미터에 저장해줌
         // 참고로 @reuqestParam 어노테이션이 HTTP 요청에 URL에서 값을 찾아서 변수에 할당해주는 방식은
         // @ReuqestParam String email 이렇게 작성하면 쿼리 파라미터의 "email" 키에 해당하는 값을 찾아서 email 변수에 넣어주는것임
        boolean isVerified = verificationEmailService.verifyCode(email, code);
        // verificationEmailService에 verifyCode 메서드에 email과 code를 태워보냄
        // verifyCode 메서드는 email을 키로 사용해 해당 사용자의 인증 코드를 찾아 비교하고 true /false를 반환함
        // 그 값을 isVerified 변수에 할당함 아까 JVM 힙 메모리 영역에 저장해놨던던 코드를 찾아서 반환하는거임
        if (isVerified) { // 만약 isVerifield가 true면 실행행
            return ResponseEntity.ok("이메일 인증이 완료되었습니다.");
        } else { // 만약 isVerifield가 false면 실행행
            return ResponseEntity.badRequest().body("인증 코드가 올바르지 않습니다.");
        }
    }

    @Operation(summary = "회원탈퇴", description = "세션 사용자 탈퇴")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "탈퇴 완료"),
            @ApiResponse(responseCode = "400", description = "로그인 필요") // Swagger 문서에 표시할 내용들
    })
    @DeleteMapping("/withdraw") // DELETE 요청을 처리하는 엔드포인트 /api/auth/withdraw 
    public ResponseEntity<String> withdraw(HttpSession session) {
        // ResponseEntity는 HTTP 응답을 감싸는 객체임 이 객체는 제네릭타입으로 선언되어있고
        // String을 타입 매게변수로 넘기면 withdraw 메서드는 ResponseEntity<String) 타입만 반환가능
        // HttpSession은 세션 데이터 저장/조회를함 프론트에서 credentials: 'include' 옵션으로 쿠키를 보내면
        // 스프링이 자동으로 세션을 찾아서 주입해줌 그게 session 파라미터에 할당됨
        String userEmail = (String) session.getAttribute("userEmail");
        // 로그인시 setAttribute 메서드로 저장할때 Object 타입으로 자동 업캐스팅됨 왜냐하면 value를 Object 타입으로 받기 떄문임
        // 그걸 꺼내서 사용하려면 명시적 다운캐스팅을 해야하고 string으로 다운캐스팅하고 userEmail 변수에 할당한것것
        if (userEmail == null) { // 만약 userEmail이 null이면 실행
            return ResponseEntity.badRequest().body("로그인이 필요합니다.");
        }
        emailAuthService.withdraw(userEmail); // userEmail 값이 null이 아니면 실행
        // emailAuthService에 withdraw 메서드에 userEmail 값을 태워보냄
        // withdraw 메서드는 userEmail을 키로 사용해 해당 사용자의 계정을 비활성화(탈퇴) 처리함
        session.invalidate(); // 연결된 세션을 무효화함
        return ResponseEntity.ok("회원탈퇴가 완료되었습니다.");
        // 프론트에 응답을 보내줌
    }

    @Operation(summary = "비밀번호 변경", description = "세션 사용자 비밀번호 변경")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "변경 완료"),
            @ApiResponse(responseCode = "400", description = "로그인 필요") // Swagger 문서에 표시할 내용들
    })
    @PutMapping("/settings/change-password") // PUT 요청을 처리하는 엔드포인트 /api/auth/settings/change-password 
    public ResponseEntity<String> changePassword(HttpSession session, @RequestBody ChangePasswordRequestDto requestDto) {
        // ResponseEntity는 HTTP 응답을 감싸는 객체임 이 객체는 제네릭타입으로 선언되어있고
        // String을 타입 매게변수로 넘기면 changePassword 메서드는 ResponseEntity<String) 타입만 반환가능
        // HttpSession은 세션 데이터 저장/조회를함 프론트에서 credentials: 'include' 옵션으로 쿠키를 보내면
        // 스프링이 자동으로 세션을 찾아서 주입해줌 그게 session 파라미터에 할당됨
        // @RequestBody는 요청 본문(request body)을 받는 어노테이션이고 요청 본문의 JSON을 ChangePasswordRequestDto 객체로 변환해서
        // 파라미터에 넣어주는 표시임
        // requestDto 파라미터에 위에 설명한 2개의 에너테이션을 부여하고 ChangePasswordRequestDto 타입을 지정해줌
        String userEmail = (String) session.getAttribute("userEmail");
        // 로그인되어있는 세션에서 이메일을 조회해서 userEmail 변수에 할당한것
        if (userEmail == null) { // 만약 userEmail이 null이면 실행
            return ResponseEntity.badRequest().body("로그인이 필요합니다.");
        }
        emailAuthService.changePassword(userEmail, requestDto.getCurrentPassword(), requestDto.getNewPassword());
        // emailAuthService에 changePassword 메서드에 userEmail, currentPassword, newPassword를 태워보냄
        // changePassword 메서드는 userEmail을 키로 사용해 해당 사용자를 찾고 비밀번호를 변경해서 저장함함
        return ResponseEntity.ok("비밀번호가 성공적으로 변경되었습니다.");
        // 프론트에 응답을 보내줌
    }

}
