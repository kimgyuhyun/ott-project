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
  // useAuth가 반환한 객체에서 login 속성을 추출해 setAuthUser로 사용한다는 뜻
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
  const handleSubmit = async (e: React.FormEvent) => { // 사용자가가 폼 제출시 실행되는 비동기 함수
    // e: React.FormEvent는 폼 제출 이벤트 객체의 타입이고 브라우저가 폼 제출시 자동으로 전달함
    e.preventDefault(); // 폼 제출시 브라우저가 기본적으로 페에지를 새로고침하는데
    // e.preventDefault()는 폼 제출시 기본 동작을 막는 메서드임 폼 제출시 새로고침 방지함
    // React에서는 SPA라 페이지 새로고침을 막아야함 SPA는 단일 페이지 애플리케이션임(페이지 새로고침 없이 부분만 업데이트)
    setError(''); // 폼 제출시마다 에러메시지 초기화
    setIsLoading(true); // 폼 제출시 로딩 상태를 true로 설정 API 호출은 시간이 걸리고 로딩 표시가 없으면
    // 사용자가 반응이 없다고 느낄 수 있기에 로딩 표시로 "처리 중"임을 알림 사용자 경험을 위해 권장한다는듯함함

    try { // ===는 값과 타입 모두 비교하는것 // try는 handleSubmit 실행 시 바로 truy 블록을 실행함
      // 폼 제출시 try 블록 안의 코드를 실행한다는 뜻뜻
      if (mode === 'login') { // mode가 'login'일떄 실행
        const user = await login(email, password); // login 함수는 lib/api/auth에 정의되어있음 백엔드 API를 호출하는 함수임임
        // loing 함수 호출함 email, password는 위에서 useState로 구조 분해 할당해서 만든 변수들
        // await으로 응답이 올 때까지 대기함 이 아래라인은 일정시간동안 계속 대기함 다른 함수는 실행 가능 (비동기)
        // 사용자가 폼 제출하면 handleSubmit 함수가 실행되고 try 블록이 실행됨
        // login 함수에 사용자가 입력한 값인 email, password를 전달함 그럼 백엔드에서 사용자 정보를 반환해줌
        // 예를 들면 예: { id: 1, username: "홍길동", email: "test@example.com" } 이런 형식으로
        // 저 응답정보를 user 변수에 할당하면 user는 백앤드가 반환한 응답 객체가됨
        if (user) {
          setAuthUser({
            id: String((user as any).id ?? ''),
            username: (user as any).username ?? (user as any).name ?? email,
            email: (user as any).email ?? email,
            profileImage: (user as any).profileImage ?? undefined,
          });
        }
        onSuccess();
      } else {
        if (registerStep === 'email') {
          // 이메일 중복 확인
          const isDuplicate = await checkEmailDuplicate(email);
          if (isDuplicate) {
            setError('이미 사용 중인 이메일입니다.');
            setIsLoading(false);
            return;
          }
          setEmailChecked(true);
          setRegisterStep('verification');
        } else if (registerStep === 'verification') {
          // 인증코드 검증
          await verifyCode(email, verificationCode);
          setEmailVerified(true);
          setSuccessMessage('이메일 인증이 완료되었습니다!');
          setRegisterStep('password');
        } else if (registerStep === 'password') {
          // 회원가입 완료
          await register(email, password, name);
          onSuccess();
        }
      }
    } catch (err) {
      if (mode === 'login') {
        setError('로그인에 실패했습니다. 회원가입이 필요할 수 있습니다.');
      } else {
        setError(err instanceof Error ? err.message : '오류가 발생했습니다.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleEmailCheck = async () => {
    if (!email) {
      setError('이메일을 입력해주세요.');
      return;
    }
    
    try {
      const isDuplicate = await checkEmailDuplicate(email);
      if (isDuplicate) {
        setError('이미 사용 중인 이메일입니다.');
        setEmailChecked(false);
      } else {
        setError('');
        setEmailChecked(true);
      }
    } catch (err) {
      setError('이메일 확인 중 오류가 발생했습니다.');
    }
  };

  const handleSendVerificationCode = async () => {
    if (!email || !emailChecked) {
      setError('이메일을 입력하고 중복 확인을 먼저 해주세요.');
      return;
    }

    setIsLoading(true);
    setError('');
    
    try {
      await sendVerificationCode(email);
      setSuccessMessage('인증코드가 이메일로 발송되었습니다. 이메일을 확인해주세요.');
      setRegisterStep('verification');
    } catch (err) {
      setError(err instanceof Error ? err.message : '인증코드 발송에 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  const switchToRegister = () => {
    setMode('register');
    setRegisterStep('email');
    setError('');
    setEmailChecked(false);
    setEmailVerified(false);
    setSuccessMessage('');
  };

  const switchToLogin = () => {
    setMode('login');
    setError('');
    setEmailChecked(false);
    setEmailVerified(false);
    setSuccessMessage('');
  };

  const resetForm = () => {
    setEmail('');
    setPassword('');
    setName('');
    setVerificationCode('');
    setError('');
    setEmailChecked(false);
    setEmailVerified(false);
    setSuccessMessage('');
    setRegisterStep('email');
  };

  return (
    <div className={styles.emailAuthOverlay}>
      <div className={`${styles.emailAuthContainer} ${styles.emailAuthForm}`}>
        <div className={styles.formHeader}>
          <h2 className={styles.formTitle}>
            {mode === 'login' ? '로그인' : '회원가입'}
          </h2>
          <button
            onClick={onClose}
            className={styles.closeButton}
          >
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.inputGroup}>
            <label htmlFor="email" className={styles.label}>
              이메일
            </label>
            <div className={styles.emailInputGroup}>
              <input
                type="email"
                id="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className={styles.input}
                placeholder="이메일을 입력하세요"
                required
              />
              {mode === 'register' && (
                <button
                  type="button"
                  onClick={handleEmailCheck}
                  className={styles.duplicateCheckButton}
                >
                  중복확인
                </button>
              )}
            </div>
          </div>

          {mode === 'register' && registerStep === 'email' && emailChecked && (
            <div className={styles.inputGroup}>
              <button
                type="button"
                onClick={handleSendVerificationCode}
                className={styles.sendVerificationButton}
              >
                인증코드 발송
              </button>
            </div>
          )}

          {mode === 'register' && registerStep === 'verification' && (
            <div className={styles.inputGroup}>
              <label htmlFor="verificationCode" className={styles.label}>
                인증코드
              </label>
              <input
                type="text"
                id="verificationCode"
                value={verificationCode}
                onChange={(e) => setVerificationCode(e.target.value)}
                className={styles.input}
                placeholder="이메일로 받은 인증코드를 입력하세요"
                required
              />
            </div>
          )}

          {mode === 'register' && registerStep === 'password' && (
            <div className={styles.inputGroup}>
              <label htmlFor="name" className={styles.label}>
                닉네임
              </label>
              <input
                type="text"
                id="name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                className={styles.input}
                placeholder="닉네임을 입력하세요"
                required
              />
            </div>
          )}

          {(mode === 'login' || (mode === 'register' && registerStep === 'password')) && (
            <div className={styles.inputGroup}>
              <label htmlFor="password" className={styles.label}>
                비밀번호
              </label>
              <input
                type="password"
                id="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className={styles.input}
                placeholder="비밀번호를 입력하세요"
                required
              />
            </div>
          )}

          {error && (
            <div className={styles.errorMessage}>{error}</div>
          )}

          {successMessage && (
            <div className={styles.successMessage}>{successMessage}</div>
          )}

          <button
            type="submit"
            disabled={isLoading || (mode === 'register' && registerStep === 'email' && !emailChecked) || (mode === 'register' && registerStep === 'verification' && !verificationCode)}
            className={styles.submitButton}
          >
            {isLoading ? '처리중...' : (
              mode === 'login' ? '로그인' : 
              registerStep === 'email' ? '다음' :
              registerStep === 'verification' ? '인증하기' : '회원가입'
            )}
          </button>
        </form>

        <div className={styles.formFooter}>
          {mode === 'login' ? (
            <button
              onClick={switchToRegister}
              className={styles.modeSwitchButton}
            >
              계정이 없으신가요? 회원가입
            </button>
          ) : (
            <button
              onClick={switchToLogin}
              className={styles.modeSwitchButton}
            >
              이미 계정이 있으신가요? 로그인
            </button>
          )}
        </div>

        {mode === 'register' && (
          <div className={styles.formFooter}>
            <button
              onClick={resetForm}
              className={styles.resetButton}
            >
              처음부터 다시 시작
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
