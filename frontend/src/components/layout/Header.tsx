"use client";
import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/lib/AuthContext";
import { useMembershipData } from "@/hooks/useMembershipData";
import SearchBar from "@/components/search/SearchBar";
import styles from "./Header.module.css";

export default function Header() {
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [isProfileOpen, setIsProfileOpen] = useState(false);
  const pathname = usePathname();
  const { user, isAuthenticated, logout } = useAuth();
  const { userMembership } = useMembershipData();

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

  // 멤버십 링크 결정 (멤버십 구독 중이거나 사용 가능 기간 내의 멤버는 모두 guide로)
  const getMembershipLink = () => {
    if (userMembership) {
      // 멤버십이 있고, 현재 시간이 endAt 이전이면 사용 가능 기간 내
      const now = new Date();
      const endDate = new Date(userMembership.endAt);
      
      if (now < endDate) {
        return '/membership/guide'; // 멤버십 구독 중이거나 사용 가능 기간 내
      }
    }
    return '/membership'; // 멤버십 없음 또는 만료된 멤버십
  };

  return (
    <header className={styles.headerContainer}>
      <div className={styles.headerWrapper}>
        <div className={styles.headerContent}>
          {/* 로고와 네비게이션을 함께 배치 */}
          <div className={styles.headerLogoNavGroup}>
            {/* 로고 */}
            <Link href="/" className={styles.headerLogo}>
              <span>LAPUTA</span>
            </Link>

            {/* 네비게이션 */}
            <nav className={styles.headerNav}>
              <Link
                href="/tags"
                className={`${styles.headerNavItem} ${isActiveLink('/tags') ? styles.active : ''}`}
              >
                태그검색
              </Link>
              <Link
                href="/weekly"
                className={`${styles.headerNavItem} ${isActiveLink('/weekly') ? styles.active : ''}`}
              >
                요일별 신작
              </Link>
              <Link
                href={getMembershipLink()}
                className={`${styles.headerNavItem} ${isActiveLink('/membership') ? styles.active : ''}`}
              >
                멤버십
              </Link>
            </nav>
          </div>

          {/* 우측 버튼들 */}
          <div className={styles.headerActions}>
            {/* 검색 버튼 */}
            <button
              className={`${styles.headerButton} ${styles.searchToggleMobile}`}
              onClick={() => setIsSearchOpen(!isSearchOpen)}
            >
              <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
            </button>

            {/* 검색 팝오버 */}
            {isSearchOpen && (
              <div className={styles.searchPopover}>
                <SearchBar
                  onSearch={handleSearch}
                  placeholder="제목(초성), 제작사, 감독으로 검색"
                  className={styles.headerSearchInline}
                  align="right"
                  autoShow={false}
                  showSuggestions={isSearchOpen}
                />
              </div>
            )}

            {/* 알림 버튼 */}
            <button className={styles.headerButton}>
              <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-5 5v-5zM4.5 19a2.5 2.5 0 01-2.5-2.5V7a2.5 2.5 0 012.5-2.5h15a2.5 2.5 0 012.5 2.5v9.5a2.5 2.5 0 01-2.5 2.5h-15z" />
              </svg>
            </button>

            {/* 로그인 상태에 따른 표시 */}
            {isAuthenticated ? (
              /* 로그인된 사용자: 프로필 드롭다운 */
              <div className={styles.headerProfile}>
                <button
                  className={styles.headerProfileButton}
                  onClick={() => setIsProfileOpen(!isProfileOpen)}
                >
                  {/* 프로필 이미지 */}
                  <div className={styles.headerProfileImage}>
                    {user?.profileImage ? (
                      <img 
                        src={user.profileImage} 
                        alt="프로필" 
                      />
                    ) : (
                      <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
                      </svg>
                    )}
                  </div>
                  
                  {/* 사용자 닉네임 */}
                  <span className={styles.headerProfileName}>
                    {user?.username || '사용자'}
                  </span>
                  
                  {/* 드롭다운 화살표 */}
                  <svg 
                    className={`${styles.headerProfileArrow} ${isProfileOpen ? styles.open : ''}`}
                    fill="none" 
                    stroke="currentColor" 
                    viewBox="0 0 24 24"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                  </svg>
                </button>

                {/* 프로필 드롭다운 메뉴 */}
                {isProfileOpen && (
                  <div className={styles.headerDropdown}>
                    <div style={{ padding: '0.25rem 0' }}>
                      {/* 사용자 정보 표시 */}
                      <div style={{ padding: '0.5rem 1rem', borderBottom: '1px solid var(--border-1)' }}>
                        <p style={{ fontSize: '0.875rem', fontWeight: 500, color: 'var(--foreground-1)' }}>
                          {user?.username || '사용자'}
                        </p>
                        <p style={{ fontSize: '0.75rem', color: 'var(--foreground-3)' }}>
                          {user?.email || ''}
                        </p>
                      </div>
                      
                      {/* 보관함 */}
                      <Link
                        href="/mypage"
                        onClick={() => setIsProfileOpen(false)}
                        style={{ textDecoration: 'none' }}
                      >
                        <button className={styles.headerDropdownItem}>
                          보관함
                        </button>
                      </Link>
                      
                      {/* 라프텔 멤버십 */}
                      <Link
                        href={getMembershipLink()}
                        onClick={() => setIsProfileOpen(false)}
                        style={{ textDecoration: 'none' }}
                      >
                        <button className={styles.headerDropdownItem}>
                          라프텔 멤버십
                        </button>
                      </Link>

                      {/* 설정 */}
                      <Link
                        href="/settings"
                        onClick={() => setIsProfileOpen(false)}
                        style={{ textDecoration: 'none' }}
                      >
                        <button className={styles.headerDropdownItem}>
                          설정
                        </button>
                      </Link>
                      
                      {/* 이용내역 (임시) */}
                      <Link
                        href="/history"
                        onClick={() => setIsProfileOpen(false)}
                        style={{ textDecoration: 'none' }}
                      >
                        <button className={styles.headerDropdownItem}>
                          이용내역
                        </button>
                      </Link>

                      {/* 구분선 */}
                      <div className={styles.headerDropdownDivider} />
                      
                      {/* 로그아웃 */}
                      <button
                        className={`${styles.headerDropdownItem} ${styles.logout}`}
                        onClick={handleLogout}
                      >
                        로그아웃
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              /* 로그인되지 않은 사용자: 로그인 버튼 */
              <Link href="/login" className={styles.headerLoginButton}>
                로그인/가입
              </Link>
            )}
          </div>
        </div>

      </div>

      {/* 프로필 드롭다운 외부 클릭 시 닫기 */}
      {(isProfileOpen || isSearchOpen) && (
        <div 
          className={styles.headerOverlay}
          onClick={() => {
            setIsProfileOpen(false);
            setIsSearchOpen(false);
          }}
        />
      )}
    </header>
  );
}
