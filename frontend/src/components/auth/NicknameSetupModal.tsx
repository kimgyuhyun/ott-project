"use client";
import { useState } from "react";
import Modal from "@/components/ui/Modal";
import { api } from "@/lib/api/index";
import { useAuth } from "@/lib/AuthContext";
import { getErrorMessage } from "@/lib/errorMessage";

interface NicknameSetupModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

/**
 * 닉네임 설정 모달
 * 소셜 로그인 신규 가입자가 닉네임을 설정할 수 있는 모달
 */
export default function NicknameSetupModal({ isOpen, onClose, onSuccess }: NicknameSetupModalProps) {
  const [nickname, setNickname] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { user, login } = useAuth();

  // 닉네임 제출 처리
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!nickname.trim()) {
      setError('닉네임을 입력해주세요.');
      return;
    }

    if (nickname.trim().length < 2 || nickname.trim().length > 20) {
      setError('닉네임은 2자 이상 20자 이하로 입력해주세요.');
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      // 닉네임 업데이트 API 호출
      const response = await api.put<{ success: boolean; newNickname?: string; message?: string }>('/oauth2/nickname', {
        nickname: nickname.trim()
      });

      if (response && response.success) {
        console.log('닉네임 설정 완료:', response.newNickname);
        
        // AuthContext의 사용자 정보 업데이트
        if (user) {
          const updatedUser = {
            ...user,
            username: response.newNickname ?? user.username
          };
          login(updatedUser);
        }

        onSuccess();
      } else {
        setError(response?.message || '닉네임 설정에 실패했습니다.');
      }
    } catch (err: unknown) {
      console.error('닉네임 설정 오류:', err);

      const message = getErrorMessage(err) ?? '닉네임 설정 중 오류가 발생했습니다.';
      setError(message);
    } finally {
      setIsLoading(false);
    }
  };

  // 나중에 설정하기 (건너뛰기)
  const handleSkip = () => {
    onSuccess();
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="환영합니다!">
      <div >
        {/* 환영 메시지 */}
        <div >
          <div >🎉</div>
          <h2 >
            회원가입이 완료되었습니다!
          </h2>
          <p >
            다른 사용자들에게 보여질 닉네임을 설정해주세요.
          </p>
        </div>

        {/* 닉네임 입력 폼 */}
        <form onSubmit={handleSubmit} >
          <div>
            <label htmlFor="nickname" >
              닉네임
            </label>
            <input
              type="text"
              id="nickname"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              placeholder="닉네임을 입력하세요 (2-20자)"
              
              maxLength={20}
              disabled={isLoading}
            />
            <p >
              {nickname.length}/20자
            </p>
          </div>

          {/* 에러 메시지 */}
          {error && (
            <div >
              {error}
            </div>
          )}

          {/* 버튼들 */}
          <div >
            <button
              type="submit"
              disabled={isLoading || !nickname.trim()}
              
            >
              {isLoading ? (
                <span >
                  <div ></div>
                  설정 중...
                </span>
              ) : (
                '닉네임 설정'
              )}
            </button>
            
            <button
              type="button"
              onClick={handleSkip}
              disabled={isLoading}
              
            >
              나중에 설정
            </button>
          </div>
        </form>

        {/* 추가 안내 */}
        <div >
          <p >
            닉네임은 언제든지 설정 페이지에서 변경할 수 있습니다.
          </p>
        </div>
      </div>
    </Modal>
  );
}
