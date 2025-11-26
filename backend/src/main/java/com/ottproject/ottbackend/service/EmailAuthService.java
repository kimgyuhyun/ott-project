package com.ottproject.ottbackend.service;

import com.ottproject.ottbackend.dto.AuthRegisterRequestDto;
import com.ottproject.ottbackend.dto.UserResponseDto;
import com.ottproject.ottbackend.entity.User;
import com.ottproject.ottbackend.enums.AuthProvider;
import com.ottproject.ottbackend.enums.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EmailAuthService
 *
 * 큰 흐름
 * - 이메일 기반 회원가입/로그인/중복확인/탈퇴/비밀번호 변경을 처리한다.
 *
 * 메서드 개요
 * - register: 회원가입 처리
 * - login: 로그인 검증 후 사용자 반환
 * - checkEmailDuplicate: 이메일 중복 확인
 * - withdraw: 계정 비활성화(탈퇴)
 * - changePassword: 비밀번호 변경
 */
@Service // 스프링에 Bean에 Service로 등록함 싱글턴 패턴을 사용 / 싱글턴 패턴은 클래스의 인스턴스가 하나만 생성되도록 보장하는것것
@RequiredArgsConstructor // final 필드만 생성자에 파라미터로 받는 생성자를 생성함
@Transactional // 트랜잭션 관리를 위한 어노테이션임 클래스 레벨에 붙이면 모든 메서드에 트랜잭션이 적용됨
// 트랜잭션은 DB 작업을 하나의 단위로 묶는 것, 모든 작업이 성공하면 커밋, 하나라도 실패하면 롤백백
public class EmailAuthService {

	private final UserService userService; // 사용자 서비스 주입
	private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder; // 비밀번호 암호화 주입
	// SPring Security 에서 제공하는 암호화 인터페이스임
	private final com.ottproject.ottbackend.mappers.UserMapper userMapper; // 사용자 매퍼 주입
	// User 엔티티를 UserResponseDto로 변환해줌 MapStruct로 자동 매핑 코드 생성
	// User 엔티티에는 비밀번호 등 민감 정보가 포함되기 때문에 UserResponseDto로 바꿔서 비밀번호를 제외한 안전한 정보만 포함함
	public UserResponseDto  register(AuthRegisterRequestDto requestDto) { // 회원가입 메서드
		// 회원가입 처리하는 메서드고 파라미터로 요청Dto를 받고 UserResponseDto로 반환해줌
		if (userService.existsByEmail(requestDto.getEmail())) {
			// 만약 existByEmail 메서드에 인자로 requestDto에 담있는 email을 태워보냈는데 true가 나오면 이미 가입된 이메일이란뜻
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
			// 상태코드와 에러메시지를 생성자에 인자로 넘겨서 ResponseStatusException 객체를 생성해서 던짐
			// CONFLCT를 넣으면 409 상태 코드가 생성되고 의미는 리소스 충돌(이미 존재하는 리소스)임
		}
		User user = User.createLocalUser(  // existByEmail이 false를 반환하면 중복된게 아니라는 뜻으로 여기가 실행
			// createLocalUser는 static 키워드가 붙은 정적 팩토리 메서드이고 객체를 생성하지않고 클래스명으로 직접 호출이 가능함
			// 생성자로 오버로딩은 파라미터 타입/개수가 달라야하는데 회원가입할 때 필요한 파라미터는 모두 동일함 그래서 생성자 오버로딩은 사용못하고
			// 정적 팩토리 메서드를 생성자 역할로 사용함 메서드 이름으로 역할도 구분가능
			// 생성자로하면 객체 생성시 user만 사용하니까 내가 관리자를 만드는지 로컬유저를 만드는지 구분을못해서 애초에안됨
				requestDto.getEmail(), // 요청객체에서 Email값 꺼내서 파라미터로 전달
				requestDto.getPassword(), // 요청객체에서 Password값 꺼내서 파라미터로 전달
				requestDto.getName() // 요청객체에서 Name값 꺼내서 파라미터로 전달
		); // User 엔티티에 정의된 정적 팩토리 메서드인 createLocalUser로 로컬유저를 생성해서 user 변수에 할당함
		User saveUser = userService.saveUser(user);
		// userService에 saveUser 메서드에 user 객체를 태워보냄
		// saveUser 메서드는 user 객체를 DB에 저장하고 저장된 객체를 반환함
		// 반환된 객체를 saveuser 변수에 할당
		return userMapper.toUserResponseDto(saveUser);
		// userMapper에 toUserResponseDto 메서드에 Db에 저장한 user를태워보내면
		// User 객체가 userResponseDto로 변환되서 반환되고 그 값을 바로 리턴해줌
	}

