"use client";
import { useState, useEffect } from "react";

/**
 * 메인 배너 컴포넌트
 * 추천 애니메이션을 크게 보여주는 배너 섹션
 */
export default function MainBanner() {
  const [currentSlide, setCurrentSlide] = useState(0);
  
  // 임시 데이터 (실제로는 API에서 가져올 데이터)
  const bannerItems = [
    {
      id: 1,
      title: "귀멸의 칼날",
      subtitle: "무한성편",
      description: "8월 22일, 전국 극장 대개봉",
      backgroundImage: "https://placehold.co/1200x600/1a1a1a/ffffff?text=귀멸의+칼날",
      badge: "극장판"
    },
    {
      id: 2,
      title: "원피스",
      subtitle: "와노쿠니편",
      description: "새로운 에피소드 공개",
      backgroundImage: "https://placehold.co/1200x600/2a2a2a/ffffff?text=원피스",
      badge: "신작"
    }
  ];

  // 자동 슬라이드
  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentSlide((prev) => (prev + 1) % bannerItems.length);
    }, 5000);
    return () => clearInterval(timer);
  }, [bannerItems.length]);

  const currentItem = bannerItems[currentSlide];

  return (
    <section className="relative h-[70vh] min-h-[500px] overflow-hidden">
      {/* 배경 이미지 */}
      <div 
        className="absolute inset-0 bg-cover bg-center transition-all duration-1000"
        style={{ 
          backgroundImage: `linear-gradient(to right, rgba(0,0,0,0.8) 0%, rgba(0,0,0,0.4) 50%, rgba(0,0,0,0.8) 100%), url('${currentItem.backgroundImage}')`
        }}
      />

      {/* 콘텐츠 */}
      <div className="relative z-10 h-full flex items-center">
        <div className="max-w-7xl mx-auto px-4 w-full">
          <div className="max-w-lg">
            {/* 배지 */}
            <div className="inline-flex items-center mb-4">
              <span className="bg-red-600 text-white text-sm font-medium px-3 py-1 rounded-full">
                {currentItem.badge}
              </span>
            </div>

            {/* 제목 */}
            <h1 className="text-4xl md:text-6xl font-bold text-white mb-2">
              {currentItem.title}
            </h1>
            <h2 className="text-xl md:text-2xl text-white/80 mb-4">
              {currentItem.subtitle}
            </h2>

            {/* 설명 */}
            <p className="text-lg text-white/70 mb-8">
              {currentItem.description}
            </p>

            {/* 버튼 */}
            <div className="flex items-center space-x-4">
              <button className="bg-white text-black px-8 py-3 rounded-lg font-medium hover:bg-white/90 transition-colors">
                보러가기 ▷
              </button>
              <button className="border border-white/30 text-white px-8 py-3 rounded-lg font-medium hover:bg-white/10 transition-colors">
                더보기
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* 슬라이드 네비게이션 */}
      <div className="absolute bottom-8 left-1/2 transform -translate-x-1/2 flex space-x-2">
        {bannerItems.map((_, index) => (
          <button
            key={index}
            onClick={() => setCurrentSlide(index)}
            className={`w-3 h-3 rounded-full transition-colors ${
              index === currentSlide ? 'bg-white' : 'bg-white/30'
            }`}
          />
        ))}
      </div>

      {/* 화살표 네비게이션 */}
      <button
        onClick={() => setCurrentSlide((prev) => (prev - 1 + bannerItems.length) % bannerItems.length)}
        className="absolute left-4 top-1/2 transform -translate-y-1/2 bg-black/50 hover:bg-black/70 text-white p-3 rounded-full transition-colors"
      >
        ‹
      </button>
      <button
        onClick={() => setCurrentSlide((prev) => (prev + 1) % bannerItems.length)}
        className="absolute right-4 top-1/2 transform -translate-y-1/2 bg-black/50 hover:bg-black/70 text-white p-3 rounded-full transition-colors"
      >
        ›
      </button>
    </section>
  );
}
