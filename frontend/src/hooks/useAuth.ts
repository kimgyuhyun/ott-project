import { useState, useEffect } from 'react';

interface AuthUser {
  id?: number | string;
  username?: string;
  email?: string;
  profileImage?: string;
}

interface AuthStatusResponse {
  authenticated?: boolean;
  isLoggedIn?: boolean;
  id?: number | string;
  username?: string;
  email?: string;
  profileImage?: string;
  principal?: {
    id?: number | string;
    username?: string;
    email?: string;
    profileImage?: string;
  };
}

export const useAuth = () => {
  const [isLoggedIn, setIsLoggedIn] = useState<boolean | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [user, setUser] = useState<AuthUser | null>(null);

  useEffect(() => {
    checkAuthStatus();
  }, []);

  const checkAuthStatus = async (): Promise<void> => {
    try {
      console.log('🔍 useAuth - 로그인 상태 확인 시작');
      
      const response = await fetch('/api/oauth2/status', {
        credentials: 'include'
      });
      
      console.log('🔍 useAuth - API 응답 상태:', response.status, response.statusText);
      console.log('🔍 useAuth - API 응답 헤더:', Object.fromEntries(response.headers.entries()));
      
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      const data: AuthStatusResponse = await response.json();
      console.log('🔍 useAuth - API 응답 데이터:', data);
      
      // 백엔드 응답 구조에 맞게 authenticated 필드 사용
      const loginStatus = data.authenticated || data.isLoggedIn || false;
      console.log('🔍 useAuth - 로그인 상태 설정:', loginStatus, 'authenticated:', data.authenticated);
      
      // 사용자 정보 설정
      if (loginStatus) {
        const src = data.principal ?? data;
        setUser({
          id: src.id,
          username: src.username,
          email: src.email,
          profileImage: src.profileImage,
        });
      } else {
        setUser(null);
      }
      
      // 로그인 상태를 로컬/세션 스토리지에 저장
      if (loginStatus) {
        if (typeof window !== 'undefined') {
          localStorage.setItem('isLoggedIn', 'true');
          sessionStorage.setItem('isLoggedIn', 'true');
        }
        console.log('🔍 useAuth - 로그인 상태를 로컬/세션에 저장');
      } else {
        if (typeof window !== 'undefined') {
          localStorage.removeItem('isLoggedIn');
          sessionStorage.removeItem('isLoggedIn');
        }
        console.log('🔍 useAuth - 로그인 상태를 로컬/세션에서 제거');
      }
      
      setIsLoggedIn(loginStatus);
    } catch (err: unknown) {
      console.error('🔍 useAuth - 로그인 상태 확인 실패:', err);
      if (err instanceof Error) {
        console.error('🔍 useAuth - 에러 상세 정보:', {
          name: err.name,
          message: err.message,
          stack: err.stack,
        });
      }
      setIsLoggedIn(false);
      setUser(null);
    } finally {
      setIsLoading(false);
      console.log('🔍 useAuth - 로딩 완료, 최종 상태:', { isLoggedIn, isLoading: false });
    }
  };

  return { isLoggedIn, isLoading, user };
};
