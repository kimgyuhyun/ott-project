"use client";
import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import NicknameSetupModal from "@/components/auth/NicknameSetupModal";
import { useAuth } from "@/lib/AuthContext";
import { api } from "@/lib/api/index";

function AuthCallbackInner() {
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
        const me = await api.get<{
          authenticated: boolean;
          username?: string;
          attributes?: {
            userId?: string;
            userName?: string;
            userEmail?: string;
            picture?: string;
            isNewUser?: boolean;
          };
        }>('/oauth2/user-info');
        if (me?.authenticated && me?.attributes) {
          // 전역 AuthContext 업데이트
          login({
            id: me.attributes.userId || 'unknown',
            username: me.attributes.userName ?? me.username ?? 'unknown',
            email: me.attributes.userEmail ?? me.username ?? '',
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
  }, [searchParams, router, login]);

  return (
    <div >
      <div >
        {status === 'loading' && (
          <>
            <div ></div>
            <h2 >로그인 처리 중...</h2>
            <p >잠시만 기다려주세요.</p>
          </>
        )}

        {status === 'success' && (
          <>
            <div >✓</div>
            <h2 >로그인 성공!</h2>
            <p >{message}</p>
          </>
        )}

        {status === 'error' && (
          <>
            <div >✗</div>
            <h2 >로그인 실패</h2>
            <p >{message}</p>
            <button
              onClick={() => router.push('/login')}
              
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

export default function AuthCallbackPage() {
  return (
    <Suspense fallback={
      <div>
        <div></div>
        <h2>로그인 처리 중...</h2>
        <p>잠시만 기다려주세요.</p>
      </div>
    }>
      <AuthCallbackInner />
    </Suspense>
  );
}
