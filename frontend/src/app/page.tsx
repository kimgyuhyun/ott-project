"use client";
import { useState } from "react";
import Header from "@/components/layout/Header";
import WeeklySchedule from "@/components/home/WeeklySchedule";
import AnimeDetailModal from "@/components/anime/AnimeDetailModal";
import { useAuth } from "@/lib/AuthContext";

/**
 * 메인 홈페이지
 * 라프텔 스타일의 홈화면 레이아웃
 */
export default function Home() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const { user, isAuthenticated, login, logout } = useAuth();

  // 테스트용 임시 로그인
  const handleTestLogin = () => {
    login({
      id: "1",
      username: "김규현",
      email: "test@example.com",
      profileImage: undefined
    });
  };

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
                 backgroundImage: 'url("https://via.placeholder.com/1920x768/4a5568/ffffff?text=귀멸의+칼날+탄지로")'
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
          
          {/* 테스트용 버튼들 */}
          <div className="max-w-7xl mx-auto px-6 py-8 text-center space-y-4">
            {/* 인증 상태 표시 */}
            <div className="text-gray-800">
              <p>현재 상태: {isAuthenticated ? `로그인됨 (${user?.username})` : '로그아웃됨'}</p>
            </div>
            
            {/* 로그인/로그아웃 테스트 버튼 */}
            <div className="space-x-4">
              {!isAuthenticated ? (
                <button
                  onClick={handleTestLogin}
                  className="px-6 py-3 bg-green-600 hover:bg-green-700 text-white font-semibold rounded-lg transition-colors"
                >
                  🔑 테스트 로그인
                </button>
              ) : (
                <button
                  onClick={logout}
                  className="px-6 py-3 bg-red-600 hover:bg-red-700 text-white font-semibold rounded-lg transition-colors"
                >
                  🚪 테스트 로그아웃
                </button>
              )}
            </div>
            
            {/* 애니 상세 모달 테스트 버튼 */}
            <div>
              <button
                onClick={() => setIsModalOpen(true)}
                className="px-6 py-3 bg-purple-600 hover:bg-purple-700 text-white font-semibold rounded-lg transition-colors"
              >
                🎬 애니 상세 모달 테스트
              </button>
            </div>
          </div>
        </div>
      </main>

      {/* 애니 상세 모달 */}
      <AnimeDetailModal 
        isOpen={isModalOpen} 
        onClose={() => setIsModalOpen(false)} 
      />
    </div>
  );
}
