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
// const는 재할당 불가 useState로 만든 값은 setOpen()으로 변경 가능함함
// 구조 분해 할당
// 동작 흐름 
// 1. usState(true): 컴포넌트 처음 렌더링 시 한번만 호출 -> [true, 함수] 반환
// 2. setOpen(false): 함수 호출 -> open 값을 false로 변경 -> 반환값 없음
// 3. React가 상태 변경을 감지하고 컴포넌트를 다시 렌더링 -> open이 false인 상태로 화면 업데이트
// 만약에 상태가 true로 바뀌면 React가 다시 상태 변경 감지해서 재 랜더링해서 open 상태로 화면 업데이트해줌줌
  const [open, setOpen] = useState(true); // open은 현재 상태 값이고 setOpen은 상태를 변경하는 함수임 
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
          setOauthUrls({ // 상태 변경 함수 호출출
            // java는 논리합으로 boolean 값을 반환하지만 javascript에 ||는 값 반환(기본값 설정에 사용)
            kakao: data.loginUrls.kakao || `/login/oauth2/authorization/kakao`,
            // Truthy: true로 평가되는 값 (예: "문자열", 1, {}, [])
            // Falsy: false로 평가되는 값 (예: false, 0, "", null, undefined)
            // 왼쪽 값이 있으면 왼쪽 없으면 오른쪽(기본값) 사용
            // 삼항연산자와 비슷하지만 더 간단한 문법인듯듯
            // 백엔드에서 받은 값이 있으면 그걸 쓰고, 없으면 기본값을 사용함함
            google: data.loginUrls.google || `/login/oauth2/authorization/google`,
            naver: data.loginUrls.naver || `/login/oauth2/authorization/naver`
          });
          return; // 함수 종료 retunr이 없으면 API 성공 후에도 아래 기본값 설정 코드가 실행됨
          // return이 있으면 API 성공 시 함수 종료, 기본값 설정 코드는 실행 안됨됨
        }
      } catch { // res.ok = false -> !로 true로 치환되서 조건성립 예외처리로 catch로 이동동
        // 실패 시 기본값으로 폴백
        // FallBack: 대체/백업이란 의미 코드에서 APi 실패 시 기본값으로 대체체
      }
      setOauthUrls({ // data가 null이거나 loginUrls 요소가 없으면 여기로 바로옴
        kakao: `/login/oauth2/authorization/kakao`,
        google: `/login/oauth2/authorization/google`,
        naver: `/login/oauth2/authorization/naver`
      }); // 페이지 초기에는 빈문자열이지만 페이지 렌더링 후에 useEffect 실행행하고 기본값 또는 API 값이 들어감
      // 기본값 폴백 덕분에 API 요청이 실패 시에도 기본값으로 세팅되어있음음
    }; // loadLoginUrls 비동기 함수 종료
    loadLoginUrls(); // 비동기 함수 호출(비동기 실행 시작)
  }, []); // } : useEffect의 콜백 함수 종료, []: 의존성 배열(빈 배열 = 한 번만 실행), ): useEffect 함수 호출 종료

  const handleEmailClick = () => {
    setShowEmailForm(true);
  };

  const handleEmailFormClose = () => {
    setShowEmailForm(false);
  };

  const handleAuthSuccess = () => {
    // 로그인/회원가입 성공 시 처리
    setShowEmailForm(false);
    // 홈페이지로 리다이렉트
    window.location.href = '/';
  };

  return (
    <main className={styles.loginContainer}>
      <PosterWall />

      <Modal open={open} onClose={() => setOpen(false)} closeOnBackdropClick={false}>
        <div className={styles.loginModal}>
          <div className={styles.loginLogo}>LAPUTA</div>
          <div className={styles.loginDescription}>
            동시방영 신작부터 역대 인기작까지
            <br />한 곳에서 편-안하게!
          </div>

          <SocialButton 
            provider="email" 
            label="이메일로 시작" 
            onClick={handleEmailClick}
          />

          <div className={styles.loginDivider}>또는</div>

          <div className={styles.socialButtonsContainer}>
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
        </div>
      </Modal>

      {/* 이메일 로그인/회원가입 폼 */}
      {showEmailForm && (
        <Modal open={showEmailForm} onClose={handleEmailFormClose}>
          <EmailAuthForm 
            onClose={handleEmailFormClose}
            onSuccess={handleAuthSuccess}
          />
        </Modal>
      )}
    </main>
  );
}


