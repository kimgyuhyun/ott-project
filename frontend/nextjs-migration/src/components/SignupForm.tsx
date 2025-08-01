'use client';

import { useState } from 'react';
import apiClient, { API_ENDPOINTS } from '@/lib/api';
import EmailVerification from './EmailVerification';

const SignupForm = () => {
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    nickname: ''
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [showEmailVerification, setShowEmailVerification] = useState(false);
  const [isEmailVerified, setIsEmailVerified] = useState(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value
    });
  };

  const handleEmailVerification = () => {
    if (!formData.email) {
      setError('이메일을 먼저 입력해주세요.');
      return;
    }
    setShowEmailVerification(true);
  };

  const handleVerificationComplete = () => {
    setIsEmailVerified(true);
    setShowEmailVerification(false);
  };

  const handleVerificationCancel = () => {
    setShowEmailVerification(false);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!isEmailVerified) {
      setError('이메일 인증을 완료해주세요.');
      return;
    }
    
    setLoading(true);
    setError('');

    try {
      const response = await apiClient.post(API_ENDPOINTS.SIGNUP, formData);

      if (response.data.success) {
        alert('회원가입 성공!');
        // 대시보드로 이동
        window.location.href = '/dashboard';
      }
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      setError(error.response?.data?.message || '회원가입에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  if (showEmailVerification) {
    return (
      <EmailVerification
        email={formData.email}
        onVerificationComplete={handleVerificationComplete}
        onCancel={handleVerificationCancel}
      />
    );
  }

  return (
    <form onSubmit={handleSubmit} className="auth-form">
      {error && <div className="error-message">{error}</div>}
      
      <div className="form-group">
        <label htmlFor="email">이메일</label>
        <div className="email-input-group">
          <input
            type="email"
            id="email"
            name="email"
            value={formData.email}
            onChange={handleChange}
            required
            placeholder="이메일을 입력하세요"
            disabled={isEmailVerified}
          />
          {!isEmailVerified && (
            <button
              type="button"
              onClick={handleEmailVerification}
              disabled={!formData.email}
              className="verify-email-btn"
            >
              인증
            </button>
          )}
          {isEmailVerified && (
            <span className="verified-badge">✓ 인증완료</span>
          )}
        </div>
      </div>

      <div className="form-group">
        <label htmlFor="password">비밀번호</label>
        <input
          type="password"
          id="password"
          name="password"
          value={formData.password}
          onChange={handleChange}
          required
          placeholder="비밀번호를 입력하세요 (8자 이상)"
          minLength={8}
        />
      </div>

      <div className="form-group">
        <label htmlFor="nickname">닉네임</label>
        <input
          type="text"
          id="nickname"
          name="nickname"
          value={formData.nickname}
          onChange={handleChange}
          required
          placeholder="닉네임을 입력하세요 (2-20자)"
          minLength={2}
          maxLength={20}
        />
      </div>

      <button 
        type="submit" 
        className="submit-btn"
        disabled={loading || !isEmailVerified}
      >
        {loading ? '회원가입 중...' : '회원가입'}
      </button>
    </form>
  );
};

export default SignupForm; 