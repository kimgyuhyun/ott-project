"use client";
import { createContext, useContext, useState, useEffect, ReactNode } from 'react';

interface User {
  id: string;
  username: string;
  email: string;
  profileImage?: string;
  role?: 'USER' | 'ADMIN';
}

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean | undefined;
  isAdmin: boolean;
  isInitialized: boolean;
  login: (user: User) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isInitialized, setIsInitialized] = useState(false);

  // 클라이언트에 남은 로그인 흔적 제거(서버 세션이 만료/무효일 때)
  const clearClientAuth = () => {
    setUser(null);
    localStorage.removeItem('user');
  };

  // 컴포넌트 마운트 시 로컬 스토리지에서 사용자 정보 확인
  useEffect(() => {
    const initializeAuth = async () => {
      const savedUser = localStorage.getItem('user');
      if (savedUser) {
        try {
          setUser(JSON.parse(savedUser));
        } catch {
          localStorage.removeItem('user');
        }
      }

      // 서버 프로필로 로그인 상태를 검증하고 사용자명을 동기화(소셜 닉네임 오염 방지)
      try {
        const res = await fetch('/api/users/me/profile', { credentials: 'include' });
        if (res.ok) {
          const prof = await res.json();
          const serverUser = {
            id: String(prof.id ?? ''),
            username: prof.username ?? prof.name ?? '',
            email: prof.email ?? '',
            profileImage: undefined as string | undefined,
            role: (prof.role === 'ADMIN' ? 'ADMIN' : 'USER') as 'USER' | 'ADMIN',
          };
          if (serverUser.username) {
            setUser(serverUser);
            localStorage.setItem('user', JSON.stringify(serverUser));
          }
        } else if (res.status === 401) {
          // 서버 세션이 만료/무효 → localStorage 의 stale 한 로그인 상태를 제거해 화면과 서버를 일치시킨다
          clearClientAuth();
        }
        // 그 외 상태(네트워크 불안정 등)는 기존 상태를 유지해 일시적 오류로 로그아웃되지 않게 한다
      } catch {
        // 네트워크 오류: 오프라인 등 일시적 상황이므로 로그인 상태를 건드리지 않는다
      } finally {
        setIsInitialized(true);
      }
    };

    initializeAuth();
  }, []);

  // 전역 401 인터셉터(api 레이어)가 세션 만료를 알리면 클라이언트 상태를 정리한다
  useEffect(() => {
    const handleUnauthorized = () => clearClientAuth();
    window.addEventListener('auth:unauthorized', handleUnauthorized);
    return () => window.removeEventListener('auth:unauthorized', handleUnauthorized);
  }, []);

  const login = (userData: User) => {
    setUser(userData);
    localStorage.setItem('user', JSON.stringify(userData));
  };

  const logout = async () => {
    try {
      // 소셜 로그아웃과 이메일 로그아웃 모두 호출(동일 세션 기반)
      await Promise.allSettled([
        fetch('/api/oauth2/logout', { method: 'POST', credentials: 'include' }),
        fetch('/api/auth/logout', { method: 'POST', credentials: 'include' })
      ]);
    } catch {
      // 네트워크 오류는 무시하고 클라이언트 상태는 정리
    } finally {
      setUser(null);
      localStorage.removeItem('user');

      // 홈페이지로 리다이렉트
      if (typeof window !== 'undefined') {
        window.location.href = '/';
      }
    }
  };

  const value = {
    user,
    isAuthenticated: isInitialized ? !!user : undefined,
    isAdmin: user?.role === 'ADMIN',
    isInitialized,
    login,
    logout,
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
