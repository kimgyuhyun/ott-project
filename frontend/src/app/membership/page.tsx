"use client";
import { useState } from "react";
import Link from "next/link";
import Header from "@/components/layout/Header";
import MembershipModal from "@/components/membership/MembershipModal";

/**
 * 멤버십 페이지
 * Black Butler 캐릭터 배경과 멤버십 안내 텍스트, 시작 버튼 포함
 */
export default function MembershipPage() {
  const [isModalOpen, setIsModalOpen] = useState(false);

  return (
    <div className="min-h-screen bg-black">
      <Header />
      
      {/* 메인 콘텐츠 */}
      <main className="relative pt-16 min-h-screen">
        {/* 배경 이미지 */}
        <div className="absolute inset-0 z-0">
          <div className="w-full h-full bg-gradient-to-b from-gray-900 via-black to-black">
            {/* Black Butler 캐릭터 이미지 (플레이스홀더) */}
            <div className="absolute inset-0 bg-cover bg-center bg-no-repeat opacity-20"
                 style={{
                   backgroundImage: 'url("https://via.placeholder.com/1920x1080/1a1a1a/ffffff?text=Black+Butler+Character")'
                 }}>
            </div>
          </div>
        </div>

        {/* 콘텐츠 오버레이 */}
        <div className="relative z-10 flex flex-col items-center justify-center min-h-screen px-4">
          {/* 메인 텍스트 */}
          <div className="text-center max-w-4xl mb-16">
            <h1 className="text-4xl md:text-6xl font-bold text-white mb-4 leading-tight">
              동시방영 신작부터
            </h1>
            <h2 className="text-3xl md:text-5xl font-bold text-white leading-tight">
              역대 인기작까지 한 곳에서
            </h2>
          </div>

          {/* 멤버십 플랜 비교 섹션 */}
          <div className="w-full max-w-6xl mb-16">
            <div className="text-center mb-8">
              <h3 className="text-3xl md:text-4xl font-bold text-white mb-4">
                나에게 맞는 멤버십을 확인하세요
              </h3>
              <p className="text-white/80 text-lg">
                멤버십은 언제든 해지가 가능해요.
              </p>
            </div>

            {/* 플랜 카드들 */}
            <div className="grid md:grid-cols-2 gap-8">
              {/* 베이직 플랜 */}
              <div className="bg-gray-800/80 backdrop-blur-sm rounded-2xl p-8 border border-gray-700">
                <div className="text-center mb-6">
                  <h4 className="text-2xl font-bold text-white mb-2">베이직</h4>
                  <p className="text-3xl font-bold text-purple-400">월 9,900원</p>
                </div>
                
                <div className="space-y-4">
                  <div className="flex items-center space-x-3">
                    <div className="w-5 h-5 bg-purple-500 rounded-full flex items-center justify-center">
                      <svg className="w-3 h-3 text-white" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                      </svg>
                    </div>
                    <span className="text-white">프로필 1인·동시재생 1회선</span>
                  </div>
                  <div className="flex items-center space-x-3">
                    <div className="w-5 h-5 bg-purple-500 rounded-full flex items-center justify-center">
                      <svg className="w-3 h-3 text-white" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                      </svg>
                    </div>
                    <span className="text-white">최신화 시청</span>
                  </div>
                  <div className="flex items-center space-x-3">
                    <div className="w-5 h-5 bg-purple-500 rounded-full flex items-center justify-center">
                      <svg className="w-3 h-3 text-white" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                      </svg>
                    </div>
                    <span className="text-white">다운로드 지원</span>
                  </div>
                  <div className="flex items-center space-x-3">
                    <div className="w-5 h-5 bg-purple-500 rounded-full flex items-center justify-center">
                      <svg className="w-3 h-3 text-white" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                      </svg>
                    </div>
                    <span className="text-white">FHD 화질 지원</span>
                  </div>
                  <div className="flex items-center space-x-3">
                    <div className="w-5 h-5 bg-purple-500 rounded-full flex items-center justify-center">
                      <svg className="w-3 h-3 text-white" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                      </svg>
                    </div>
                    <span className="text-white">TV 앱 지원</span>
                  </div>
                </div>
              </div>

              {/* 프리미엄 플랜 */}
              <div className="bg-gray-800/80 backdrop-blur-sm rounded-2xl p-8 border border-gray-700">
                <div className="text-center mb-6">
                  <h4 className="text-2xl font-bold text-white mb-2">프리미엄</h4>
                  <p className="text-3xl font-bold text-purple-400">월 14,900원</p>
                </div>
                
                <div className="space-y-4">
                  <div className="flex items-center space-x-3">
                    <div className="w-5 h-5 bg-purple-500 rounded-full flex items-center justify-center">
                      <svg className="w-3 h-3 text-white" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                      </svg>
                    </div>
                    <span className="text-white">프로필 4인·동시재생 4회선</span>
                  </div>
                  <div className="flex items-center space-x-3">
                    <div className="w-5 h-5 bg-purple-500 rounded-full flex items-center justify-center">
                      <svg className="w-3 h-3 text-white" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                      </svg>
                    </div>
                    <span className="text-white">최신화 시청</span>
                  </div>
                  <div className="flex items-center space-x-3">
                    <div className="w-5 h-5 bg-purple-500 rounded-full flex items-center justify-center">
                      <svg className="w-3 h-3 text-white" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                      </svg>
                    </div>
                    <span className="text-white">다운로드 지원</span>
                  </div>
                  <div className="flex items-center space-x-3">
                    <div className="w-5 h-5 bg-purple-500 rounded-full flex items-center justify-center">
                      <svg className="w-3 h-3 text-white" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                      </svg>
                    </div>
                    <span className="text-white">FHD 화질 지원</span>
                  </div>
                  <div className="flex items-center space-x-3">
                    <div className="w-5 h-5 bg-purple-500 rounded-full flex items-center justify-center">
                      <svg className="w-3 h-3 text-white" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                      </svg>
                    </div>
                    <span className="text-white">TV 앱 지원</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* 멤버십 시작 버튼 */}
          <div className="text-center">
            <button
              onClick={() => setIsModalOpen(true)}
              className="px-12 py-4 bg-purple-600 hover:bg-purple-700 text-white text-xl font-semibold rounded-lg transition-colors duration-200 transform hover:scale-105"
            >
              멤버십 시작하기
            </button>
          </div>

          {/* 멤버십 유의사항 */}
          <div className="text-center mt-8">
            <p className="text-white/60 text-sm">
              멤버십 유의사항
            </p>
          </div>
        </div>

        {/* 저작권 정보 */}
        <div className="absolute bottom-4 right-4 z-10">
          <p className="text-white/60 text-sm">
            ©Yana Toboso/SQUARE ENIX, Kuroshitsuji2 Project, MBS
          </p>
        </div>
      </main>

      {/* 멤버십 선택 모달 */}
      <MembershipModal 
        isOpen={isModalOpen} 
        onClose={() => setIsModalOpen(false)} 
      />
    </div>
  );
}
