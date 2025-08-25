"use client";
import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import NicknameSetupModal from "@/components/auth/NicknameSetupModal";
import { useAuth } from "@/lib/AuthContext";
import { api } from "@/lib/api/index";

export default function AuthCallbackPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
  const [message, setMessage] = useState('');
  const [showNicknameModal, setShowNicknameModal] = useState(false);
  const { login } = useAuth();

  useEffect(() => {
    const handleCallback = async () => {
      try {
        // URL 파라미터에서 에러 확인
        const error = searchParams.get('error');
        if (error) {
          setStatus('error');
          setMessage(`로그인 실패: ${error}`);
          setTimeout(() => router.push('/login'), 3000);
          return;
        }

        // 서버 세션에서 사용자 정보 조회
        const me = await api.get<any>('/oauth2/user-info');
        if (me?.authenticated && me?.attributes) {
          // 전역 AuthContext 업데이트
          login({
            id: me.attributes.userId || 'unknown',
            username: me.attributes.userName || me.username,
            email: me.attributes.userEmail || me.username,
            profileImage: me.attributes.picture || undefined
          });

          // 신규 사용자 플래그(new=1 쿼리 또는 attributes.isNewUser) 확인해 모달 표시
          const isNewParam = searchParams.get('new') === '1';
          const isNewAttr = me.attributes.isNewUser === true;
          if (isNewParam || isNewAttr) {
            setShowNicknameModal(true);
            setStatus('success');
            setMessage('닉네임을 설정해주세요.');
            return; // 콜백 페이지에 머물러 모달 표시
          }
        }

        // 신규 사용자가 아니면 홈으로 이동
        setStatus('success');
        setMessage('로그인 성공! 홈페이지로 이동합니다...');
        router.push('/');

      } catch (error) {
        setStatus('error');
        setMessage('로그인 처리 중 오류가 발생했습니다.');
        setTimeout(() => router.push('/login'), 3000);
      }
    };

    handleCallback();
  }, [searchParams, router]);

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center">
      <div className="bg-white rounded-lg shadow-lg p-8 max-w-md w-full mx-4 text-center">
        {status === 'loading' && (
          <>
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-purple-600 mx-auto mb-4"></div>
            <h2 className="text-xl font-semibold text-gray-800 mb-2">로그인 처리 중...</h2>
            <p className="text-gray-600">잠시만 기다려주세요.</p>
          </>
        )}

        {status === 'success' && (
          <>
            <div className="text-green-500 text-6xl mb-4">✓</div>
            <h2 className="text-xl font-semibold text-gray-800 mb-2">로그인 성공!</h2>
            <p className="text-gray-600">{message}</p>
          </>
        )}

        {status === 'error' && (
          <>
            <div className="text-red-500 text-6xl mb-4">✗</div>
            <h2 className="text-xl font-semibold text-gray-800 mb-2">로그인 실패</h2>
            <p className="text-gray-600 mb-4">{message}</p>
            <button
              onClick={() => router.push('/login')}
              className="bg-purple-600 hover:bg-purple-700 text-white px-6 py-2 rounded-lg transition-colors"
            >
              로그인 페이지로 돌아가기
            </button>
          </>
        )}
      </div>

      {/* 신규 소셜 사용자용 닉네임 설정 모달 */}
      <NicknameSetupModal
        isOpen={showNicknameModal}
        onClose={() => setShowNicknameModal(false)}
        onSuccess={() => {
          setShowNicknameModal(false);
          router.push('/');
        }}
      />
    </div>
  );
}
