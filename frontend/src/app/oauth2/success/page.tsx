"use client";
import { useEffect, useRef, useState, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/AuthContext";
import NicknameSetupModal from "@/components/auth/NicknameSetupModal";

/**
 * OAuth2 소셜 로그인 성공 콜백 페이지
 * 신규 사용자 여부에 따라 닉네임 설정 모달을 표시
 */
function OAuth2SuccessContent() {
  const [isLoading, setIsLoading] = useState(true);
  const [showNicknameModal, setShowNicknameModal] = useState(false);
  const router = useRouter();
  const searchParams = useSearchParams();
  const { login } = useAuth();
  const ranRef = useRef(false);
  const isNewUserParam = (searchParams.get('newUser') === 'true');

  useEffect(() => {
    if (ranRef.current) return; // 중복 실행 방지
    ranRef.current = true;

    const handleOAuth2Success = async () => {
      try {
        // URL 파라미터에서 신규 사용자 여부 확인
        const isNewUser = isNewUserParam;
        
        console.log('OAuth2 로그인 성공 - 신규 사용자:', isNewUser);
        
        // 백엔드에서 현재 사용자 정보 가져오기
        const response = await fetch('/api/oauth2/user-info', {
          credentials: 'include'
        });
        
        if (response.ok) {
          const userData = await response.json();
          
          if (userData.authenticated && (userData.attributes || userData.principal)) {
            // OAuth2 사용자 정보를 AuthContext 형식으로 변환
            const user = {
              id: (userData.id || userData.attributes?.userId || userData.attributes?.id || userData.principal?.attributes?.userId || 'unknown'),
              // DB 응답(userInfo.username)을 최우선으로 사용하고, 없을 때만 attributes 사용
              username: (userData.username || userData.attributes?.userName || userData.attributes?.name || userData.principal?.attributes?.userName || userData.principal?.attributes?.name),
              email: (userData.email || userData.attributes?.userEmail || userData.attributes?.email || userData.username),
              profileImage: (userData.attributes?.picture || userData.principal?.attributes?.picture || undefined)
            };
            
            console.log('소셜 로그인 사용자 정보:', user);
            login(user);

            // 서버 프로필 우선으로 사용자명 동기화 (DB 닉네임 반영)
            try {
              const profRes = await fetch('/api/users/me/profile', { credentials: 'include' });
              if (profRes.ok) {
                const prof = await profRes.json();
                const serverName = prof.username || prof.name;
                if (serverName && serverName !== user.username) {
                  login({ ...user, username: serverName });
                }
              }
            } catch (_) {}

            // 신규 여부 판단: 쿼리파라미터 or 서버 속성 or 이름 미설정
            const attrIsNew = userData.attributes?.isNewUser ?? userData.isNewUser;
            const isNewByAttr = (attrIsNew === true || attrIsNew === 'true');
            const needsNickname = !user.username || String(user.username).trim().length < 2;
            if (isNewUserParam || isNewByAttr || needsNickname) {
              setShowNicknameModal(true);
            } else {
              window.location.replace('/');
            }
          }
        } else {
          console.error('사용자 정보 조회 실패:', response.status);
          window.location.replace('/');
        }
      } catch (error) {
        console.error('OAuth2 성공 처리 중 오류:', error);
        window.location.replace('/');
      } finally {
        setIsLoading(false);
      }
    };

    handleOAuth2Success();
  }, [isNewUserParam]);

  const handleNicknameSuccess = () => {
    setShowNicknameModal(false);
    window.location.replace('/');
  };

  const handleNicknameClose = () => {
    setShowNicknameModal(false);
    window.location.replace('/');
  };

  if (isLoading) {
    return (
      <div >
        <div >로그인 처리 중...</div>
      </div>
    );
  }

  return (
    <div >
      <div >
        <div >소셜 로그인이 완료되었습니다!</div>
        <div >잠시 후 홈페이지로 이동합니다.</div>
      </div>
      <NicknameSetupModal
        isOpen={showNicknameModal}
        onClose={handleNicknameClose}
        onSuccess={handleNicknameSuccess}
      />
    </div>
  );
}

export default function OAuth2SuccessPage() {
  return (
    <Suspense fallback={<div><div>로그인 처리 중...</div></div>}>
      <OAuth2SuccessContent />
    </Suspense>
  );
}
