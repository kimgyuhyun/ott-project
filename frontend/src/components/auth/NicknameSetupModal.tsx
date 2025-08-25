"use client";
import { useState } from "react";
import Modal from "@/components/ui/Modal";
import { api } from "@/lib/api/index";
import { useAuth } from "@/lib/AuthContext";

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
      const response = await api.put('/oauth2/nickname', {
        nickname: nickname.trim()
      });

      if (response.success) {
        console.log('닉네임 설정 완료:', response.newNickname);
        
        // AuthContext의 사용자 정보 업데이트
        if (user) {
          const updatedUser = {
            ...user,
            username: response.newNickname
          };
          login(updatedUser);
        }

        onSuccess();
      } else {
        setError(response.message || '닉네임 설정에 실패했습니다.');
      }
    } catch (err: any) {
      console.error('닉네임 설정 오류:', err);
      
      if (err.response?.data?.message) {
        setError(err.response.data.message);
      } else {
        setError('닉네임 설정 중 오류가 발생했습니다.');
      }
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
      <div className="space-y-6">
        {/* 환영 메시지 */}
        <div className="text-center">
          <div className="text-6xl mb-4">🎉</div>
          <h2 className="text-2xl font-bold text-gray-800 mb-2">
            회원가입이 완료되었습니다!
          </h2>
          <p className="text-gray-600">
            다른 사용자들에게 보여질 닉네임을 설정해주세요.
          </p>
        </div>

        {/* 닉네임 입력 폼 */}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="nickname" className="block text-sm font-medium text-gray-700 mb-2">
              닉네임
            </label>
            <input
              type="text"
              id="nickname"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              placeholder="닉네임을 입력하세요 (2-20자)"
              className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500 focus:border-transparent text-gray-900 placeholder-gray-400"
              maxLength={20}
              disabled={isLoading}
            />
            <p className="text-xs text-gray-500 mt-1">
              {nickname.length}/20자
            </p>
          </div>

          {/* 에러 메시지 */}
          {error && (
            <div className="text-red-600 text-sm bg-red-50 p-3 rounded-lg">
              {error}
            </div>
          )}

          {/* 버튼들 */}
          <div className="flex space-x-3">
            <button
              type="submit"
              disabled={isLoading || !nickname.trim()}
              className="flex-1 bg-purple-600 hover:bg-purple-700 disabled:bg-gray-300 text-white py-2 px-4 rounded-lg font-medium transition-colors disabled:cursor-not-allowed"
            >
              {isLoading ? (
                <span className="flex items-center justify-center">
                  <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>
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
              className="flex-1 bg-gray-200 hover:bg-gray-300 text-gray-700 py-2 px-4 rounded-lg font-medium transition-colors disabled:cursor-not-allowed"
            >
              나중에 설정
            </button>
          </div>
        </form>

        {/* 추가 안내 */}
        <div className="text-center">
          <p className="text-xs text-gray-500">
            닉네임은 언제든지 설정 페이지에서 변경할 수 있습니다.
          </p>
        </div>
      </div>
    </Modal>
  );
}
