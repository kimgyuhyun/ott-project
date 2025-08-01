'use client';

import { useState, useEffect } from 'react';
import apiClient, { API_ENDPOINTS } from '@/lib/api';
import WithdrawButton from '@/components/WithdrawButton';

interface User {
  id: number;
  email: string;
  nickname: string;
  role: string;
  isActive: boolean;
  emailVerified: boolean;
  createdAt: string;
}

const Dashboard = () => {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
      const response = await apiClient.get(API_ENDPOINTS.ME);
      if (response.data.success) {
        setUser(response.data.user);
      }
    } catch (err) {
      setError('로그인이 필요합니다.');
      // 로그인 페이지로 리다이렉트
      window.location.href = '/';
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = async () => {
    try {
      await apiClient.post(API_ENDPOINTS.LOGOUT);
      alert('로그아웃되었습니다.');
      window.location.href = '/';
    } catch (err) {
      alert('로그아웃에 실패했습니다.');
    }
  };

  if (loading) {
    return <div className="loading">로딩 중...</div>;
  }

  if (error) {
    return <div className="error">{error}</div>;
  }

  return (
    <div className="dashboard">
      <header className="dashboard-header">
        <h1>OTT Project 대시보드</h1>
        <div className="user-info">
          <span>안녕하세요, {user?.nickname}님!</span>
          <button onClick={handleLogout} className="logout-btn">
            로그아웃
          </button>
        </div>
      </header>

      <main className="dashboard-main">
        <div className="user-details">
          <h2>사용자 정보</h2>
          <div className="info-grid">
            <div className="info-item">
              <label>이메일:</label>
              <span>{user?.email}</span>
            </div>
            <div className="info-item">
              <label>닉네임:</label>
              <span>{user?.nickname}</span>
            </div>
            <div className="info-item">
              <label>역할:</label>
              <span>{user?.role}</span>
            </div>
            <div className="info-item">
              <label>이메일 인증:</label>
              <span>{user?.emailVerified ? '완료' : '미완료'}</span>
            </div>
            <div className="info-item">
              <label>가입일:</label>
              <span>{new Date(user?.createdAt || '').toLocaleDateString()}</span>
            </div>
          </div>
        </div>

        <div className="actions">
          <h2>계정 관리</h2>
          <WithdrawButton />
        </div>
      </main>
    </div>
  );
};

export default Dashboard; 