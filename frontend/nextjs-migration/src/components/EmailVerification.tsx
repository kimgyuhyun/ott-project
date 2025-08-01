'use client';

import { useState } from 'react';
import apiClient, { API_ENDPOINTS, getApiUrl } from '@/lib/api';

interface EmailVerificationProps {
  email: string;
  onVerificationComplete: () => void;
  onCancel: () => void;
}

const EmailVerification: React.FC<EmailVerificationProps> = ({
  email,
  onVerificationComplete,
  onCancel
}) => {
  const [verificationCode, setVerificationCode] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [isCodeSent, setIsCodeSent] = useState(false);

  const sendVerificationCode = async () => {
    setIsLoading(true);
    setMessage('');
    
    try {
      const response = await apiClient.post(`${API_ENDPOINTS.SEND_EMAIL_VERIFICATION}?email=${email}`);
      
      if (response.data.success) {
        setMessage('인증 코드가 이메일로 발송되었습니다.');
        setIsCodeSent(true);
      } else {
        setMessage(response.data.message || '인증 코드 발송에 실패했습니다.');
      }
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      setMessage(err.response?.data?.message || '인증 코드 발송에 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  const verifyCode = async () => {
    if (!verificationCode.trim()) {
      setMessage('인증 코드를 입력해주세요.');
      return;
    }

    setIsLoading(true);
    setMessage('');
    
    try {
      const response = await apiClient.post(API_ENDPOINTS.VERIFY_EMAIL, {
        email,
        verificationCode
      });
      
      if (response.data.success) {
        setMessage('이메일 인증이 완료되었습니다!');
        setTimeout(() => {
          onVerificationComplete();
        }, 1500);
      } else {
        setMessage(response.data.message || '인증 코드가 올바르지 않습니다.');
      }
    } catch (error: unknown) {
      const err = error as { response?: { data?: { message?: string } } };
      setMessage(err.response?.data?.message || '인증에 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="email-verification">
      <h2>이메일 인증</h2>
      <p className="email-display">인증할 이메일: {email}</p>
      
      {!isCodeSent ? (
        <div className="send-code-section">
          <p>이메일로 인증 코드를 발송하시겠습니까?</p>
          <div className="button-group">
            <button 
              onClick={sendVerificationCode} 
              disabled={isLoading}
              className="send-button"
            >
              {isLoading ? '발송 중...' : '인증 코드 발송'}
            </button>
            <button onClick={onCancel} className="cancel-button">
              취소
            </button>
          </div>
        </div>
      ) : (
        <div className="verify-code-section">
          <p>이메일로 받은 6자리 인증 코드를 입력해주세요.</p>
          <input
            type="text"
            value={verificationCode}
            onChange={(e) => setVerificationCode(e.target.value)}
            placeholder="인증 코드 입력"
            maxLength={6}
            className="verification-input"
          />
          <div className="button-group">
            <button 
              onClick={verifyCode} 
              disabled={isLoading || !verificationCode.trim()}
              className="verify-button"
            >
              {isLoading ? '인증 중...' : '인증 확인'}
            </button>
            <button 
              onClick={sendVerificationCode} 
              disabled={isLoading}
              className="resend-button"
            >
              재발송
            </button>
          </div>
        </div>
      )}
      
      {message && (
        <div className={`message ${message.includes('완료') || message.includes('발송되었습니다') ? 'success' : 'error'}`}>
          {message}
        </div>
      )}
    </div>
  );
};

export default EmailVerification; 