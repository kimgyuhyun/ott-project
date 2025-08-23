"use client";
import { useState } from "react";
import Link from "next/link";
import { useAuth } from "@/lib/AuthContext";

/**
 * 라프텔 스타일 헤더 네비게이션
 * 상단 네비게이션 메뉴와 로그인/프로필 드롭다운 포함
 */
export default function Header() {
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [isProfileOpen, setIsProfileOpen] = useState(false);
  const { user, isAuthenticated, logout } = useAuth();

  return (
    <header className="fixed top-0 left-0 right-0 z-50 bg-black/90 backdrop-blur-sm">
      <div className="max-w-7xl mx-auto px-4 py-3 flex items-center">
        {/* 로고 */}
        <Link href="/" className="text-white text-xl font-bold tracking-wider">
          LAFTEL
        </Link>

        {/* 네비게이션 메뉴 */}
        <nav className="hidden md:flex items-center space-x-8 ml-8">
          <Link href="/tags" className="text-white/80 hover:text-white transition-colors">
            태그별
          </Link>
          <Link href="/weekly" className="text-white/80 hover:text-white transition-colors">
            요일별 신작
          </Link>
          <Link href="/membership" className="text-white/80 hover:text-white transition-colors">
            멤버십
          </Link>
        </nav>

        {/* 빈 공간 채우기 */}
        <div className="flex-1"></div>

        {/* 우측 버튼들 */}
        <div className="flex items-center space-x-3">
          {/* 검색 버튼 */}
          <button
            onClick={() => setIsSearchOpen(!isSearchOpen)}
            className="p-2 text-white/80 hover:text-white transition-colors"
            aria-label="검색"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </button>

          {/* 알림 버튼 */}
          <button className="p-2 text-white/80 hover:text-white transition-colors relative" aria-label="알림">
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-5 5v-5zM10.5 3.75a6 6 0 0 1 6 6v7.5a1.5 1.5 0 0 1-3 0v-7.5a3 3 0 0 0-3-3H9a3 3 0 0 0-3 3v7.5a1.5 1.5 0 0 1-3 0v-7.5a6 6 0 0 1 6-6Z" />
            </svg>
            {/* 알림 개수 표시 */}
            <span className="absolute -top-1 -right-1 bg-red-500 text-white text-xs rounded-full w-5 h-5 flex items-center justify-center">
              1
            </span>
          </button>

          {/* 인증 상태에 따른 버튼 */}
          {isAuthenticated ? (
            /* 프로필 드롭다운 */
            <div className="relative">
              <button
                onClick={() => setIsProfileOpen(!isProfileOpen)}
                className="flex items-center space-x-2 text-white/80 hover:text-white transition-colors"
                aria-label="프로필 메뉴"
              >
                {/* 프로필 이미지 */}
                <div className="w-8 h-8 rounded-full bg-purple-400 flex items-center justify-center">
                  {user?.profileImage ? (
                    <img 
                      src={user.profileImage} 
                      alt="프로필" 
                      className="w-8 h-8 rounded-full object-cover"
                    />
                  ) : (
                    <svg className="w-5 h-5 text-white" fill="currentColor" viewBox="0 0 24 24">
                      <path d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z" />
                    </svg>
                  )}
                </div>
                {/* 사용자 이름 */}
                <span className="text-sm font-medium">{user?.username}</span>
                {/* 화살표 아이콘 */}
                <svg 
                  className={`w-4 h-4 transition-transform ${isProfileOpen ? 'rotate-180' : ''}`} 
                  fill="none" 
                  stroke="currentColor" 
                  viewBox="0 0 24 24"
                >
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                </svg>
              </button>

              {/* 프로필 드롭다운 메뉴 */}
              {isProfileOpen && (
                <div className="absolute right-0 mt-2 w-48 bg-black/95 border border-white/20 rounded-lg shadow-lg py-2 z-50">
                  <Link 
                    href="/mypage" 
                    className="block px-4 py-2 text-white/80 hover:text-white hover:bg-white/10 transition-colors"
                    onClick={() => setIsProfileOpen(false)}
                  >
                    마이페이지
                  </Link>
                  <Link 
                    href="/settings" 
                    className="block px-4 py-2 text-white/80 hover:text-white hover:bg-white/10 transition-colors"
                    onClick={() => setIsProfileOpen(false)}
                  >
                    설정
                  </Link>
                  <hr className="border-white/20 my-1" />
                  <button
                    onClick={() => {
                      logout();
                      setIsProfileOpen(false);
                    }}
                    className="block w-full text-left px-4 py-2 text-white/80 hover:text-white hover:bg-white/10 transition-colors"
                  >
                    로그아웃
                  </button>
                </div>
              )}
            </div>
          ) : (
            /* 로그인/가입 버튼 */
            <Link 
              href="/login" 
              className="text-sm text-white/80 hover:text-white transition-colors"
            >
              로그인/가입
            </Link>
          )}
        </div>
      </div>

      {/* 검색창 (열렸을 때) */}
      {isSearchOpen && (
        <div className="border-t border-white/10 bg-black/95 p-4">
          <div className="max-w-2xl mx-auto">
            <input
              type="text"
              placeholder="작품 제목을 검색해 보세요"
              className="w-full px-4 py-3 bg-white/10 border border-white/20 rounded-lg text-white placeholder-white/50 focus:outline-none focus:border-white/50"
              autoFocus
            />
          </div>
        </div>
      )}

      {/* 프로필 드롭다운 외부 클릭 시 닫기 */}
      {isProfileOpen && (
        <div 
          className="fixed inset-0 z-40" 
          onClick={() => setIsProfileOpen(false)}
        />
      )}
    </header>
  );
}
