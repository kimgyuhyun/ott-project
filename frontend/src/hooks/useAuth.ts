import { useState, useEffect } from 'react';

export const useAuth = () => {
  const [isLoggedIn, setIsLoggedIn] = useState<boolean | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [user, setUser] = useState<any>(null);

  useEffect(() => {
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async () => {
    try {
      console.log('🔍 useAuth - 로그인 상태 확인 시작');
      
      const response = await fetch('/api/oauth2/status', {
        credentials: 'include'
      });
      
      console.log('🔍 useAuth - API 응답 상태:', response.status, response.statusText);
      
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      const data = await response.json();
      console.log('🔍 useAuth - API 응답 데이터:', data);
      
      // 백엔드 응답 구조에 맞게 authenticated 필드 사용
      const loginStatus = data.authenticated || data.isLoggedIn || false;
      console.log('🔍 useAuth - 로그인 상태 설정:', loginStatus, 'authenticated:', data.authenticated);
      
      // 사용자 정보 설정
      if (loginStatus && data.principal) {
        setUser({
          id: data.id,
          username: data.username,
          email: data.email,
          profileImage: data.profileImage
        });
      }
      
      // 로그인 상태를 로컬/세션 스토리지에 저장
      if (loginStatus) {
        localStorage.setItem('isLoggedIn', 'true');
        sessionStorage.setItem('isLoggedIn', 'true');
        console.log('🔍 useAuth - 로그인 상태를 로컬/세션에 저장');
      } else {
        localStorage.removeItem('isLoggedIn');
        sessionStorage.removeItem('isLoggedIn');
        console.log('🔍 useAuth - 로그인 상태를 로컬/세션에서 제거');
      }
      
      setIsLoggedIn(loginStatus);
    } catch (error) {
      console.error('🔍 useAuth - 로그인 상태 확인 실패:', error);
      console.error('🔍 useAuth - 에러 상세 정보:', {
        name: error.name,
        message: error.message,
        stack: error.stack
      });
      setIsLoggedIn(false);
      setUser(null);
    } finally {
      setIsLoading(false);
      console.log('🔍 useAuth - 로딩 완료, 최종 상태:', { isLoggedIn, isLoading: false });
    }
  };

  return { isLoggedIn, isLoading, user };
};
