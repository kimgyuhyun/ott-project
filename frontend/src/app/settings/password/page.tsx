"use client";
import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import Header from "@/components/layout/Header";
import { changePassword, getUserProfile } from "@/lib/api/user";
import styles from "./password.module.css";

/**
 * 비밀번호 변경 페이지
 * 사용자가 현재 비밀번호와 새 비밀번호를 입력하여 비밀번호를 변경
 */
export default function PasswordChangePage() {
  const router = useRouter();
  const [user, setUser] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [formData, setFormData] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  });
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 다크모드 적용
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', 'dark');
  }, []);

  // 사용자 정보 로드
  useEffect(() => {
    const loadUserProfile = async () => {
      try {
        const userData = await getUserProfile();
        setUser(userData);
      } catch (err) {
        console.error('사용자 정보 로드 실패:', err);
        router.push('/login');
      } finally {
        setIsLoading(false);
      }
    };

    loadUserProfile();
  }, [router]);

  // 폼 입력 처리
  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
    // 입력 시 에러 메시지 초기화
    if (error) setError(null);
  };

  // 폼 제출 처리
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    // 유효성 검사
    if (!formData.currentPassword || !formData.newPassword || !formData.confirmPassword) {
      setError('모든 필드를 입력해주세요.');
      return;
    }

    if (formData.newPassword !== formData.confirmPassword) {
      setError('새 비밀번호가 일치하지 않습니다.');
      return;
    }

    if (formData.newPassword.length < 8) {
      setError('새 비밀번호는 8자 이상이어야 합니다.');
      return;
    }

    // 비밀번호 규칙 검사 (영문/숫자/특수문자 중 2가지 이상)
    const passwordRegex = /^(?=.*[a-zA-Z])(?=.*\d)|(?=.*[a-zA-Z])(?=.*[!@#$%^&*])|(?=.*\d)(?=.*[!@#$%^&*])/;
    if (!passwordRegex.test(formData.newPassword)) {
      setError('새 비밀번호는 영문/숫자/특수문자 중 2가지 이상을 포함해야 합니다.');
      return;
    }

    try {
      setIsSubmitting(true);
      setError(null);
      
      await changePassword({
        currentPassword: formData.currentPassword,
        newPassword: formData.newPassword
      });
      
      alert('비밀번호가 성공적으로 변경되었습니다.');
      router.push('/settings');
      
    } catch (err: any) {
      console.error('비밀번호 변경 실패:', err);
      setError(err.message || '비밀번호 변경에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  // 로딩 중
  if (isLoading) {
    return (
      <div className={styles.passwordPageContainer}>
        <Header />
        <main className={styles.passwordMain}>
          <div className={styles.passwordContent}>
            <div className={styles.loadingContainer}>
              <div className={styles.loadingText}>로딩 중...</div>
            </div>
          </div>
        </main>
      </div>
    );
  }

  // 소셜 로그인 사용자
  if (user?.authProvider && user.authProvider !== 'LOCAL') {
    return (
      <div className={styles.passwordPageContainer}>
        <Header />
        <main className={styles.passwordMain}>
          <div className={styles.passwordContent}>
            <h1 className={styles.passwordPageTitle}>LAPUTA</h1>
            
            <div className={styles.socialAccountContainer}>
              <div className={styles.socialAccountTitle}>비밀번호 변경</div>
              <form className={styles.socialAccountForm}>
                <div className={styles.socialInputGroup}>
                  <label className={styles.socialInputLabel}>현재 비밀번호</label>
                  <div className={styles.socialInputWrapper}>
                    <input 
                      placeholder="현재 사용중인 비밀번호를 입력해주세요." 
                      className={styles.socialInput} 
                      type="password" 
                      disabled
                    />
                    <div className={styles.socialInputBorder}></div>
                  </div>
                  <div className={styles.socialInputError}></div>
                </div>
                <div className={styles.socialInputGroup}>
                  <label className={styles.socialInputLabel}>새 비밀번호</label>
                  <div className={styles.socialInputWrapper}>
                    <input 
                      placeholder="8자 이상 영문/숫자/특수문자 중 2가지 포함" 
                      className={styles.socialInput} 
                      type="password" 
                      disabled
                    />
                    <div className={styles.socialInputBorder}></div>
                  </div>
                  <div className={styles.socialInputError}></div>
                </div>
                <div className={styles.socialInputGroup}>
                  <label className={styles.socialInputLabel}>새 비밀번호 확인</label>
                  <div className={styles.socialInputWrapper}>
                    <input 
                      placeholder="비밀번호를 다시 한 번 입력해주세요." 
                      className={styles.socialInput} 
                      type="password" 
                      disabled
                    />
                    <div className={styles.socialInputBorder}></div>
                  </div>
                  <div className={styles.socialInputError}></div>
                </div>
                <a className={styles.socialForgotPasswordLink} href="/auth/find-password">
                  비밀번호를 잊으셨나요?
                </a>
                <button disabled className={styles.socialSubmitButton}>
                  비밀번호 변경하기
                </button>
              </form>
            </div>
          </div>
        </main>
      </div>
    );
  }

  // 로컬 계정 사용자 - 비밀번호 변경 폼
  return (
    <div className={styles.passwordPageContainer}>
      <Header />
      
      <main className={styles.passwordMain}>
        <div className={styles.passwordContent}>
          {/* 페이지 제목 */}
          <h1 className={styles.passwordPageTitle}>LAPUTA</h1>
          
          {/* 비밀번호 변경 폼 */}
          <div className={styles.passwordFormContainer}>
            <h2 className={styles.passwordFormTitle}>비밀번호 변경</h2>
            
            <form onSubmit={handleSubmit} className={styles.passwordForm}>
              {/* 현재 비밀번호 */}
              <div className={styles.inputGroup}>
                <label htmlFor="currentPassword" className={styles.inputLabel}>
                  현재 비밀번호
                </label>
                <input
                  type="password"
                  id="currentPassword"
                  name="currentPassword"
                  placeholder="현재 사용중인 비밀번호를 입력해주세요."
                  value={formData.currentPassword}
                  onChange={handleInputChange}
                  className={styles.passwordInput}
                  disabled={isSubmitting}
                />
              </div>

              {/* 새 비밀번호 */}
              <div className={styles.inputGroup}>
                <label htmlFor="newPassword" className={styles.inputLabel}>
                  새 비밀번호
                </label>
                <input
                  type="password"
                  id="newPassword"
                  name="newPassword"
                  placeholder="8자 이상 영문/숫자/특수문자 중 2가지 포함"
                  value={formData.newPassword}
                  onChange={handleInputChange}
                  className={styles.passwordInput}
                  disabled={isSubmitting}
                />
              </div>

              {/* 새 비밀번호 확인 */}
              <div className={styles.inputGroup}>
                <label htmlFor="confirmPassword" className={styles.inputLabel}>
                  새 비밀번호 확인
                </label>
                <input
                  type="password"
                  id="confirmPassword"
                  name="confirmPassword"
                  placeholder="비밀번호를 다시 한 번 입력해주세요."
                  value={formData.confirmPassword}
                  onChange={handleInputChange}
                  className={styles.passwordInput}
                  disabled={isSubmitting}
                />
              </div>

              {/* 에러 메시지 */}
              {error && (
                <div className={styles.errorMessage}>
                  {error}
                </div>
              )}

              {/* 비밀번호 찾기 링크 */}
              <div className={styles.forgotPasswordLink}>
                <Link href="/auth/forgot-password">
                  비밀번호를 잊으셨나요?
                </Link>
              </div>

              {/* 제출 버튼 */}
              <button
                type="submit"
                className={styles.submitButton}
                disabled={isSubmitting || !formData.currentPassword || !formData.newPassword || !formData.confirmPassword}
              >
                {isSubmitting ? '변경 중...' : '비밀번호 변경하기'}
              </button>
            </form>
          </div>
        </div>
      </main>
    </div>
  );
}
