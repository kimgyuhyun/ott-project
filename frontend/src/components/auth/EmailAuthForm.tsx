"use client";
import { useState } from "react";
import { useAuth } from "@/lib/AuthContext";
import { login, register, checkEmailDuplicate, sendVerificationCode, verifyCode } from "@/lib/api/auth";
import styles from "./EmailAuthForm.module.css";

type AuthMode = 'login' | 'register';
type RegisterStep = 'email' | 'verification' | 'password';

interface EmailAuthFormProps {
  onClose: () => void;
  onSuccess: () => void;
  isRegister?: boolean;
}

export default function EmailAuthForm({ onClose, onSuccess, isRegister = false }: EmailAuthFormProps) {
  const { login: setAuthUser } = useAuth();
  const [mode, setMode] = useState<AuthMode>(isRegister ? 'register' : 'login');
  const [registerStep, setRegisterStep] = useState<RegisterStep>('email');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [verificationCode, setVerificationCode] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [emailChecked, setEmailChecked] = useState(false);
  const [emailVerified, setEmailVerified] = useState(false);
  const [successMessage, setSuccessMessage] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);

    try {
      if (mode === 'login') {
        const user = await login(email, password);
        // 백엔드가 반환하는 사용자 정보를 컨텍스트에 저장
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