	public UserResponseDto login(String email, String password) { // 로그인 메서드
		// 로그인 처리하는 메서드고 파라미터로 email이랑 password를 받고 UserResponseDto로 반환해줌
		User user = userService.findByEmail(email)
				.orElseThrow(() -> new RuntimeException("이메일 또는 비밀번호가 올바르지 않습니다."));
		// userService에 findByEmail 메서드에 email을 태워보냄
		// finByEamil 메서드는 email을 키로 넣으면 해당 이메일 매칭되는 user 객체를 Optional<User>로 감싸서 반환하고
		// 이걸 user 변수에 저장함 DB에서 조회할 때 값이 없을 수 있고 User를 직접 반환하면 null이 될 수 있음
		// null 체크를 깜빢하면 NullPointerException이 발생해버림 이걸 방지하기위해 Optional<User>로 감싸써 반환하는거임
		// 값이 있으면 optional로 감싸고 없으면 빈 Optional을 반환하는 것
		// 서비스레이어에 모든 메서드가 Optional을 반환하는건 아니고 DB에 값이 없을 수 있는 단건 조회에만 사용함
		// 저장/수정은 저장할 객체가 이미 있으므로 null일 수 없음
		// 존재 여부는 true/false라 사용안해도됨
		// 목록 조회는 List를 반환해서 빈값은 ([])을 반환해서 사용 안해도됨
		//  .orElseThrow()는 Optional이 비어있으면 예외를 던지고 값이 있으면 그 값을 반환해준다는뜻
		// 그리고 .orElseThorw()에 인자로 람다식을 써서 인자로 태워보내야하는 이유는 
		// .orElseThrow의 파라미터가 타입이 Suppler<>라 그럼  이 타입은 함수를 인자로 받음 이 인자로 넘긴 함수는 값을 받지않음
		// 직접 객체로 넘기거나 값이 있어도 예외가 생성되어버리고 타입도 안맞음
		// 람다식으로 넘기면 값이 없을 때만 함수가 실행되어 예외 생성 값이 있으면 함수가 실행 안됨(지연 평가)
		// 람다식은 함수형 인터페이스들에 구현체임 각 함수형 인터페이스 맞춰 작성하고 넘기면됨
		// Suppller = 인자 없이 값을 반환하는 함수
		// Function = 인자를 받아서 값을 반환하는 함수
		// Consumer = 인자를 받지만 반환값 없는 함수
		// 자바의 람다식은 한 줄일떄 중괄호 생략이 가능하고 68라인이 바로 그 형식으로 작성한것
		// 그러니까 람다식으로 익명함수를 만들고 그 안에 에러메시지 생성하는 객체를 넣어둬서 익명함수 호출하면 에러메시지가 나오는 형식임
		// 그걸 파라미터로 넘긴것
		// 67 ~ 68 라인을 정리하면 userService.findByEmail 메서드에 email을 인자로 태워보내면 Otpional<User>로 감싸서 반환되는데
		// 이 값이 비어있으면 메서드채이닝으로 붙여서 부른 .orElseThrow()메서드에 인자로 보낸 익명 함수에 정의된 에러객체가 생성되고
		// 예외를 던져서 login 메서드 중단됨
		// Optional<User>에 값이 있면 그대로 user 변수에 할당됨
		// 참고로 자바에 람다식은 인자로 함수를 보내야할때 사용됨
		if (!passwordEncoder.matches(password, user.getPassword())) {
			// passwodEncoder.matches 메서드는 인자로 원본 비밀번호(평문) - 로그인 시 입력한 비밀번호,
			// 암호화된 비밀번호- DB에 저장된 해시값을 받아서 비교해주고 일치하면 true, 불일치하면 false를 리턴해줌
			// 그리고 이 조건식은 입력한 비밀번호와 DB에 비밀번호가 불일치하면 false를 반환할텐데 그걸 부정연산자로 true로 바꿔서 실행
			// 즉 비밀번호가 불일치할때 실행되는곳임임
			throw new RuntimeException("이메일 또는 비밀번호가 올바르지 않습니다.");
		}
		// 비밀번호가 맞으면 아래 실행
		if (!user.isEnabled()) {
			// isEnalbed() 메서드는 계정 활성화 여부를 확인하는 메서드임
			// USer 엔티티의의 enabled 필드 값을 반환함
			// true가 나오면  계정 활성화 상태고 false가 나오면 비활성화된 상태임
			// false면 비활성화고 이걸 부정연산자로 true로 치환해서 조건 성립시켜서 실행시킴
			// 즉 비활성화된 계정일때 실행하는것것
			throw new RuntimeException("비활성화된 계정입니다.");
			// 예외객체 던져서 login 메서드 중단시킴
		}
		// 활성화 유저면 실행됨
		return userMapper.toUserResponseDto(user);
		// 로그인 정보로 찾은 user 객체를 userMapper로 유저응답객체로 바꿔서 리턴해줌
	}

