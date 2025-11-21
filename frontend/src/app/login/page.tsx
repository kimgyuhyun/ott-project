"use client";
// next.js는 기본이 서버 컴포넌트로 ssr이라서 상태/이벤트핸들러 사용이 불가함
// use client로 클라이넌트 컴포넌트로 명시해서 브라우저에서 실행하게해 useState, useEffect, 이벤트 핸들러 사용가능하게함
import { useState, useEffect } from "react"; // 리엑트 훅을 사용하기 위해 import
import Image from "next/image"; // <img> 대신 사용하면 자동 최적화가 적용됨
// @는 src의 별칭임임
import Modal from "@/components/ui/Modal"; // 모달 기본 컴포넌트
import PosterWall from "@/components/auth/PosterWall"; // 포스터 배경 컴포넌트
import SocialButton from "@/components/auth/SocialButton"; // 소셜 버튼 컴포넌트
import EmailAuthForm from "@/components/auth/EmailAuthForm"; // 이메일 로그인/회원가입 폼 컴포넌트
import styles from "./login.module.css"; // 로그인 페이지 스타일


export default function LoginPage() { // 이 함수를 다른 파일에서 import 할 수 있게함 
// default는 기본 export(이 파일의 메인 함수) next.js가 이 함수를 페이지 컴포넌트로 인식함함 
// const는 재할당 불가 useState로 만든 값은 setOpen()으로 변경 가능함
// 구조 분해 할당
// 동작 흐름 
// 1. usState(true): 컴포넌트 처음 렌더링 시 한번만 호출 -> [true, 함수] 반환
// 2. setOpen(false): 함수 호출 -> open 값을 false로 변경 -> 반환값 없음
// 3. React가 상태 변경을 감지하고 컴포넌트를 다시 렌더링 -> open이 false인 상태로 화면 업데이트
// 만약에 상태가 true로 바뀌면 React가 다시 상태 변경 감지해서 재 랜더링해서 open 상태로 화면 업데이트해줌
  const [open, setOpen] = useState(true); // open은 현재 상태 값이고 setOpen은 상태를 변경하는 함수임 
  // useState(true)는 [true, 함수]를 배열로 반환함 첫 번쨰값은 변수 open, 두 번째 상태 변경 함수는 setOpen에 할당됨
  // oepn은 변수 setOpen은 상태 변경 함수가됨
  // useSete에 초기값을 true로 설정함 setOpen(false)를 호출하면 useState의 값이 false로바뀜
  const [showEmailForm, setShowEmailForm] = useState(false);  // 상태 변경 함수
  // 기본값은 false로 숨겨진 상태
  const [oauthUrls, setOauthUrls] = useState({
    kakao: "", // 초기값: 빈 문자열임 이유는
    google: "", // 페이지 로드 시점에는 URL을 모르고 useEffect에서 API로 받아온 URL로 업데이트됨
    naver: "" // 초긱밧은 임시값임임
  });

  // 화살표 함수: 함수를 정의하는 방법 중 하나 () => { ... } 형태
  // 1. 인자로 전달 useEffect(() => {...}) // 함수를 바로 만들어서 인자로 전달
  // 2. 변수에 할당 const loadLoginUrIs = () => { ... } // 함수를 만들어서 변수에 저장장
  // (): 파라미터 부분(입력)
  // =>: 화살표 (함수라는 의미)
  // { ... }: 함수 본문(실행할 코드)
  // () => { ... } 는 함수 정의 문법법
  // useEffect() 안에 () => { 콜백함수}가 첫번째 인자로 넘어가고 그 다음 [] 의존성 배열이 두번째 인자로 넘어감
  // 이 함수를 useEffect의 첫 번쨰 인자로 전달
  // 컴포넌트가 화면에 나타난 후, 이 함수를 한 번 실행해줘

  useEffect(() => { // React 훅: 컴포넌트가 랜더링 된 후 실행하는 함수, 페이지 로드 시 한 번 실행(의존성 배열[])
    // 백엔드 컨트롤러에서 로그인 URL을 받아와 사용 (도커/프록시 환경 일관성 확보)
    // () => {: 화살표 함수 시작(랜더링 후 실행할 코드) useEffect의 콜백함수
    // 콜백 함수: React가 나중에 호출(call back)하는 함수, 랜더링 후 React가 실행
    // 화살표 함수로 콜백 함수를 정의해 useEffect에 전달하고, 두 번째 인자로 의존성 배열을 넣음
    // 인자로 전달 (콜백) useEffect(() => { ... })  // 함수를 인자로 전달
    // 첫 번째 스코프: 함수 정의 문법으로 스코프를 열기 ( 콜백 함수)
    // useEffect 의 첫 번째 인자 = 익명 함수
    // 그 안에 중첩 함수가 있는 구조
    const loadLoginUrls = async () => { // 재할당 불가 loadLoginUrls는 함수명임
      // 여기에 화살표 함수는 함수 정의
      // async는 비동기 함수 키워드 () => {: 화살표 함수 문법
      // 비동기 화살표 함수를 loadLoginUrls 변수에 할당 async로 await 사용 가능 (API 호출 대기)
      // async: 병렬처리로 다른 작업을 막지 않고 동시에 실행된다는 뜻 예로는 API 호출 중에도 화면은 계속 반응함
      // awit: 비동기 작업이 끝날때 까지 기다림 async 함수 안에서만 사용 가능
      // 변수에 할당 (함수 정의) const loadLoginUrls = async () => { ... }  // 변수에 함수 할당
      // 두 번째 스코프: 함수 정의 스코프 // 스코프 안에 스코프를 여는 중첩 구조
      // async/await을 쓰려면 중첩 스코프를 사용해야함
      // API 호출이 많으면 중첩 스코프 / 간단한 코드면 단일 스코프
      // 코드가 길어지거나 재사용이 필요하면 중첩 스코프 사용 가능능
       try { // API 호출 시도
        const res = await fetch('/api/oauth2/login-urls', { credentials: 'include' });
        // 재할당 불가 변수 res API 응답을 저장함
        // await은 비동기 작업이 끝날 때까지 대기하고 fetch가 응답을 받을 때까지 기다림
        //  fetch('/api/oauth2/login-urls',...) HTTP 요청 함수 /api/oauth2/login-urls로 GET 요청
        // 백앤드 API 엔드포인트 컨트롤러에 있음
        // credentials: 'include': 쿠키를 포함해 요청 전송(세션 인증용)
        // await fetch로 요청하면 응답값이 res에 값이 들어올 때까지 대기함
        // 응답값이 안오면 계속 대기하고 응답값이 오면 다음줄 실행함
        // 무제한 대기는 아니고 브라우저 기본값을 따름 에러 발생시 catch로 이동동
        if (!res.ok) throw new Error('failed'); // res.ok가 true면 올바른 응답 !로 false로 치환 다음 라인 실행
        // res.ok가 false면 !로 true로 치환 if문 성립 예외처리
        const data = await res.json(); // 응답객체.json(): 응답 객체에서 JSON을 파싱하는 함수
        // data: 파싱된 JSON 데이터를 저장하는 변수
        if (data && data.loginUrls) { // 만약 data가 있고 그 객체 안의 loginUrls 속성이 있으면 실행
          // 만약 false가 뜨면 catch로 이동은 안하고 아래 기본값 설정 코드만 실행됨
          setOauthUrls({ // 상태 변경 함수 호출 // 콜백 함수 안에서 상태를 재정의하는 방식
            // java는 논리합으로 boolean 값을 반환하지만 javascript에 ||는 값 반환(기본값 설정에 사용)
            kakao: data.loginUrls.kakao || `/login/oauth2/authorization/kakao`,
            // Truthy: true로 평가되는 값 (예: "문자열", 1, {}, [])
            // Falsy: false로 평가되는 값 (예: false, 0, "", null, undefined)
            // 왼쪽 값이 있으면 왼쪽 없으면 오른쪽(기본값) 사용
            // 삼항연산자와 비슷하지만 더 간단한 문법인듯
            // 백엔드에서 받은 값이 있으면 그걸 쓰고, 없으면 기본값을 사용함함
            google: data.loginUrls.google || `/login/oauth2/authorization/google`,
            naver: data.loginUrls.naver || `/login/oauth2/authorization/naver`
          });
          return; // 함수 종료 return이 없으면 API 성공 후에도 아래 기본값 설정 코드가 실행됨
          // return이 있으면 API 성공 시 함수 종료, 기본값 설정 코드는 실행 안됨
        }
      } catch { // res.ok = false -> !로 true로 치환되서 조건성립 예외처리로 catch로 이동
        // 실패 시 기본값으로 폴백
        // FallBack: 대체/백업이란 의미 코드에서 APi 실패 시 기본값으로 대체
      }
      setOauthUrls({ // data가 null이거나 loginUrls 요소가 없으면 여기로 바로옴
        kakao: `/login/oauth2/authorization/kakao`,
        google: `/login/oauth2/authorization/google`,
        naver: `/login/oauth2/authorization/naver`
      }); // 페이지 초기에는 빈문자열이지만 페이지 렌더링 후에 useEffect 실행행하고 기본값 또는 API 값이 들어감
      // 기본값 폴백 덕분에 API 요청이 실패 시에도 기본값으로 세팅되어있음
    }; // loadLoginUrls 비동기 함수 종료
    loadLoginUrls(); // 비동기 함수 호출(비동기 실행 시작)
  }, []); // } : useEffect의 콜백 함수 종료, []: 의존성 배열(빈 배열 = 한 번만 실행), ): useEffect 함수 호출 종료

  const handleEmailClick = () => { // 재할당 불가 함수 선언
    setShowEmailForm(true); // 상태 변경 함수를 호출 // useState로 만든 값을 변경할려면 setter 함수로만 할 수 있음
    // setter함수는 setShowEmailForm 함수를 말하는것
  }; // 이메일폼 오픈하는 함수

  const handleEmailFormClose = () => { // 재할당 불가 함수 선언
    setShowEmailForm(false); // 상태 변경 함수를 호출 // useState로 만든 값을 변경할려면 setter 함수로만 할 수 있음
  }; // 이메일폼 닫는 함수

  const handleAuthSuccess = () => { // 재할당 불가 함수
    // 로그인/회원가입 성공 시 처리
    setShowEmailForm(false); // 이메일폼 닫기
    // 홈페이지로 리다이렉트
    window.location.href = '/'; // 메인페이지로 리다이렉트
  };
  // LoginPage는 함수임 이 함수가 return으로 JSX를 반환하면 React가 이걸 화면에 렌더링함
  // LoginPage 함수 실행 -> 위쪽 로직 실행(useState 초기값 설정 함수 정의(handleEmailClick 등))
  // useEffect는 렌더링 후 실행 (첫 렌더링에서는 아직 실행 안됨)
  // retunr으로 JSX를 반환, React가 JSX를 화면에 렌더링 -> 렌더링이 완료되면 useEffect 실행
  return (
    <main className={styles.loginContainer}> 
      {/* 페이지의 메인 컨텐츠 영역 */}
      {/* JSX에서는 모든 태그에 CSS 클래스를 줄려면 className을 사용해야함  
      className은 React에서 CSS 클래스를 적용하는 속성임 HTML에에 class와 동일
       styles.loginContainer는 CSS 모듈에서 가져온 클래스임 즉 login.module.css 파일에 정의된 
       loginContainer 스타일을 적용하는것*/}
      <PosterWall /> {/* import PosterWall from "@/components/auth/PosterWall"; 컴포넌트 사용*/}

      <Modal open={open} onClose={() => setOpen(false)} closeOnBackdropClick={false}>
        {/*open={open}의 의미: 왼쪽 open은 Modal 컴포넌트가 받는 prop 이름이고 오른쪽 open은 위에서 만든 open 변수 (값은 ture
        즉, 위의 open 변수 값이 Modal의 open prop으로 전달된다는 뜻
        onClose의 의미: Modal 컴포넌트가 받는 prop 이름이고 setOpen(false)가 전달되면 오른쪽 open에 false가 전달되고 이게
        Modal prop open에 전달되서 모달이 닫힘 참고로 on은 "~할 때", "~이벤트가 발생했을 때"라는 뜻임임 
        closeOnBackdropCClick={false}는 배경 클릭해서 닫을때 란 의미고 이거에 false를 줘서 못닫게 막아둠
        참고로 esc로는 모달이닫힘*/}
        
        <div className={styles.loginModal}> {/*loginModal css 클래스 적용 */}
          <div className={styles.loginLogo}>LAPUTA</div> {/*loginLogo css 클래스 적용 */}
          <div className={styles.loginDescription}> {/*loginDescription css 클래스 적용 */}
            동시방영 신작부터 역대 인기작까지
            <br />한 곳에서 편-안하게!
          </div> {/*loginDescription css 클래스 적용 */}

          <SocialButton {/* 소셜 버튼 */}
            provider="email"  {/* 소셜 로그인 종류가 email이라는 뜻*/}
            label="이메일로 시작"  {/* 버튼에 표시될 텍스트 */}
            onClick={handleEmailClick} {/* 버튼 클릭 시 handleEmailClick 함수 호출하고 이메일폼이 오픈됨*/}
          />

          <div className={styles.loginDivider}>또는</div> {/*loginDivider css 클래스 적용 */}

          <div className={styles.socialButtonsContainer}> {/*socialButtonsContainer css 클래스 적용 소셜 버튼들을 감싸는 컨테이너임*/}
            {/* 각 버튼 구조 a hre=oauthUrls.kakao}> oauthUrls 는 객체임 oauthUrls 객체의 kakao 속성값을 사용한다는뜻
            aria-lable: 접근성(accessibility 속성 화면에는 보이지 않지만, 스크린 리더는 "kakao"라고 읽어줌
            className={`${styles.socialButton} ${styles.socialButtonKakao}`} 이 부분은 css 클래스 2개를 적용하려면
            백틱으로 문자열로 묶어줘야함 그리고 {} 형태로 쓰면 변수가 아니라 그냥 텍스트로 인식되기때문에
            ${변수명} 형태로 써줘서 변수 값이 문자열에 삽입되게해줌
            */}
            <a href={oauthUrls.kakao} aria-label="kakao" className={`${styles.socialButton} ${styles.socialButtonKakao}`}>
              <Image alt="kakao" src="/icons/kakao.svg" width={24} height={24} className={styles.socialButtonIcon} />
            </a>
            <a href={oauthUrls.google} aria-label="google" className={`${styles.socialButton} ${styles.socialButtonGoogle}`}>
              <Image alt="google" src="/icons/google.svg" width={24} height={24} className={styles.socialButtonIcon} />
            </a>
            <a href={oauthUrls.naver} aria-label="naver" className={`${styles.socialButton} ${styles.socialButtonNaver}`}>
              <Image alt="naver" src="/icons/naver.svg" width={24} height={24} className={styles.socialButtonIcon} />
            </a>
          </div>

          <a href="#" className={styles.helpLink}>로그인의 어려움을 겪고 계신가요?</a>
        </div> {/* loginModal css 클래스 적용 종료 */}
      </Modal>

      {/* 이메일 로그인/회원가입 폼 */}
      {showEmailForm && ( // 조건부 렌더링할때 사용 shwEmailForm이 true일떄 렌더링
        <Modal open={showEmailForm} onClose={handleEmailFormClose}> 
        {/* open={showEmailForm}의 의미: Modal 컴포넌트가 받는 prop 이름이고 showEmailForm 변수 값이 Modal prop open에 전달된다는 뜻
        onClose={handleEmailFormClose}의 의미: Modal 컴포넌트가 받는 prop 이름이고 handleEmailFormClose 함수가 전달되고 이게
        Modal prop onClose에 전달되서 모달이 닫힘 참고로 on은 "~할 때", "~이벤트가 발생했을 때"라는 뜻임
        그리고 onClose에 컴포넌트 닫는 함수를 넘겨야 esc키로 닫을수가 있음음
        */}
          <EmailAuthForm 
            // EmailAuthForm 컴포넌트에 전달할 props 객체
            onClose={handleEmailFormClose} // 컴포넌트 닫는 함수를 전달 // 이게 이메일 로그인/회원가입 폼에 X 버튼에서 사용됨됨
            onSuccess={handleAuthSuccess} // 로그인/회원가입 성공 시 실행되는 함수를 전달 // 이메일 폼닫고 메인페이지로 리다이렉트
            // 부모 컴포넌트가 props로 함수를 전달받았어도 공유가 안되서 자식 컴포넌트가 사용할려면 또 전달받아야함
          />
        </Modal>
      )}
    </main>
  );
}


