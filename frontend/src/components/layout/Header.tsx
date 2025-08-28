"use client";
import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/lib/AuthContext";
import SearchBar from "@/components/search/SearchBar";

export default function Header() {
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [isProfileOpen, setIsProfileOpen] = useState(false);
  const pathname = usePathname();
  const { user, isAuthenticated, logout } = useAuth();

  // 현재 페이지가 해당 링크와 일치하는지 확인하는 함수
  const isActiveLink = (path: string) => {
    if (path === '/') {
      return pathname === '/';
    }
    return pathname.startsWith(path);
  };

  // 로그아웃 처리
  const handleLogout = async () => {
    await logout();
    setIsProfileOpen(false);
  };

  // 검색 실행 처리
  const handleSearch = (query: string) => {
    window.location.href = `/tags?search=${encodeURIComponent(query)}`;
  };

  return (
    <header className="fixed top-0 left-0 right-0 pointer-events-auto" style={{ 
      backgroundColor: 'var(--background-1, #121212)',
      borderBottom: '1px solid var(--border-1, #323232)',
      position: 'fixed',
      zIndex: 999
    }}>
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center h-16">
          {/* 로고와 네비게이션을 함께 배치 */}
          <div className="flex items-center space-x-8">
            {/* 로고 */}
            <Link href="/" className="flex items-center cursor-pointer" style={{ pointerEvents: 'auto' }}>
              <span className="text-2xl font-bold" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>LAFTEL</span>
            </Link>

            {/* 네비게이션 */}
            <nav className="hidden md:flex space-x-8">
              <Link 
                href="/tags"
                className={`transition-colors duration-200 cursor-pointer ${
                  isActiveLink('/tags') 
                    ? 'font-semibold' 
                    : 'hover:opacity-80'
                }`}
                style={{ 
                  color: isActiveLink('/tags') 
                    ? 'var(--foreground-slight, #816BFF)' 
                    : 'var(--foreground-2, #E2E2E2)',
                  pointerEvents: 'auto'
                }}
              >
                태그검색
              </Link>
              <Link 
                href="/weekly"
                className={`transition-colors duration-200 cursor-pointer ${
                  isActiveLink('/weekly') 
                    ? 'font-semibold' 
                    : 'hover:opacity-80'
                }`}
                style={{ 
                  color: isActiveLink('/weekly') 
                    ? 'var(--foreground-slight, #816BFF)' 
                    : 'var(--foreground-2, #E2E2E2)',
                  pointerEvents: 'auto'
                }}
              >
                요일별 신작
              </Link>
              <Link 
                href="/membership"
                className={`transition-colors duration-200 cursor-pointer ${
                  isActiveLink('/membership') 
                    ? 'font-semibold' 
                    : 'hover:opacity-80'
                }`}
                style={{ 
                  color: isActiveLink('/membership') 
                    ? 'var(--foreground-slight, #816BFF)' 
                    : 'var(--foreground-2, #E2E2E2)',
                  pointerEvents: 'auto'
                }}
              >
                멤버십
              </Link>
            </nav>
          </div>

          {/* 우측 버튼들 */}
          <div className="flex items-center space-x-4 ml-auto">
            {/* 검색 버튼 */}
            <button
              onClick={() => setIsSearchOpen(!isSearchOpen)}
              className="transition-colors duration-200"
              style={{ color: 'var(--foreground-2, #E2E2E2)' }}
            >
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </button>

            {/* 알림 버튼 */}
            <button className="transition-colors duration-200" style={{ color: 'var(--foreground-2, #E2E2E2)' }}>
              <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-5 5v-5zM4.5 19a2.5 2.5 0 01-2.5-2.5V7a2.5 2.5 0 012.5-2.5h15a2.5 2.5 0 012.5 2.5v9.5a2.5 2.5 0 01-2.5 2.5h-15z" />
              </svg>
            </button>

            {/* 로그인 상태에 따른 표시 */}
            {isAuthenticated ? (
              /* 로그인된 사용자: 프로필 드롭다운 */
              <div className="relative">
                <button
                  onClick={() => setIsProfileOpen(!isProfileOpen)}
                  className="flex items-center space-x-2 transition-colors duration-200 hover:opacity-80"
                  style={{ color: 'var(--foreground-2, #E2E2E2)' }}
                >
                  {/* 프로필 이미지 */}
                  <div className="w-8 h-8 rounded-full overflow-hidden bg-gray-600 flex items-center justify-center">
                    {user?.profileImage ? (
                      <img 
                        src={user.profileImage} 
                        alt="프로필" 
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" style={{ color: 'var(--foreground-3, #ABABAB)' }}>
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                      </svg>
                    )}
                  </div>
                  
                  {/* 사용자 닉네임 */}
                  <span className="hidden sm:block text-sm font-medium" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>
                    {user?.username || '사용자'}
                  </span>
                  
                  {/* 드롭다운 화살표 */}
                  <svg className="w-4 h-4 transition-transform duration-200" fill="none" stroke="currentColor" viewBox="0 0 24 24" style={{ 
                    transform: isProfileOpen ? 'rotate(180deg)' : 'rotate(0deg)',
                    color: 'var(--foreground-3, #ABABAB)' 
                  }}>
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                  </svg>
                </button>

                {/* 프로필 드롭다운 메뉴 */}
                {isProfileOpen && (
                  <div className="absolute right-0 mt-2 w-48 rounded-lg shadow-lg" style={{ 
                    backgroundColor: 'var(--background-1, #121212)',
                    border: '1px solid var(--border-1, #323232)',
                    pointerEvents: 'auto',
                    zIndex: 1000
                  }}>
                    <div className="py-1">
                      {/* 사용자 정보 표시 */}
                      <div className="px-4 py-2 border-b" style={{ borderColor: 'var(--border-1, #323232)' }}>
                        <p className="text-sm font-medium" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>
                          {user?.username || '사용자'}
                        </p>
                        <p className="text-xs" style={{ color: 'var(--foreground-3, #ABABAB)' }}>
                          {user?.email || ''}
                        </p>
                      </div>
                      
                      {/* 보관함 */}
                      <Link
                        href="/mypage"
                        onClick={() => setIsProfileOpen(false)}
                        className="block px-4 py-2 text-sm transition-colors duration-200 hover:opacity-80 cursor-pointer"
                        style={{ color: 'var(--foreground-1, #F7F7F7)', pointerEvents: 'auto' }}
                      >
                        보관함
                      </Link>
                      
                      {/* 설정 */}
                      <Link
                        href="/settings"
                        onClick={() => setIsProfileOpen(false)}
                        className="block px-4 py-2 text-sm transition-colors duration-200 hover:opacity-80 cursor-pointer"
                        style={{ color: 'var(--foreground-1, #F7F7F7)', pointerEvents: 'auto' }}
                      >
                        설정
                      </Link>
                      
                      {/* 구분선 */}
                      <div className="border-t my-1" style={{ borderColor: 'var(--border-1, #323232)' }}></div>
                      
                      {/* 로그아웃 */}
                      <button
                        onClick={handleLogout}
                        className="block w-full text-left px-4 py-2 text-sm transition-colors duration-200 hover:opacity-80 cursor-pointer"
                        style={{ color: 'var(--foreground-3, #ABABAB)', pointerEvents: 'auto' }}
                      >
                        로그아웃
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              /* 로그인되지 않은 사용자: 로그인 버튼 */
              <Link 
                href="/login" 
                className="px-4 py-2 rounded-lg transition-colors duration-200 font-medium cursor-pointer"
                style={{ 
                  backgroundColor: 'var(--foreground-slight, #816BFF)',
                  color: 'var(--foreground-1, #F7F7F7)',
                  pointerEvents: 'auto'
                }}
              >
                로그인/가입
              </Link>
            )}
          </div>
        </div>

        {/* 검색바 */}
        {isSearchOpen && (
          <div className="pb-4">
            <div className="relative">
              <SearchBar
                onSearch={handleSearch}
                placeholder="작품 제목을 검색해 보세요"
                className="[&>form>input]:bg-white/10 [&>form>input]:border-white/20 [&>form>input]:text-white [&>form>input]:placeholder-white/50 [&>form>input]:focus:border-white/50 [&>form>input]:focus:ring-white/50"
              />
            </div>
          </div>
        )}
      </div>

      {/* 프로필 드롭다운 외부 클릭 시 닫기 */}
      {isProfileOpen && (
        <div 
          className="fixed inset-0" 
          style={{ zIndex: 998 }}
          onClick={() => setIsProfileOpen(false)}
        />
      )}
    </header>
  );
}
