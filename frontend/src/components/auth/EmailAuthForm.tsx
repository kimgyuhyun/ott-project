"use client"; // csr
import { useState } from "react";
import { useAuth } from "@/lib/AuthContext"; // lib에서 가져온 인증 상태 관리용 훅
import { login, register, checkEmailDuplicate, sendVerificationCode, verifyCode } from "@/lib/api/auth"; // lib 에서 가져온 API 함수들들
import styles from "./EmailAuthForm.module.css";

type AuthMode = 'login' | 'register'; // typeScript의 타입 정의 AuthMode는 login 또는 register만 가능
// 로그인 / 회원가입 모드드
type RegisterStep = 'email' | 'verification' | 'password'; // typeScript의 타입 정의 RegisterStep는 email, verification, password만 가능
// 회원가입 단계: 이메일 입력 -> 인증코드 발송 -> 인증코드 검증 -> 비밀번호 입력
interface EmailAuthFormProps { // EmailAuthForm 컴포넌트가 받을 props의 타입을 정의 , props 객체 정의
  onClose: () => void; // login/page.tsx에서 handleEmailFormClose 함수를 props로 전달해서 props명 onClose로 받음
  // : () => void는 함수 정의가아니라 함수 타입을 void로 정의한것 
  onSuccess: () => void; // login/page.tsx에서 handleAuthSuccess 함수를 props로 전달해서 props명 onSuccess로 받음
  // 그니까 props 명으로 주고 받는건데 값이 함수일뿐임 여기서는
  isRegister?: boolean; // 회원가입 여부 // 불리언 타입(true 또는 false)
  // ?는 optional 이라 전달안해도됨
}
// 함수를 만들고 외부에서 사용할 수 있게 export하고 default로 이 파일의 메인 함수로 지정함 
// React에서 함수 컴포넌트는 함수임 JSX를 반환하는 함수를 컴포넌트라고 함
// 그니까 보통 함수라고하는데 JSX를 반환할때만 컴포넌트라고함
export default function EmailAuthForm({ onClose, onSuccess, isRegister = false }: EmailAuthFormProps) {
  // login/page.tsx에서 props로 함수를 전달하면 onCloes, onSuccess props 객체들한테 값이 전달되고 이걸 구조 분해 할당으로
  // EmailAUthForm에서 받음 isRegister 기본 값을 false로 설정, 
  // // :EmailAuthFormProps는 props 객체가 EmailAuthFormProps 타입이어햔다는 뜻 TypeScript 타입 검증임
  const { login: setAuthUser } = useAuth(); // 인증 상태 관리 훅 login을 setAuthUser로 이름 변경
  // useAuth가 는 객체를 반환함 그 객체에는 login이라는 함수가 있고 login 함수는 사용자 정보(user 객체)를 받아서 전역 상태에 저장하는 함수임
  // 이 함수를 setAuthUser라는 이름으로 사용한다는뜻뜻
  // useState는 {값, 함수}에 배열을 반환하고 useAuth는 {값들, 함수들}에 객체를 반환함
  // JavaScript는 함수도 객체라고함 보통 함수라고하는거같긴한데 const로 구현한거에 따라서 객체라부를지 함수라 부를지 나뉘어지는듯
  // const 로 시작은 하는데 = { } 중괄호로 화살표 함수 안쓰고 객체를 만듬 코드보면 자바랑 비슷해서 바로 이해 가능함
  // 함수는 = () => { } 화살표 함수를 사용함
  const [mode, setMode] = useState<AuthMode>(isRegister ? 'register' : 'login'); // <>로 TypeScript 제네릭 타입 정의
  // mode는 AuthMode 타입만 값으로 받을수 있음 isRegist에 기본값은 false이므로 'login'이 기본값으로 mode 변수에 할당됨
  // setMode에는 상태 변경 함수가 할당됨
  const [registerStep, setRegisterStep] = useState<RegisterStep>('email');
  // registerStep는 RegisterStep 타입만 값으로 받을수 있음 'email'이 기본값으로 registerStep 변수에 할당됨
  const [email, setEmail] = useState('');
  // 사용자가 입력 전에는 email 값이 없으니 빈 문자열로 초기화해서 변수에 할당
  const [password, setPassword] = useState('');
  // 사용자가 입력 전에는 password 값이 없으니 빈 문자열로 초기화해서 변수에 할당
  const [name, setName] = useState('');
  // 사용자가 입력 전에는 name 값이 없으니 빈 문자열로 초기화해서 변수에 할당
  const [verificationCode, setVerificationCode] = useState('');
  // 사용자가 입력 전에는 verificationCode 값이 없으니 빈 문자열로 초기화해서 변수에 할당
  const [isLoading, setIsLoading] = useState(false); // 초기값 로딩 중 아님 flase로 설정
  const [error, setError] = useState(''); // 초기값 에러 없음 빈 문자열로 초기화해서 변수에 할당
  const [emailChecked, setEmailChecked] = useState(false); // 이메일 중복 확인 완료 여부고 기본값 false
  const [emailVerified, setEmailVerified] = useState(false); // 이메일 인증 완료 여부고 기본값 false
  const [successMessage, setSuccessMessage] = useState(''); // 성공 메시지 빈 문자열로 초기화해서 변수에 할당

  // useEffect가 없으니 콜백 함수 개념은 없음
  const handleSubmit = async (e: React.FormEvent) => { // 사용자가 폼 제출시 실행되는 비동기 함수
    // e: React.FormEvent는 폼 제출 이벤트 객체의 타입이고 브라우저가 폼 제출시 자동으로 전달함
    e.preventDefault(); // 폼 제출시 브라우저가 기본적으로 페에지를 새로고침하는데
    // e.preventDefault()는 폼 제출시 기본 동작을 막는 메서드임 폼 제출시 새로고침 방지함
    // React에서는 SPA라 페이지 새로고침을 막아야함 SPA는 단일 페이지 애플리케이션임(페이지 새로고침 없이 부분만 업데이트)
    setError(''); // 폼 제출시마다 에러메시지 초기화
    setIsLoading(true); // 폼 제출시 로딩 상태를 true로 설정 API 호출은 시간이 걸리고 로딩 표시가 없으면
    // 사용자가 반응이 없다고 느낄 수 있기에 로딩 표시로 "처리 중"임을 알림 사용자 경험을 위해 권장한다는듯함함

    try { // ===는 값과 타입 모두 비교하는것 // try는 handleSubmit 실행 시 바로 truy 블록을 실행함
      // 폼 제출시 try 블록 안의 코드를 실행한다는 뜻
      if (mode === 'login') { // mode가 'login'일때 실행
        const user = await login(email, password); // login 함수는 lib/api/auth에 정의되어있음 백엔드 API를 호출하는 함수임임
        // loing 함수 호출함 email, password는 위에서 useState로 구조 분해 할당해서 만든 변수들
        // await으로 응답이 올 때까지 대기함 이 아래라인은 일정시간동안 계속 대기함 다른 함수는 실행 가능 (비동기)
        // 사용자가 폼 제출하면 handleSubmit 함수가 실행되고 try 블록이 실행됨
        // login 함수에 사용자가 입력한 값인 email, password를 전달함 그럼 백엔드에서 사용자 정보를 반환해줌
        // 예를 들면 예: { id: 1, username: "홍길동", email: "test@example.com" } 이런 형식으로
        // 저 응답정보를 user 변수에 할당하면 user는 백앤드가 반환한 응답 객체가됨
        if (user) { // 만약 user 객체가 있으면, (null이 아니고, undefined도 아니면) // 여기는 user 객체가 있을때만 실행됨됨
          // 백엔드에서 받은 user 객체를 그대로 사용하지않고 그 정보를 기반으로 새 객체를 만드는 이유는
          // 데이터 구조 차이, 데이터 변환 필요, 필드명 차이 처리, 기본값 처리, 타입 안전성
          // 필드명과 타입을 완전히 맞췄어도 백앤드 응답 타입이 명확하지 않을 수 있기에 변환 과정에서 타입 체크를 할 수 있게 변환하는게나음
          setAuthUser({ // useAuth()로 가져온 login 함수를 구조분해 할당 받은 변수로 사용
            // {}는 배열이 아니라 객체를 만드는 문법이고 setAUthUser는 본디 login() 함수였고 이 함수는
            // 하나의 인자만 받는데, 그 인자가user 타입의 객체라서 {}로 객체를 만들고 인자로 넘기려는것임임
            id: String((user as any).id ?? ''), // id는 객체의 속성임 만들 객체에 id라는 속성을 추가한다는뜻
            // Stirng(...)은 JavaScript 내장 함수로 값을 문자열로 변환함
            // (user as any).id에서 user as any: TypeScript에서 user를 any 타입으로 취급(타입 체크 우회)
            // .id: user 객체의 id 속성에 접근 
            // ??:왼쪽이 null 또는 undefined이면 오른쪽 값을 사용 아니면 왼쪽 값을 사용
            // user.id가 null 또는 undefined이면 오른쪽 값인 빈 문자열을 사용
            // 값이 있으면 그 값을 사용하고 Stirng(...)로 문자열로 반환 후 최종 결과를 id 속성에 저장함
            // 그니까 객체를 만들껀데 거기에 id 속성을 추가할꺼고 거기에 값은 user.id를 사용할꺼고 any를 사용해 타입체크를 우회할꺼야
            // 그리고 user.io에 값이 있으면 그 값을 사용할꺼고 없으면 '' 빈 문자열을 사용할꺼고 그 값을 String(...)로 문자열로 반환한 후
            // id에 저장할꺼야라는 뜻
            // login()에 Promise<LoginResponse>이게 없어서 as로 타입우회를해야함 이거는 다시 구현할때 반드시 보완해서 작성해야함
            // Promise<LoginResponse>: 나중에 LoginResponse 타입의 값이 올 거야 라는 뜻 나중에 값이 올 것을 나타내는 객체이며
            // 비동기 작업(시간이 걸리는 작업)의 결과를 다루는 방식임
            // 지금 당장은 없지만 나중에 LoginResponse 타입의 값이 올거야, 즉 비동기 작업이 완료되면 LoginResponse 형태의 데이터를 반환한다는 뜻
            username: (user as any).username ?? (user as any).name ?? email,
            // ?? 연산자를 연쇄 사용하면 왼쪽부터 순서대로 평가하고 null/undefined가 아닌 첫 번쨰 값을 반환하고 
            // 모두가 null/undefined이면 마지막 값을 반환함
            // username이라는 속성을 추가할꺼고 값은 user.username에 값이 있으면 그걸 사용 없으면 user.name을 사용,
            // 그것도 없으면 사용자 폼엥 입력한 email을 사용함함
            email: (user as any).email ?? email,
            // email이라는 속성을 추가할꺼고 백엔드에서 반환된 user.email이 있으면 그걸 사용하고 없으면 사용자 폼애 입력한 email변수를 사용한다는뜻
            profileImage: (user as any).profileImage ?? undefined,
            // profileImage이라는 속성을 추가할꺼고 백엔드에서 반환된 user.profileImage이 있으면 그걸 사용하고 없으면 undefined를 사용함
            // profiledImage는 선택적 속성이라 있어도 되고 없어도 되는 속성임 그래서 값이 없을때 undefiend를 명시적으로 설정정
          }); // setAuthUser 함수 호출 후 사용자 정보 저장
          // 백엔드에 반환된 user 객체를 프론트에서 사용할 user 객체로 반환종료
        } // if문 종료
        onSuccess(); // EmailAuthForm 컴포넌트 닫고 메인페이지로 redirct하는 함수 호출 user 객체 여부에 상관없이 실행됨
        // user 객체가 없어도 실행되서 user 객체가 있을 때만 onSuccess()를 호출하도록 수정하는 것이 좋을듯
      } else { // mode가 login이 아니라 register 일 때 실행
        if (registerStep === 'email') { // 만약 회원가입 단계가 email 단계일 때 실행
          // if가 아니라 else if를 사용한 이유는 단계별로 하나만 실행하려는 의도
          // isRegister는 하나의 값만 갖기때문에 if문으로 해도 오류는 안나겠지만 나중에 코드 변경이나 버그로 예상치못한 상황이 생길 수 있음
          // else if를 쓰면 여러 조건이 동시에 실행되는 것을 방지함 방어적 프로그래밍임임
          // 이메일 중복 확인
          const isDuplicate = await checkEmailDuplicate(email); // 입력 폼의 email 변수를 백엔드로 보내 중복 확인
          // checEmailDuplicate 함수는 email 값 넘겨주면 그걸 DB까지 가져가서 가입됐는지 안됐는지 확인하고 
          // ture/false를 반환함 true면 중복됐다는 뜻 false면 중복되지 않았다는 뜻임
          if (isDuplicate) { // 만약 isDublicate에 값이 true면 중복됐다는 뜻이니까 여기로옴
            setError('이미 사용 중인 이메일입니다.');
            setIsLoading(false); // 로딩 상태 초기화
            return; // 함수 종료
          }
          setEmailChecked(true); // isDublicate가 false면 중복이 아니란 뜻이고 여기로옴
          // 이메일 중복 체크 완료란 뜻
          setRegisterStep('verification'); // 회원가입단계를 이메일 인증 단계로 변경
        } else if (registerStep === 'verification') { // 만약 회원가입 단계가 verification 단계일 때 실행
          // 인증코드 검증
          await verifyCode(email, verificationCode);// 입력폼에 email과 verificationCode를 백엔드로 보내서 인증코드 검증
          // verifyCode 함수는 email을 키로 사용해 해당 사용자의 인증 코드를 찾아 비교하고 true /false를 반환함
          // 그니까 유저가 인증코드 발송 누르면 백엔드에서 인증코드를 입력폼에 email로 보내고 유저는 email에서 그 인증코드를 확인해서
          // 입력폼에 입력하면 그게 verificationCode에 저장되고 프론트에서 이메일과 인증코드를 vertyCode함수에 인자로 태워보내면
          // 백엔드에서 email을 키로 해당 사용자한테 보낸 인증 코드를 찾아 비교하함
          // 성공하면 true를 반환하고 실패 시 Error thorw(catch 블록으로 이동) 성공/실패만 확인하면되서 반환값 저장할 필요가 없음
          // 참고로 verfyCode는 post 요청임 인증 코드는 일회용이라 사용 후 무효화/삭제 처리를 해야하기 떄문
          // GET은 쿼리스트링에 노출되어 로그/히스토리가 남을 수 있고 POST는 요청 본문에 포함되니 더 안전함함
          setEmailVerified(true); // verifyCOde가 true를 반환하면 실행행 이메일 인증여부부 true로 설정
          setSuccessMessage('이메일 인증이 완료되었습니다!'); // 인증 완료 메시지 설정
          setRegisterStep('password'); // 회원가입단계를 비밀번호 입력 단계로 변경
        } else if (registerStep === 'password') { // 만약 회원가입 단계가 password 단계일 때 실행
          // 회원가입 완료
          await register(email, password, name); // 입력폼의 email, password, name을 함수에 전달
          // 백엔드로 post 요청을 보내서 DB에 사용자 정보를 저장함
          onSuccess(); // EmailAuthForm 컴포넌트 닫고 메인페이지로 redirct하는 함수 호출
        }
      } // if-else문 종료
    } catch (err) { // 로그인 모드: 네트워크 ,백엔드 서버, 비밀번호 불일치, 사용자 없을시 여기로옴
      // 회원가입 모드: 네트워크, 백엔드 서버, 인증코드 불일치, 인증코드 만료, 회원가입 실패시 여기로 옴
      // 즉 try 블록 안의 모든 코드에서 에러가 발생하면 여기로옴옴
      if (mode === 'login') { // 만약 mode가 'login'이면
        setError('로그인에 실패했습니다. 회원가입이 필요할 수 있습니다.'); // 에러 메시지 출력
      } else { // 만약 mode가 'login'이 아니라 register 일 때 실행
        setError(err instanceof Error ? err.message : '오류가 발생했습니다.'); // 에러 메시지 출력
        // err instanceOf: err가 Error 타입인지 확인
        // err이 Error 타입이면 err.message 사용
        // Error 타입이 아니면 기본 메시지 사용
      }
    } finally { // 성공 / 실패와 관계없이 무조건 실행
      setIsLoading(false); // 로딩 상태를 false로 초기화
    }
  }; // handleSubmit 함수 종료

  const handleEmailCheck = async () => { // 재할당 불가 비동기 함수 선언 // 회원가입 폼에서 "중복확인" 버튼 클릭 시 실행됨
    if (!email) { // email이 falsy값이면 true // falsy값은 "", 0, null, undefined, false, NaN 등
      setError('이메일을 입력해주세요.');
      return;
    }
    
    try { // email 값이 입력됐으면 실행
      const isDuplicate = await checkEmailDuplicate(email); // 114 라인의 이메일 중복 체크랑 똑같이 동작함
      // 여긴 수동으로 중복확인 버튼 클릭 시 실행되는 곳이고 114라인은 폼 제출시 실행되는 곳이라 중복체크가 이중으로 들어감
      // 나중에 114라인의 이메일중복체크 로직을 삭제해야함
      if (isDuplicate) { // 만약 isDublicate에 값이 true면 중복됐다는 뜻이고 여기가 실행
        setError('이미 사용 중인 이메일입니다.');
        setEmailChecked(false); // 중복됐다는 뜻이니까 이메일 중복 확인 완료 상태를 false로 설정
      } else { // isDublicate가 false면 중복이 아니란 뜻이고 else 문이 실행
        setError('');
        setEmailChecked(true); // 중복이 아니란 뜻이니까 이메일 중복 확인 완료 상태를 true로 설정
      }
    } catch (err) { // try 블록에서 오류가나면 여기로 옴옴
      setError('이메일 확인 중 오류가 발생했습니다.'); // 에러 메시지 출력
    }
  }; // handleEmailCheck 함수 종료

  const handleSendVerificationCode = async () => { // 재할당 불가 비동기 함수 선언 // 인증 코드 발송하는 함수
    if (!email || !emailChecked) { // email이 falsy값이거나 emailChecked가 false면 실행함
      setError('이메일을 입력하고 중복 확인을 먼저 해주세요.');
      return;
    }

    setIsLoading(true); // 로딩 상태를 true로 설정
    setError(''); // 에러 메시지를 빈 문자열로 설정
    
    try {
      await sendVerificationCode(email); // sendVerificationCode 함수에 email을 태워 보내면
      // 입력폼에 email에 인증코드가 발송됨
      setSuccessMessage('인증코드가 이메일로 발송되었습니다. 이메일을 확인해주세요.');
      setRegisterStep('verification'); // 회원가입단계를 이메일 인증 단계로 변경
    } catch (err) {
      setError(err instanceof Error ? err.message : '인증코드 발송에 실패했습니다.');
    } finally {
      setIsLoading(false); // 로딩 상태를 false로 초기화
    }
  };

  const switchToRegister = () => { // 재할당 불가 함수 // 회원가입 폼에 진입할때 모드와 단계를 초기화하는 함수
    // 상태들을 초기화 해줘야함 상태가 남아있으면 문제가 될 수 있음
    // 모드 전환 시 해당 모드에서 사용하는 상태 변수들은 다 초기화해야함
    setMode('register'); // 모드를 회원가입 모드로 변경
    setRegisterStep('email'); // 회원가입단계를 이메일 입력 단계로 변경
    setError(''); // 에러 메시지를 빈 문자열로 설정
    setEmailChecked(false); // 이메일 중복 확인 완료 상태를 false로 설정
    setEmailVerified(false); // 이메일 인증 여부를 false로 설정
    setSuccessMessage(''); // 성공 메시지를 빈 문자열로 설정
  }; // switchToRegister 함수 종료

  const switchToLogin = () => { // 재할당 불가 함수 // 로그인 폼에 진입할때 모드를 로그인 모드로 변경하는 함수
    setMode('login'); // 모드를 로그인 모드로 변경
    setError(''); // 에러 메시지를 빈 문자열로 설정
    setEmailChecked(false); // 이메일 중복 확인 완료 상태를 false로 설정
    setEmailVerified(false); // 이메일 인증 여부를 false로 설정
    setSuccessMessage(''); // 성공 메시지를 빈 문자열로 설정
  }; // switchToLogin 함수 종료

  const resetForm = () => { // 재할당 불가 함수 // 처음부터 다시 시작 버튼 클릭 시 실행
    setEmail(''); // 이메일 입력 필드를 초기화
    setPassword(''); // 비밀번호 입력 필드를 초기화
    setName(''); // 닉네임 입력 필드를 초기화
    setVerificationCode(''); // 인증코드 입력 필드를 초기화
    setError(''); // 에러 메시지를 빈 문자열로 설정
    setEmailChecked(false); // 이메일 중복 확인 완료 상태를 false로 설정
    setEmailVerified(false); // 이메일 인증 여부를 false로 설정
    setSuccessMessage(''); // 성공 메시지를 빈 문자열로 설정
    setRegisterStep('email'); // 회원가입단계를 이메일 입력 단계로 변경
  }; // resetForm 함수 종료

  return ( // JSX 반환 시작
    <div className={styles.emailAuthOverlay}> {/* 젤 바깥 div에 emailAuthOverlay css 클래스스적용*/}
      <div className={`${styles.emailAuthContainer} ${styles.emailAuthForm}`}>
        {/* ``백틱으로 감싸고 ${변수명} 형태로 해줘야 변수 값이 문자열이 아니라 변수로 인식되게해줌
        이렇게 해야 css 클래스를 2개 적용 가능
        보통 컨테이너가 바깥, 폼이 안쪽에 있지만 지금은 오버레이가 맨 바깥임 배경 어둡게, 클릭 시 닫기를 위해서임
        컨테이너는 중앙정렬, 크기조절, 폼은 실제 폼내용
        만약 페이지면 컨테이너 -> 폼임 오버레이 없이 페이지 전체가 모달
        정리하면 로그인 페이지: 페이지처럼 보이게 → 컨테이너 > 폼
        여기기 컴포넌트: 진짜 모달 → 오버레이 > 컨테이너 + 폼*/}
        <div className={styles.formHeader}> {/* formHeader css 클래스 적용*/}
          <h2 className={styles.formTitle}> {/* formTitle css 클래스 적용*/}
            {mode === 'login' ? '로그인' : '회원가입'} {/* mode가 login이면 로그인 아니면 회원가입으로 표시시*/}
          </h2>
          <button // 버튼
            onClick={onClose} // 로그인 페이지에서 props로 전달받은 함수를 porps 객체(onCLose)에 담아둠, 
            // 그 함수를 버튼의 onClick에 전달함. 정확히 말하면 props 인터페이스에 정의해둔 속성(필드)지만 객체라해도 상관없음음
            className={styles.closeButton}
          > {/* closeButton css 클래스 적용*/}
            ✕ {/* X 버튼에 클릭이벤트가 연결됨 */}
          </button>
        </div>

        <form onSubmit={handleSubmit} className={styles.form}> {/* 폼 제출 시 handleSubmit 함수 실행 // form css 클래스 적용*/}
          <div className={styles.inputGroup}> {/* inputGroup css 클래스 적용*/}
            <label htmlFor="email" className={styles.label}>  
              {/*htmlFor="email"은 JSX에서 HTML의 for 속성임 label과 input을 연결하는 속성
              htmlFor="email"은 id="email"인 input과 연결됨
              lable을 클릭하면 연결된 input에 포커스가 감
              input에 정의해둔 id="email"과 연결됨 */}
              이메일
            </label>
            <div className={styles.emailInputGroup}> {/* emailInputGroup css 클래스 적용*/}
              <input // 입력 필드
                type="email" // input 타입을 이메일로 설정 / 이메일 형식 검증 제제공
                id="email" // input의 id를 email로 설정 labe의 htmlFor="email"과 연결
                value={email} // input의 값은 email의 상태변수 / 폼 제출시 전달되는 이메일 값
                onChange={(e) => setEmail(e.target.value)} // onChange{...}는 이벤트 값이 변경될 때 실행되는 이벤트 핸들러임
                // onChange{(e) => 상태변경함수(e.target.value)} 형태로 input값 넘길때 사용하니 외워두는게 나을듯
                // 이벤트 발생 함수를 정의해 onChange에 넣어두면 React가 이벤트 발생 시 자동으로 호출하고
                // 이벤트 객체란 이벤트 발생 시 React가 만드는 객체고 이벤트 정보를 담고있음
                // 이벤트 객체의 주요 속성은 e.target: 이벤트가 발생한 요소
                // e.target.value: 그 요소에 입력된 값 / e.type: 이벤트 타입("Change") / 기타 이벤트 정보들
                // 사용자가 input에 타이핑하면 React가 이벤트 객체를 생성함 여기는 이벤트 정보가 담겨있음
                // React가 파라미터 e로 전달하는데 이때 이벤트 객체 전체를 전달함
                // 그 안에 e.taget이 있고 그 안에 e.target.value가 있는것 요소 안에 요소인 구조
                // 결과적으론 setName에는 e.target.value가 전달되고 파라미터 e에는 이벤트 객체 전체가 전달됨
                // 사용흐름은 React가 onChage를 실행하면 타이핑할 때 만들어진 이벤트 객체가 전달되고, 그 이벤트 객체에서
                // e.target.value를 꺼내서 setName에 전달하는식임
                className={styles.input} // CSS 클래스 적용
                placeholder="이메일을 입력하세요" // 입력 선 표시되는 안내 문구
                required // 필수 입력 속성
              />
              {mode === 'register' && ( // mode가 register 일 때 실행 계정이 없으신가요? 회원가입 버튼 누르면 모드 변경되고 여기로옴
                <button
                  type="button" // 타입을 버튼으로 설정함
                  onClick={handleEmailCheck} // handleEmailCheck 함수 실행
                  // 이메일 중복체크 함수를 onCLick에 연결함
                  className={styles.duplicateCheckButton} // duplicateCheckButton css 클래스 적용
                >
                  중복확인 {/* 위에 동작을 중복확인 버튼에 연결함*/}
                </button>
              )}
            </div> {/* emailInputGroup 종료 */}
          </div> {/* inputGroup 종료 */}

          {mode === 'register' && registerStep === 'email' && emailChecked && (
            // mode가 register고 registerStep이 email이고 emailChecked가 true면 렌더링
            <div className={styles.inputGroup}> {/* inputGroup css 클래스 적용*/}
              <button // 버튼
                type="button" // 타입을 버튼으로 설정함
                onClick={handleSendVerificationCode} // handleSendVerificationCode 함수를 onCLick에 연결함
                // handleSendVerificationCode 함수는 이메일 인증코드를 보내는 함수임임
                className={styles.sendVerificationButton} // sendVerificationButton css 클래스 적용
              >
                인증코드 발송 {/* 위에 동작을 인증코드 발송 버튼에 연결함*/}
              </button>
            </div>
          )}

          {mode === 'register' && registerStep === 'verification' && (
            // mode가 register고 registerStep이 verification이면 렌더링
            <div className={styles.inputGroup}> {/* inputGroup css 클래스 적용*/}
              <label htmlFor="verificationCode" className={styles.label}>
                {/*htmlFor="verificationCode"은 JSX에서 HTML의 for 속성임 label과 input을 연결하는 속성
                input에 정의해둔 id="verificationCode"과 연결됨 */}
                인증코드
              </label>
              <input // 입력 필드
                type="text" // input 타입을 텍스트로 설정 / 텍스트 형식 검증 제공
                id="verificationCode" // input의 id를 verificationCode로 설정 labe의 htmlFor="verificationCode"과 연결
                value={verificationCode} // input의 값은 verificationCode의 상태변수 / 폼 제출시 전달되는 인증코드 값
                onChange={(e) => setVerificationCode(e.target.value)} 
                // onChange{(e) => 상태변경함수(e.target.value)} 형태로 input값 넘길때 사용하니 외워두는게 나을듯
                // 이벤트 발생 함수를 정의해 onChange에 넣어두면 React가 이벤트 발생 시 자동으로 호출하고
                // 이벤트 객체란 이벤트 발생 시 React가 만드는 객체고 이벤트 정보를 담고있음
                // 이벤트 객체의 주요 속성은 e.target: 이벤트가 발생한 요소
                // e.target.value: 그 요소에 입력된 값 / e.type: 이벤트 타입("Change") / 기타 이벤트 정보들
                // 사용자가 input에 타이핑하면 React가 이벤트 객체를 생성함 여기는 이벤트 정보가 담겨있음
                // React가 파라미터 e로 전달하는데 이때 이벤트 객체 전체를 전달함
                // 그 안에 e.taget이 있고 그 안에 e.target.value가 있는것 요소 안에 요소인 구조
                // 결과적으론 setName에는 e.target.value가 전달되고 파라미터 e에는 이벤트 객체 전체가 전달됨
                // 사용흐름은 React가 onChage를 실행하면 타이핑할 때 만들어진 이벤트 객체가 전달되고, 그 이벤트 객체에서
                // e.target.value를 꺼내서 setName에 전달하는식임
                className={styles.input} // CSS 클래스 적용
                placeholder="이메일로 받은 인증코드를 입력하세요" // 입력 선 표시되는 안내 문구
                required // 필수 입력 속성
              />
            </div>
          )}

          {mode === 'register' && registerStep === 'password' && (
            // mode가 register고 registerStep이 password면 렌더링
            <div className={styles.inputGroup}> {/* inputGroup css 클래스 적용*/}
              <label htmlFor="name" className={styles.label}>
                {/*htmlFor="name"은 JSX에서 HTML의 for 속성임 label과 input을 연결하는 속성
                input에 정의해둔 id="name"과 연결됨 */}
                닉네임
              </label>
              <input // 입력 필드
                type="text" // input 타입을 텍스트로 설정 / 텍스트 형식 검증 제공
                id="name" // input의 id를 name로 설정 labe의 htmlFor="name"과 연결
                value={name} // input의 값은 name의 상태변수 / 폼 제출시 전달되는 닉네임 값
                onChange={(e) => setName(e.target.value)} 
                // onChange{(e) => 상태변경함수(e.target.value)} 형태로 input값 넘길때 사용하니 외워두는게 나을듯
                // 이벤트 발생 함수를 정의해 onChange에 넣어두면 React가 이벤트 발생 시 자동으로 호출하고
                // 이벤트 객체란 이벤트 발생 시 React가 만드는 객체고 이벤트 정보를 담고있음
                // 이벤트 객체의 주요 속성은 e.target: 이벤트가 발생한 요소
                // e.target.value: 그 요소에 입력된 값 / e.type: 이벤트 타입("Change") / 기타 이벤트 정보들
                // 사용자가 input에 타이핑하면 React가 이벤트 객체를 생성함 여기는 이벤트 정보가 담겨있음
                // React가 파라미터 e로 전달하는데 이때 이벤트 객체 전체를 전달함
                // 그 안에 e.taget이 있고 그 안에 e.target.value가 있는것 요소 안에 요소인 구조
                // 결과적으론 setName에는 e.target.value가 전달되고 파라미터 e에는 이벤트 객체 전체가 전달됨
                // 사용흐름은 React가 onChage를 실행하면 타이핑할 때 만들어진 이벤트 객체가 전달되고, 그 이벤트 객체에서
                // e.target.value를 꺼내서 setName에 전달하는식임
                className={styles.input} // CSS 클래스 적용
                placeholder="닉네임을 입력하세요" // 입력 선 표시되는 안내 문구
                required // 필수 입력 속성
              />
            </div>
          )}

          {(mode === 'login' || (mode === 'register' && registerStep === 'password')) && (
            // mode가 login이거나 mode가 register고 registerStep이 password면 렌더링
            <div className={styles.inputGroup}> {/* inputGroup css 클래스 적용*/}
              <label htmlFor="password" className={styles.label}>
                {/*htmlFor="password"은 JSX에서 HTML의 for 속성임 label과 input을 연결하는 속성
                input에 정의해둔 id="password"과 연결됨 */}
                비밀번호
              </label>
              <input // 입력 필드
                type="password" // input 타입을 비밀번호로 설정 / 비밀번호 형식 검증 제공
                id="password"
                value={password} // input의 값은 password의 상태변수 / 폼 제출시 전달되는 비밀번호 값
                onChange={(e) => setPassword(e.target.value)} 
                // onChange{(e) => 상태변경함수(e.target.value)} 형태로 input값 넘길때 사용하니 외워두는게 나을듯
                // 이벤트 발생 함수를 정의해 onChange에 넣어두면 React가 이벤트 발생 시 자동으로 호출하고
                // 이벤트 객체란 이벤트 발생 시 React가 만드는 객체고 이벤트 정보를 담고있음
                // 이벤트 객체의 주요 속성은 e.target: 이벤트가 발생한 요소
                // e.target.value: 그 요소에 입력된 값 / e.type: 이벤트 타입("Change") / 기타 이벤트 정보들
                // 사용자가 input에 타이핑하면 React가 이벤트 객체를 생성함 여기는 이벤트 정보가 담겨있음
                // React가 파라미터 e로 전달하는데 이때 이벤트 객체 전체를 전달함
                className={styles.input} // CSS 클래스 적용
                placeholder="비밀번호를 입력하세요" // 입력 선 표시되는 안내 문구
                required // 필수 입력 속성
              />
            </div>
          )}

          {error && ( // error 상태변수가 빈 문자열이 아니면 렌더링
            <div className={styles.errorMessage}>{error}</div>
          )}  {/* errorMessage css 클래스 적용 {error}은 상태변수의 값을 문자열로 표시함 407*/}

          {successMessage && ( // successMessage 상태변수가 빈 문자열이 아니면 렌더링
            <div className={styles.successMessage}>{successMessage}</div>
          )}  {/* successMessage css 클래스 적용 {successMessage}은 상태변수의 값을 문자열로 표시함*/}

          <button
            type="submit" // 타입을 submit으로 설정함 폼제출용 버튼임
            disabled={isLoading || (mode === 'register' && registerStep === 'email' && !emailChecked) || (mode === 'register' && registerStep === 'verification' && !verificationCode)}
            // disabled는 비활성화를 뜻함 버튼은 렌더링되지만, disabled={true}일 뗴 클릭할 수 없음
            // isLoading이 ture 면 조건식이 true가 되고 disalbed가 true가 되서 버튼이 비활성화됨
            // 회원가입 모드에 이메일 단계고 이메일 중복 확인이 안되어있으면 버튼이 비활성화됨
            // 회원가입 모드에  이메일 인증단계고 인증코드가 빈 문자열이면 버튼이 비활성화됨
            className={styles.submitButton} // submitButton css 클래스 적용
          > {/* 버튼 텍스트를 조건에 따라 다르게 렌더링함 삼항 연산자 체인인*/}
            {isLoading ? '처리중...' : ( // isLoading이 true면 처리중... 표시
              mode === 'login' ? '로그인' :  // mode가 login이면 로그인 표시
              registerStep === 'email' ? '다음' : // registerStep이 email이면 다음 표시
              registerStep === 'verification' ? '인증하기' : '회원가입' 
              // registerStep이 verification이면 인증하기 표시 다른값이면 회원가입이 표시
              // 아마 인증하기가 끝나면 registerStep이 password로 변경되고 회원가입버튼으로 폼제출해서 회원가입 성공하는 흐름일듯듯
            )}
          </button>
        </form>

        <div className={styles.formFooter}> {/* formFooter css 클래스 적용*/}
          {mode === 'login' ? ( // mode가 login이면 렌더링링
            <button
              onClick={switchToRegister} // switchToRegister 함수 실행
              // switchToRegister 함수는 회원가입 모드로 전환하는 함수임
              className={styles.modeSwitchButton} // modeSwitchButton css 클래스 적용
            >
              계정이 없으신가요? 회원가입 {/* 위에 동작을 계정이 없으신가요? 회원가입 버튼에 연결함*/}
            </button>
          ) : (
            <button
              onClick={switchToLogin} // switchToLogin 함수 실행
              // switchToLogin 함수는 로그인 모드로 전환하는 함수임
              className={styles.modeSwitchButton} // modeSwitchButton css 클래스 적용
            >
              이미 계정이 있으신가요? 로그인 {/* 위에 동작을 이미 계정이 있으신가요? 로그인 버튼에 연결함*/}
            </button>
          )}
        </div>

        {mode === 'register' && ( // mode가 register이면 렌더링
          <div className={styles.formFooter}> {/* formFooter css 클래스 적용*/}
            <button
              onClick={resetForm} // resetForm 함수 실행
              // resetForm 함수는 폼 초기화하는 함수임
              className={styles.resetButton} // resetButton css 클래스 적용
            >
              처음부터 다시 시작 {/* 위에 동작을 처음부터 다시 시작 버튼에 연결함*/}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
