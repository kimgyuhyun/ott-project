"use client";
import { useState } from "react";
import { login, register, checkEmailDuplicate, sendVerificationCode, verifyCode } from "@/lib/api/auth";

type AuthMode = 'login' | 'register';
type RegisterStep = 'email' | 'verification' | 'password';

interface EmailAuthFormProps {
  onClose: () => void;
  onSuccess: () => void;
  isRegister?: boolean;
}

export default function EmailAuthForm({ onClose, onSuccess, isRegister = false }: EmailAuthFormProps) {
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
        await login(email, password);
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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-lg p-8 max-w-md w-full mx-4">
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-2xl font-bold text-gray-800">
            {mode === 'login' ? '로그인' : '회원가입'}
          </h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            ✕
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-1">
              이메일
            </label>
            <div className="flex space-x-2">
              <input
                type="email"
                id="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="flex-1 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="이메일을 입력하세요"
                required
              />
              {mode === 'register' && (
                <button
                  type="button"
                  onClick={handleEmailCheck}
                  className="px-3 py-2 bg-gray-500 text-white text-sm rounded-md hover:bg-gray-600 transition-colors"
                >
                  중복확인
                </button>
              )}
            </div>
          </div>

          {mode === 'register' && registerStep === 'email' && emailChecked && (
            <div className="text-center">
              <button
                type="button"
                onClick={handleSendVerificationCode}
                className="w-full py-2 px-4 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors"
              >
                인증코드 발송
              </button>
            </div>
          )}

          {mode === 'register' && registerStep === 'verification' && (
            <div>
              <label htmlFor="verificationCode" className="block text-sm font-medium text-gray-700 mb-1">
                인증코드
              </label>
              <input
                type="text"
                id="verificationCode"
                value={verificationCode}
                onChange={(e) => setVerificationCode(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="이메일로 받은 인증코드를 입력하세요"
                required
              />
            </div>
          )}

          {mode === 'register' && registerStep === 'password' && (
            <div>
              <label htmlFor="name" className="block text-sm font-medium text-gray-700 mb-1">
                닉네임
              </label>
              <input
                type="text"
                id="name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="닉네임을 입력하세요"
                required
              />
            </div>
          )}

          {(mode === 'login' || (mode === 'register' && registerStep === 'password')) && (
            <div>
              <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1">
                비밀번호
              </label>
              <input
                type="password"
                id="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="비밀번호를 입력하세요"
                required
              />
            </div>
          )}

          {error && (
            <div className="text-red-500 text-sm">{error}</div>
          )}

          {successMessage && (
            <div className="text-green-600 text-sm">{successMessage}</div>
          )}

          <button
            type="submit"
            disabled={isLoading || (mode === 'register' && registerStep === 'email' && !emailChecked) || (mode === 'register' && registerStep === 'verification' && !verificationCode)}
            className="w-full py-2 px-4 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:bg-gray-400 transition-colors"
          >
            {isLoading ? '처리중...' : (
              mode === 'login' ? '로그인' : 
              registerStep === 'email' ? '다음' :
              registerStep === 'verification' ? '인증하기' : '회원가입'
            )}
          </button>
        </form>

        <div className="mt-4 text-center">
          {mode === 'login' ? (
            <button
              onClick={switchToRegister}
              className="text-blue-600 hover:text-blue-800 text-sm"
            >
              계정이 없으신가요? 회원가입
            </button>
          ) : (
            <button
              onClick={switchToLogin}
              className="text-blue-600 hover:text-blue-800 text-sm"
            >
              이미 계정이 있으신가요? 로그인
            </button>
          )}
        </div>

        {mode === 'register' && (
          <div className="mt-2 text-center">
            <button
              onClick={resetForm}
              className="text-gray-500 hover:text-gray-700 text-xs"
            >
              처음부터 다시 시작
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
