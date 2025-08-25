"use client";
import { useState, useEffect } from "react";
import Header from "@/components/layout/Header";
import WeeklySchedule from "@/components/home/WeeklySchedule";
import AnimeDetailModal from "@/components/anime/AnimeDetailModal";
import { useAuth } from "@/lib/AuthContext";
import { getAnimeList, getRecommendedAnime, getPopularAnime } from "@/lib/api/anime";
import { api } from "@/lib/api/index";


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
        
        // 병렬로 여러 API 호출
        const [animeListData, recommendedData, popularData] = await Promise.all([
          getAnimeList(0, 20),
          getRecommendedAnime(),
          getPopularAnime()
        ]);
        
        setAnimeList((animeListData as any).content || []);
        setRecommendedAnime((recommendedData as any) || []);
        setPopularAnime((popularData as any) || []);
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

  // 애니메이션 클릭 핸들러
  const handleAnimeClick = (anime: any) => {
    setSelectedAnime(anime);
    setIsModalOpen(true);
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-xl text-gray-600">로딩 중...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-center space-y-4">
          <div className="text-xl text-red-600">{error}</div>
          <div className="text-sm text-gray-600">
            {error.includes('백엔드 서버') && (
              <div className="space-y-2">
                <p>• 백엔드 서버가 실행 중인지 확인해주세요</p>
                <p>• 터미널에서 <code className="bg-gray-100 px-2 py-1 rounded">cd backend && ./gradlew bootRun</code> 실행</p>
              </div>
            )}
            {error.includes('네트워크') && (
              <p>• 인터넷 연결 상태를 확인해주세요</p>
            )}
          </div>
          <button 
            onClick={() => window.location.reload()} 
            className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 transition-colors"
          >
            페이지 새로고침
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen">
      {/* 헤더 네비게이션 */}
      <Header />
      
      {/* 메인 콘텐츠 */}
      <main className="pt-16">
        {/* 상단 애니 이미지 배너 - 적절한 비율로 */}
        <div className="w-full h-96 bg-gradient-to-br from-purple-900 via-pink-800 to-purple-900 relative overflow-hidden">
          {/* 배경 이미지 (귀멸의 칼날 탄지로) */}
          <div className="absolute inset-0 bg-cover bg-center bg-no-repeat opacity-80"
               style={{
                 backgroundImage: 'url("https://placehold.co/1920x768/4a5568/ffffff?text=귀멸의+칼날+탄지로")'
               }}>
          </div>
          
          {/* 배너 내비게이션 점들 */}
          <div className="absolute bottom-8 right-8 flex space-x-2">
            <div className="w-3 h-3 bg-white rounded-full opacity-100"></div>
            <div className="w-3 h-3 bg-white/50 rounded-full"></div>
            <div className="w-3 h-3 bg-white/50 rounded-full"></div>
            <div className="w-3 h-3 bg-white/50 rounded-full"></div>
            <div className="w-3 h-3 bg-white/50 rounded-full"></div>
          </div>
          
          {/* 저작권 정보 */}
          <div className="absolute bottom-4 right-4 text-white/60 text-xs">
            ©Koyoharu Gotoge / SHUEISHA, Aniplex, ufotable
          </div>
          
          {/* 좌측 정보 패널 */}
          <div className="absolute left-8 top-1/2 transform -translate-y-1/2 text-white z-10">
            <div className="space-y-4">
              <div className="text-sm font-medium bg-red-600 text-white px-3 py-1 rounded-full inline-block">
                극장판
              </div>
              <div className="text-4xl font-bold mb-2">
                귀멸의 칼날<br />
                <span className="text-red-500">무한성원</span>
              </div>
              <div className="text-lg text-white/90 mb-6">
                8월 22일, 전국 극장 대개봉
              </div>
              <button className="bg-white text-black px-6 py-3 rounded-lg font-semibold hover:bg-gray-100 transition-colors">
                보러가기 &gt;
              </button>
            </div>
          </div>
        </div>
        
        {/* 하단 하얀색 배경 영역 */}
        <div className="bg-white">
          {/* 요일별 스케줄 */}
          <div className="max-w-7xl mx-auto px-6 py-12">
            <WeeklySchedule />
          </div>
          
          {/* 추천 애니메이션 */}
          {recommendedAnime.length > 0 && (
            <div className="max-w-7xl mx-auto px-6 py-8">
              <h2 className="text-2xl font-bold text-gray-800 mb-6">추천 애니메이션</h2>
              <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
                {recommendedAnime.slice(0, 6).map((anime: any, idx: number) => (
                  <div 
                    key={anime.aniId ?? anime.id ?? idx} 
                    className="cursor-pointer hover:scale-105 transition-transform"
                    onClick={() => handleAnimeClick(anime)}
                  >
                    <div className="w-full aspect-[3/4] bg-gray-200 rounded-lg mb-2"></div>
                    <p className="text-sm text-gray-800 truncate">{anime.title}</p>
                  </div>
                ))}
              </div>
            </div>
          )}
          
          {/* 인기 애니메이션 */}
          {popularAnime.length > 0 && (
            <div className="max-w-7xl mx-auto px-6 py-8">
              <h2 className="text-2xl font-bold text-gray-800 mb-6">인기 애니메이션</h2>
              <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
                {popularAnime.slice(0, 6).map((anime: any, idx: number) => (
                  <div 
                    key={anime.aniId ?? anime.id ?? idx} 
                    className="cursor-pointer hover:scale-105 transition-transform"
                    onClick={() => handleAnimeClick(anime)}
                  >
                    <div className="w-full aspect-[3/4] bg-gray-200 rounded-lg mb-2"></div>
                    <p className="text-sm text-gray-800 truncate">{anime.title}</p>
                  </div>
                ))}
              </div>
            </div>
          )}
          
          {/* 테스트용 버튼들 제거 */}
        </div>
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
