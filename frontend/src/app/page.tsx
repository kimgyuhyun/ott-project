"use client";
import { useState, useEffect, useRef } from "react";
import Header from "@/components/layout/Header";
import WeeklySchedule from "@/components/home/WeeklySchedule";
import { getAnimeDetail } from "@/lib/api/anime";
import AnimeDetailModal from "@/components/anime/AnimeDetailModal";
import { useAuth } from "@/lib/AuthContext";
import { getAnimeList, getRecommendedAnime, getPopularAnime, listAnime } from "@/lib/api/anime";
import { api } from "@/lib/api/index";
import styles from "./page.module.css";


// OAuth2 현재 사용자 정보 응답 타입
type OAuthUserInfoResponse = {
  authenticated: boolean;
  username?: string;
  authorities?: any;
  principal?: any;
  oauth2User?: boolean;
  provider?: string;
  attributes?: Record<string, any>;
};

/**
 * 메인 홈페이지
 * 라프텔 스타일의 홈화면 레이아웃
 */
export default function Home() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedAnime, setSelectedAnime] = useState<any>(null);
  const [animeList, setAnimeList] = useState<any[]>([]);
  const [recommendedAnime, setRecommendedAnime] = useState<any[]>([]);
  const [popularAnime, setPopularAnime] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  
  const { user, isAuthenticated, login, logout } = useAuth();

  // 캐러셀 참조
  const recommendedRef = useRef<HTMLDivElement | null>(null);
  const popularRef = useRef<HTMLDivElement | null>(null);

  // 스크롤 가능 여부 상태
  const [recommendedScrollable, setRecommendedScrollable] = useState(false);
  const [popularScrollable, setPopularScrollable] = useState(false);

  // 캐러셀 스크롤 함수 (카드 한 칸 기준)
  const scrollByCard = (ref: React.RefObject<HTMLDivElement>, direction: number) => {
    const container = ref.current;
    if (!container) return;
    const firstItem = container.querySelector(`.${styles.carouselItem}`) as HTMLElement | null;
    const gapPx = 16; // CSS gap 1rem 가정
    const scrollAmount = firstItem ? (firstItem.getBoundingClientRect().width + gapPx) : Math.max(240, container.clientWidth * 0.8);
    container.scrollBy({ left: direction * scrollAmount, behavior: 'smooth' });
  };

  // 스크롤 가능 여부 계산
  useEffect(() => {
    const updateScrollability = () => {
      if (recommendedRef.current) {
        setRecommendedScrollable(recommendedRef.current.scrollWidth > recommendedRef.current.clientWidth + 4);
      }
      if (popularRef.current) {
        setPopularScrollable(popularRef.current.scrollWidth > popularRef.current.clientWidth + 4);
      }
    };

    updateScrollability();
    window.addEventListener('resize', updateScrollability);
    return () => window.removeEventListener('resize', updateScrollability);
  }, [recommendedAnime, popularAnime]);

  // 메인 페이지 테마 설정 (사용자 설정 연동)
  useEffect(() => {
    const setTheme = async () => {
      if (isAuthenticated && user) {
        // 로그인된 사용자: 설정에서 테마 가져오기
        try {
          const response = await fetch('/api/users/me/settings', {
            credentials: 'include'
          });
          if (response.ok) {
            // 응답 내용 확인
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.includes('application/json')) {
              const text = await response.text();
              if (text.trim()) {
                try {
                  const settings = JSON.parse(text);
                  // 사용자가 테마를 설정했는지 확인
                  if (settings.theme && (settings.theme === 'light' || settings.theme === 'dark')) {
                    // 사용자 설정 테마 사용
                    document.documentElement.setAttribute('data-theme', settings.theme);
                  } else {
                    // 테마 설정 안함: 메인 페이지 기본값 light
                    document.documentElement.setAttribute('data-theme', 'light');
                  }
                } catch (parseError) {
                  console.error('JSON 파싱 오류:', parseError);
                  // 파싱 실패 시 기본값 사용
                  document.documentElement.setAttribute('data-theme', 'light');
                }
              } else {
                // 빈 응답 시 기본값 사용
                document.documentElement.setAttribute('data-theme', 'light');
              }
            } else {
              // JSON이 아닌 응답 시 기본값 사용
              document.documentElement.setAttribute('data-theme', 'light');
            }
          } else {
            // 설정 로드 실패시 메인 페이지 기본값
            document.documentElement.setAttribute('data-theme', 'light');
          }
        } catch (error) {
          console.error('테마 설정 로드 실패:', error);
          document.documentElement.setAttribute('data-theme', 'light');
        }
      } else {
        // 비로그인: 메인 페이지 기본값 light
        document.documentElement.setAttribute('data-theme', 'light');
      }
    };

    setTheme();
  }, [isAuthenticated, user]);

  // 소셜 로그인 후 사용자 상태 확인 (간소화)
  useEffect(() => {
    const checkAuthStatus = async () => {
      // 이미 로그인되어 있으면 체크하지 않음
      if (isAuthenticated) return;
      
      try {
        // 백엔드에서 현재 로그인 상태 확인
        const response = await api.get<OAuthUserInfoResponse>('/oauth2/user-info');
        
        if (response.authenticated && (response.attributes || response.username)) {
          // OAuth2 사용자 정보를 AuthContext 형식으로 변환
          const userData = {
            id: (response as any).id || response.attributes?.userId || response.attributes?.id || 'unknown',
            // DB 닉네임(response.username)이 있으면 최우선 사용
            username: response.username || response.attributes?.userName || response.attributes?.name,
            email: (response as any).email || response.attributes?.userEmail || response.attributes?.email || response.username,
            profileImage: response.attributes?.picture || undefined
          };
          
          console.log('소셜 로그인 상태 확인 성공:', userData);
          login(userData);
        }
      } catch (error: any) {
        // 401 에러는 로그인되지 않은 상태이므로 정상
        if (error.response?.status !== 401) {
          console.error('로그인 상태 확인 실패:', error);
        }
      }
    };

    checkAuthStatus();
  }, [isAuthenticated, login]);

  // 애니메이션 데이터 로드
  useEffect(() => {
    const loadAnimeData = async () => {
      try {
        setIsLoading(true);
        setError(null);
        
        console.log('🚀 애니메이션 데이터 로드 시작...');
        
        // 병렬로 여러 API 호출
        const [animeListData, recommendedData, popularData] = await Promise.all([
          listAnime({ status: 'ONGOING', size: 50 }), // 방영중인 애니메이션만
          api.get('/api/anime/recommended?size=20'), // 개인화 추천 애니메이션
          listAnime({ isPopular: true, size: 20 }) // 인기 애니메이션
        ]);
        
        console.log('📊 API 응답 데이터:', { animeListData, recommendedData, popularData });
        console.log('📊 API 응답 상세:', {
          animeListDataKeys: Object.keys(animeListData || {}),
          recommendedDataKeys: Object.keys(recommendedData || {}),
          popularDataKeys: Object.keys(popularData || {})
        });
        
        const ongoingAnime = (animeListData as any).items || (animeListData as any).content || [];
        const newAnime = recommendedData || []; // 개인화 추천은 직접 배열
        const popularAnime = (popularData as any).items || (popularData as any).content || [];
        
        console.log('🔍 애니메이션 데이터 로드 결과:');
        console.log('방영중인 애니메이션:', ongoingAnime.length, ongoingAnime.slice(0, 3));
        console.log('개인화 추천 애니메이션:', Array.isArray(newAnime) ? newAnime.length : 0, Array.isArray(newAnime) ? newAnime.slice(0, 3) : []);
        console.log('인기 애니메이션:', popularAnime.length, popularAnime.slice(0, 3));
        
        // 필터링 전후 비교
        console.log('🔍 필터링 전후 비교:');
        console.log('방영중인 애니메이션 필터링 전:', ongoingAnime.length);
        console.log('방영중인 애니메이션 필터링 후:', ongoingAnime.filter((anime: any) => (anime.title && anime.title.trim()) || (anime.titleEn && anime.titleEn.trim())).length);
        console.log('첫 번째 애니메이션 필드들:', ongoingAnime[0] ? Object.keys(ongoingAnime[0]) : '없음');
        console.log('첫 번째 애니메이션 title/titleEn:', ongoingAnime[0] ? { title: ongoingAnime[0].title, titleEn: ongoingAnime[0].titleEn } : '없음');
        
        // title, titleEn, titleJp 중 하나라도 있는 애니메이션만 필터링
        setAnimeList(ongoingAnime.filter((anime: any) => 
          (anime.title && anime.title.trim()) || 
          (anime.titleEn && anime.titleEn.trim()) || 
          (anime.titleJp && anime.titleJp.trim())
        ));
        setRecommendedAnime(Array.isArray(newAnime) ? newAnime.filter((anime: any) => 
          (anime.title && anime.title.trim()) || 
          (anime.titleEn && anime.titleEn.trim()) || 
          (anime.titleJp && anime.titleJp.trim())
        ) : []);
        setPopularAnime(popularAnime.filter((anime: any) => 
          (anime.title && anime.title.trim()) || 
          (anime.titleEn && anime.titleEn.trim()) || 
          (anime.titleJp && anime.titleJp.trim())
        ));
      } catch (err: any) {
        console.error('애니메이션 데이터 로드 실패:', err);
        
        // 에러 메시지 개선
        let errorMessage = '애니메이션 데이터를 불러오는데 실패했습니다.';
        
        if (err.message.includes('서버에 연결할 수 없습니다')) {
          errorMessage = '백엔드 서버에 연결할 수 없습니다. 서버가 실행 중인지 확인해주세요.';
        } else if (err.message.includes('네트워크 연결')) {
          errorMessage = '네트워크 연결을 확인해주세요.';
        } else if (err.message.includes('API Error: 401')) {
          errorMessage = '인증이 필요합니다. 로그인해주세요.';
        } else if (err.message.includes('API Error: 404')) {
          errorMessage = '요청한 API를 찾을 수 없습니다.';
        } else if (err.message.includes('API Error: 500')) {
          errorMessage = '서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';
        }
        
        setError(errorMessage);
      } finally {
        setIsLoading(false);
      }
    };

    loadAnimeData();
  }, []);

  // 테스트용 임시 로그인 제거

  // 사용자 활동 기록
  const recordUserActivity = async (animeId: number, activityType: string) => {
    try {
      const params = new URLSearchParams();
      params.append('animeId', animeId.toString());
      params.append('activityType', activityType);
      
      await api.post(`/api/anime/activity?${params.toString()}`);
    } catch (error) {
      console.warn('사용자 활동 기록 실패:', error);
    }
  };

  // 애니메이션 클릭 핸들러
  const handleAnimeClick = async (anime: any) => {
    try {
      // 목록 DTO에는 필드가 적으므로 상세 조회로 모달 데이터 보강
      const id = anime?.aniId ?? anime?.id;
      if (id) {
        // 상세보기 활동 기록
        recordUserActivity(id, 'view');
        
        const detail = await getAnimeDetail(id);
        setSelectedAnime(detail);
      } else {
        // id가 없으면 목록 객체라도 표시
        setSelectedAnime(anime);
      }
    } catch (e) {
      console.warn('상세 조회 실패, 목록 데이터로 대체합니다.', e);
      setSelectedAnime(anime);
    } finally {
      setIsModalOpen(true);
    }
  };

  if (isLoading) {
    return (
      <div className={styles.loadingContainer}>
        <div className={styles.loadingText}>로딩 중...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className={styles.errorContainer}>
        <div className={styles.errorContent}>
          <div className={styles.errorMessage}>{error}</div>
          <div className={styles.errorHelp}>
            {error.includes('백엔드 서버') && (
              <div style={{ marginBottom: '0.5rem' }}>
                <p>• 백엔드 서버가 실행 중인지 확인해주세요</p>
                <p>• 터미널에서 <code>cd backend && ./gradlew bootRun</code> 실행</p>
              </div>
            )}
            {error.includes('네트워크') && (
              <p>• 인터넷 연결 상태를 확인해주세요</p>
            )}
          </div>
          <button className={styles.errorRetryButton} onClick={() => window.location.reload()}>
            페이지 새로고침
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.homeContainer}>
      {/* 헤더 네비게이션 */}
      <Header />
      
      {/* 메인 콘텐츠 */}
      <main className={styles.homeMain}>
        {/* 상단 애니 이미지 배너 - 적절한 비율로 */}
        <div className={styles.mainBanner}>
          {/* 배경 이미지 (귀멸의 칼날 탄지로) */}
          <div className={styles.bannerBackground}
               style={{
                 backgroundImage: 'url("https://placehold.co/1920x768/4a5568/ffffff?text=귀멸의+칼날+탄지로")'
               }}>
          </div>
          
          {/* 배너 내비게이션 점들 */}
          <div className={styles.bannerDots}>
            <div className={`${styles.bannerDot} ${styles.active}`}></div>
            <div className={styles.bannerDot}></div>
            <div className={styles.bannerDot}></div>
            <div className={styles.bannerDot}></div>
            <div className={styles.bannerDot}></div>
          </div>
          
          {/* 저작권 정보 */}
          <div className={styles.bannerCopyright}>
            ©Koyoharu Gotoge / SHUEISHA, Aniplex, ufotable
          </div>
          
          {/* 좌측 정보 패널 */}
          <div className={styles.bannerInfo}>
            <div className={styles.bannerContent}>
              <div className={styles.bannerBadge}>
                극장판
              </div>
              <div className={styles.bannerTitle}>
                귀멸의 칼날<br />
                <span className={styles.bannerTitleHighlight}>무한성원</span>
              </div>
              <div className={styles.bannerSubtitle}>
                8월 22일, 전국 극장 대개봉
              </div>
              <button className={styles.bannerButton}>
                보러가기 &gt;
              </button>
            </div>
          </div>
        </div>
        
        {/* 하단 하얀색 배경 영역 */}
        <section className={styles.contentSection}>
          {/* 요일별 스케줄 */}
          <div className={styles.contentContainer}>
            <WeeklySchedule 
              onAnimeClick={handleAnimeClick} 
              animeData={animeList}
            />
          </div>
          
          {/* 개인화 추천 애니메이션 */}
          {recommendedAnime.length > 0 && (
            <div className={styles.contentContainer}>
              <h2 className={styles.sectionTitle}>개인화 추천</h2>
              <div className={styles.carouselWrapper}>
                {recommendedScrollable && (
                  <button
                    className={`${styles.carouselButton} ${styles.carouselButtonLeft}`}
                    aria-label="왼쪽으로"
                    onClick={() => scrollByCard(recommendedRef, -1)}
                  >
                    ‹
                  </button>
                )}
                <div className={styles.carouselViewport}>
                  <div className={styles.carouselTrack} ref={recommendedRef}>
                    {recommendedAnime.map((anime: any, idx: number) => (
                      <div
                        key={anime.aniId ?? anime.id ?? idx}
                        className={`${styles.animeGridItem} ${styles.carouselItem}`}
                        onClick={() => handleAnimeClick(anime)}
                      >
                        <img
                          className={styles.animeGridPoster}
                          src={anime.posterUrl || '/placeholder-anime.jpg'}
                          alt={anime.title || anime.titleEn || anime.titleJp || '애니메이션 포스터'}
                        />
                        <div className={styles.animeGridTitle}>
                          {anime.title || anime.titleEn || anime.titleJp || '제목 없음'}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
                {recommendedScrollable && (
                  <button
                    className={`${styles.carouselButton} ${styles.carouselButtonRight}`}
                    aria-label="오른쪽으로"
                    onClick={() => scrollByCard(recommendedRef, 1)}
                  >
                    ›
                  </button>
                )}
              </div>
            </div>
          )}
          
          {/* 인기 애니메이션 */}
          {popularAnime.length > 0 && (
            <div className={styles.contentContainer}>
              <h2 className={styles.sectionTitle}>인기 애니메이션</h2>
              <div className={styles.carouselWrapper}>
                {popularScrollable && (
                  <button
                    className={`${styles.carouselButton} ${styles.carouselButtonLeft}`}
                    aria-label="왼쪽으로"
                    onClick={() => scrollByCard(popularRef, -1)}
                  >
                    ‹
                  </button>
                )}
                <div className={styles.carouselViewport}>
                  <div className={styles.carouselTrack} ref={popularRef}>
                    {popularAnime.map((anime: any, idx: number) => (
                      <div
                        key={anime.aniId ?? anime.id ?? idx}
                        className={`${styles.animeGridItem} ${styles.carouselItem}`}
                        onClick={() => handleAnimeClick(anime)}
                      >
                        <img
                          className={styles.animeGridPoster}
                          src={anime.posterUrl || '/placeholder-anime.jpg'}
                          alt={anime.title || anime.titleEn || anime.titleJp || '애니메이션 포스터'}
                        />
                        <div className={styles.animeGridTitle}>
                          {anime.title || anime.titleEn || anime.titleJp || '제목 없음'}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
                {popularScrollable && (
                  <button
                    className={`${styles.carouselButton} ${styles.carouselButtonRight}`}
                    aria-label="오른쪽으로"
                    onClick={() => scrollByCard(popularRef, 1)}
                  >
                    ›
                  </button>
                )}
              </div>
            </div>
          )}
          
          {/* 테스트용 버튼들 제거 */}
        </section>
      </main>

      {/* 애니메이션 상세 모달 */}
      {isModalOpen && selectedAnime && (
        <AnimeDetailModal
          anime={selectedAnime}
          isOpen={isModalOpen}
          onClose={() => setIsModalOpen(false)}
        />
      )}


    </div>
  );
}