	public boolean checkEmailDuplicate(String email) {
		// 이메일 중복 확인 메서드고 파라미터로 이메일값을 받음
		return userService.existsByEmail(email);
		// 그걸 userService에 existByEmail 메서드에 태워넘기면
		// DB에 접근해서 중복인지 아닌지 여부를 ture/false로 반환해주고 그걸 바로 반환하는 형식
	}

	public void withdraw(String email) {
		// 회원탈퇴 처리하는 메서드고 파라미터로 이메일을 받음
		User user = userService.findByEmail(email)
				.orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
		// 자세한 설명은 65라인에 login 메서드 참고
		// findByEmail 메서드에 email 넘겨서 optional<User> 타입으로 User 객체가 Optional에 감싸져 반환되고
		// orElseThrow() 메서드를 체이닝해서 호출했기떄문에 여기서 검증을하는데 optional이 비어있으면 람다식으로 넘긴
		// 예외객체가 생성되고 메서드 중단, 값이 있으면 user 변수에 저장됨
		user.setEnabled(false); // Enalled는 계정 활성화 여부고 false로 email로 가져온 user 객체를 비활성화 세팅함함
		userService.saveUser(user);
		// saveuser 메서드에 user 객체를 태워보넴
		// JPA의 save 메서드는 ID가 있으면 UPDATE를 수행함
		// 이미 존재하는 suer 객체이므로 UPDATE가 실행됨
		// DB에서 enabled 필드가 false로 업데이트되서 비활성화됨
		// 실제로 데이터를 삭제하진않고 활성화 여부만 변경해서 소프트 삭제 처리를함
		// 데이터는 보존되되지만 로그인은 불가능
		// 세션에서 email을 가지고 있고 그걸 사용해야하니 findByEmail을 사용하는것것
		// 이메일을 넣어서 유저 객체 가져오면 id도 같이 가져와짐 그리고 email을 키로 찾을려면 email에 유니크제약을 걸어둬야함
	}

	public void changePassword(String email, String currentPassword, String newPassword) {
		// 비밀번호 변경 처리하는 메서드고 파라미터로 이메일, 현재 비밀번호, 변경할 비밀번호를 받음
		User user = userService.findByEmail(email)
				.orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
		// 자세한 설명은 65라인에 login 메서드 참고
		// findByEmail 메서드에 email 넘겨서 optional<User> 타입으로 User 객체가 Optional에 감싸져 반환되고
		// orElseThrow() 메서드를 체이닝해서 호출했기떄문에 여기서 검증을하는데 optional이 비어있으면 람다식으로 넘긴
		// 예외객체가 생성되고 메서드 중단, 값이 있으면 user 변수에 저장됨
		if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
			// passwordEncoder.matches 메서드는 인자로 원본 비밀번호(평문) - 로그인 시 입력한 비밀번호,
			// 암호화된 비밀번호- DB에 저장된 해시값을 받아서 비교해주고 일치하면 true, 불일치하면 false를 리턴해줌
			// 그리고 이 조건식은 입력한 비밀번호와 DB에 비밀번호가 불일치할때 false를 반환할텐데 그걸 부정연산자로 true로 바꿔서 실행
			// 즉 비밀번호가 불일치할때 실행하는곳임임
			throw new RuntimeException("현재 비밀번호가 올바르지 않습니다.");
		}
		// 비밀번호가 맞으면 아래 실행
		String encodeNewPassword = passwordEncoder.encode(newPassword);
		// passwodEncoder.encode 메서드는 인자로 전달된 평문 비밀번호 암호화해서 해시값을 반환해줌
		// 이 반환값을 encodeNewPassword 변수에 할당
		user.setPassword(encodeNewPassword);
		// 새 비밀번호를 user password 필드에 세팅함함
		userService.saveUser(user); // 변경된 user 객체를 그대로 db에 저장
	}
}
