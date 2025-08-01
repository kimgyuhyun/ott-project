'use client';

import { useState } from 'react';
import apiClient, { API_ENDPOINTS } from '@/lib/api';

const WithdrawButton = () => {
  const [loading, setLoading] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);

  const handleWithdraw = async () => {
    if (!confirm('정말로 회원탈퇴를 하시겠습니까?\n이 작업은 되돌릴 수 없습니다.')) {
      return;
    }

    setLoading(true);

    try {
      const response = await apiClient.delete(API_ENDPOINTS.WITHDRAW);

      if (response.data.success) {
        alert('회원탈퇴가 완료되었습니다.');
        // 홈페이지로 리다이렉트
        window.location.href = '/';
      }
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      alert(error.response?.data?.message || '회원탈퇴에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="withdraw-section">
      <button
        type="button"
        onClick={() => setShowConfirm(true)}
        className="withdraw-btn"
        disabled={loading}
      >
        {loading ? '처리 중...' : '회원탈퇴'}
      </button>

      {showConfirm && (
        <div className="confirm-modal">
          <div className="confirm-content">
            <h3>회원탈퇴 확인</h3>
            <p>정말로 회원탈퇴를 하시겠습니까?</p>
            <p>이 작업은 되돌릴 수 없습니다.</p>
            <div className="confirm-buttons">
              <button
                onClick={handleWithdraw}
                className="confirm-yes"
                disabled={loading}
              >
                {loading ? '처리 중...' : '탈퇴하기'}
              </button>
              <button
                onClick={() => setShowConfirm(false)}
                className="confirm-no"
                disabled={loading}
              >
                취소
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default WithdrawButton; 