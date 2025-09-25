"use client";
import { useState, useEffect } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/lib/AuthContext";
import { useMembershipData } from "@/hooks/useMembershipData";
import { getUserStats } from "@/lib/api/user";
import { getUnreadNotificationCount } from "@/lib/api/notification";
import SearchBar from "@/components/search/SearchBar";
import NotificationDropdown from "./NotificationDropdown";
import styles from "./Header.module.css";

export default function Header() {
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [isProfileOpen, setIsProfileOpen] = useState(false);
  const [isNotificationOpen, setIsNotificationOpen] = useState(false);
  const [userStats, setUserStats] = useState<any>(null);
  const [unreadNotificationCount, setUnreadNotificationCount] = useState(0);
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

  // 사용자 통계 로드
  useEffect(() => {
    if (isAuthenticated) {
      getUserStats()
        .then(stats => setUserStats(stats))
        .catch(err => console.error('통계 로드 실패:', err));
    }
  }, [isAuthenticated]);

  // 읽지 않은 알림 개수 로드
  useEffect(() => {
    const loadUnreadCount = async () => {
      if (isAuthenticated) {
        try {
          const count = await getUnreadNotificationCount();
          setUnreadNotificationCount(count as number);
        } catch (error) {
          console.error('읽지 않은 알림 개수 로드 실패:', error);
        }
      }
    };

    loadUnreadCount();
    
    // 주기적으로 알림 개수 확인 (5분마다)
    const interval = setInterval(loadUnreadCount, 5 * 60 * 1000);
    return () => clearInterval(interval);
  }, [isAuthenticated]);

  // 검색 실행 처리
  const handleSearch = (query: string) => {
    window.location.href = `/tags?search=${encodeURIComponent(query)}`;
  };

  // 알림 드롭다운 토글
  const handleNotificationToggle = () => {
    setIsNotificationOpen(!isNotificationOpen);
    setIsProfileOpen(false);
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
            {isAuthenticated && (
              <div className={styles.notificationContainer}>
                <button 
                  className={styles.headerButton}
                  onClick={handleNotificationToggle}
                >
                  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-5 5v-5zM4.5 19a2.5 2.5 0 01-2.5-2.5V7a2.5 2.5 0 012.5-2.5h15a2.5 2.5 0 012.5 2.5v9.5a2.5 2.5 0 01-2.5 2.5h-15z" />
                  </svg>
                  {unreadNotificationCount > 0 && (
                    <span className={styles.notificationBadge}>
                      {unreadNotificationCount > 99 ? '99+' : unreadNotificationCount}
                    </span>
                  )}
                </button>
                <NotificationDropdown 
                  isOpen={isNotificationOpen}
                  onClose={() => setIsNotificationOpen(false)}
                />
              </div>
            )}

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
                      <img src="/icons/default-avatar.png" alt="default" />
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
                    <div className={styles.dropdownContent}>
                      {/* 사용자 프로필 섹션 */}
                      <div className={styles.userProfileSection}>
                        <div className={styles.userProfileInfo}>
                          <div className={styles.userProfileImage}>
                            {user?.profileImage ? (
                              <img 
                                src={user.profileImage} 
                                alt="프로필" 
                              />
                            ) : (
                              <img src="/icons/default-avatar.png" alt="default" />
                            )}
                          </div>
                          <div className={styles.userProfileDetails}>
                            <div className={styles.userNameRow}>
                              <span className={styles.userName}>
                                {user?.username || '사용자'}
                              </span>
                              <svg className={styles.arrowIcon} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                              </svg>
                            </div>
                            <div className={styles.userLevelRow}>
                              <span className={styles.babyIcon}>👶</span>
                              <span className={styles.userLevel}>Lv.0 베이비</span>
                            </div>
                          </div>
                        </div>
                        
                        {/* 사용자 통계 섹션 */}
                        <div className={styles.userStatsSection}>
                          <Link
                            href="/mypage?tab=activity&activityTab=ratings"
                            onClick={() => setIsProfileOpen(false)}
                            className={styles.statItem}
                          >
                            <span className={styles.statNumber}>{userStats?.ratingCount || 0}</span>
                            <span className={styles.statLabel}>별점</span>
                          </Link>
                          <Link
                            href="/mypage?tab=activity&activityTab=reviews"
                            onClick={() => setIsProfileOpen(false)}
                            className={styles.statItem}
                          >
                            <span className={styles.statNumber}>{userStats?.reviewCount || 0}</span>
                            <span className={styles.statLabel}>리뷰</span>
                          </Link>
                          <Link
                            href="/mypage?tab=activity&activityTab=comments"
                            onClick={() => setIsProfileOpen(false)}
                            className={styles.statItem}
                          >
                            <span className={styles.statNumber}>{userStats?.commentCount || 0}</span>
                            <span className={styles.statLabel}>댓글</span>
                          </Link>
                        </div>
                        
                        {/* 보관함 버튼 */}
                        <Link
                          href="/mypage"
                          onClick={() => setIsProfileOpen(false)}
                          className={styles.archiveButton}
                        >
                          <svg className={styles.archiveIcon} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 8a2 2 0 012-2h10a2 2 0 012 2v8a2 2 0 01-2 2H7a2 2 0 01-2-2V8z" />
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 12h8" />
                          </svg>
                          <span>보관함</span>
                        </Link>
                      </div>

                      {/* 메뉴 항목들 */}
                      <div className={styles.menuItems}>
                        {/* 라퓨타 멤버십 */}
                        <Link
                          href={getMembershipLink()}
                          onClick={() => setIsProfileOpen(false)}
                          className={styles.menuItem}
                        >
                          <svg className={styles.menuIcon} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
                          </svg>
                          <span>라퓨타 멤버십</span>
                        </Link>
                        
                        {/* 이용내역 */}
                        <Link
                          href="/history"
                          onClick={() => setIsProfileOpen(false)}
                          className={styles.menuItem}
                        >
                          <svg className={styles.menuIcon} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                          </svg>
                          <span>이용내역</span>
                        </Link>

                        {/* 구분선 */}
                        <div className={styles.menuDivider} />
                        
                        {/* 설정 */}
                        <Link
                          href="/settings"
                          onClick={() => setIsProfileOpen(false)}
                          className={styles.menuItem}
                        >
                          <svg className={styles.menuIcon} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                          </svg>
                          <span>설정</span>
                        </Link>
                        
                        {/* 고객센터 */}
                        <button className={styles.menuItem}>
                          <svg className={styles.menuIcon} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" />
                          </svg>
                          <span>고객센터</span>
                        </button>
                        
                        {/* 로그아웃 */}
                        <button
                          className={`${styles.menuItem} ${styles.logoutItem}`}
                          onClick={handleLogout}
                        >
                          <svg className={styles.menuIcon} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
                          </svg>
                          <span>로그아웃</span>
                        </button>
                      </div>
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
