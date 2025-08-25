"use client";
import { createContext, useContext, useState, useEffect, ReactNode } from 'react';

interface User {
  id: string;
  username: string;
  email: string;
  profileImage?: string;
}

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  login: (user: User) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);

  // 컴포넌트 마운트 시 로컬 스토리지에서 사용자 정보 확인
  useEffect(() => {
    const savedUser = localStorage.getItem('user');
    if (savedUser) {
      try {
        setUser(JSON.parse(savedUser));
      } catch (error) {
        console.error('사용자 정보 파싱 오류:', error);
        localStorage.removeItem('user');
      }
    }
    // 서버 프로필로 사용자명 동기화(소셜 닉네임 오염 방지)
    (async () => {
      try {
        const res = await fetch('/api/users/me/profile', { credentials: 'include' });
        if (res.ok) {
          const prof = await res.json();
          const serverUser = {
            id: String(prof.id ?? ''),
            username: prof.username ?? prof.name ?? '',
            email: prof.email ?? '',
            profileImage: undefined as string | undefined,
          };
          if (serverUser.username) {
            setUser(serverUser);
            localStorage.setItem('user', JSON.stringify(serverUser));
          }
        }
      } catch (_) {
        // ignore
      }
    })();
  }, []);

  const login = (userData: User) => {
    setUser(userData);
    localStorage.setItem('user', JSON.stringify(userData));
  };

  const logout = async () => {
    try {
      // 소셜 로그아웃과 이메일 로그아웃 모두 호출(동일 세션 기반)
      await fetch('/api/oauth2/logout', { method: 'POST', credentials: 'include' });
      await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
    } catch (e) {
      // 네트워크 오류는 무시하고 클라이언트 상태는 정리
      console.warn('로그아웃 요청 중 오류:', e);
    } finally {
      setUser(null);
      localStorage.removeItem('user');
      window.location.href = '/';
    }
  };

  const value = {
    user,
    isAuthenticated: !!user,
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
