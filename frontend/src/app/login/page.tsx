"use client";
import { useState, useEffect } from "react";
import Image from "next/image";
import Modal from "@/components/ui/Modal";
import PosterWall from "@/components/auth/PosterWall";
import SocialButton from "@/components/auth/SocialButton";
import EmailAuthForm from "@/components/auth/EmailAuthForm";
import styles from "./login.module.css";

export default function LoginPage() {
  const [open, setOpen] = useState(true);
  const [showEmailForm, setShowEmailForm] = useState(false);
  const [oauthUrls, setOauthUrls] = useState({
    kakao: "",
    google: "",
    naver: ""
  });

  useEffect(() => {
    // 백엔드 컨트롤러에서 로그인 URL을 받아와 사용 (도커/프록시 환경 일관성 확보)
    const loadLoginUrls = async () => {
      try {
        const res = await fetch('/api/oauth2/login-urls', { credentials: 'include' });
        if (!res.ok) throw new Error('failed');
        const data = await res.json();
        if (data && data.loginUrls) {
          setOauthUrls({
            kakao: data.loginUrls.kakao || `/login/oauth2/authorization/kakao`,
            google: data.loginUrls.google || `/login/oauth2/authorization/google`,
            naver: data.loginUrls.naver || `/login/oauth2/authorization/naver`
          });
          return;
        }
      } catch {
        // 실패 시 기본값으로 폴백
      }
      setOauthUrls({
        kakao: `/login/oauth2/authorization/kakao`,
        google: `/login/oauth2/authorization/google`,
        naver: `/login/oauth2/authorization/naver`
      });
    };
    loadLoginUrls();
  }, []);

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

      <Modal open={open} onClose={() => setOpen(false)}>
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


