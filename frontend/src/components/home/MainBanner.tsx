"use client";
import { useState, useEffect } from "react";
import styles from "./MainBanner.module.css";

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
    <div className={styles.bannerContainer}>
      {/* 배경 이미지 */}
      <div 
        className={styles.bannerBackground}
        style={{ 
          backgroundImage: `url('${currentItem.backgroundImage}')`
        }}
      />

      {/* 콘텐츠 */}
      <div className={styles.bannerInfo}>
        <div className={styles.bannerContent}>
          {/* 배지 */}
          <div className={styles.bannerBadge}>
            {currentItem.badge}
          </div>

          {/* 제목 */}
          <h1 className={styles.bannerTitle}>
            {currentItem.title}
          </h1>
          <p className={styles.bannerSubtitle}>
            {currentItem.subtitle}
          </p>

          {/* 설명 */}
          <p className={styles.bannerDescription}>
            {currentItem.description}
          </p>

          {/* 버튼 */}
          <div className={styles.bannerButtonGroup}>
            <button className={styles.bannerButton}>
              보러가기 ▷
            </button>
            <button className={styles.bannerMoreButton}>
              더보기
            </button>
          </div>
        </div>
      </div>

      {/* 슬라이드 네비게이션 */}
      <div className={styles.bannerDots}>
        {bannerItems.map((_, index) => (
          <div
            key={index}
            className={`${styles.bannerDot} ${index === currentSlide ? styles.active : ''}`}
            onClick={() => setCurrentSlide(index)}
          />
        ))}
      </div>

      {/* 화살표 네비게이션 */}
      <button
        className={`${styles.bannerArrow} ${styles.bannerArrowLeft}`}
        onClick={() => setCurrentSlide((prev) => (prev - 1 + bannerItems.length) % bannerItems.length)}
      >
        ‹
      </button>
      <button
        className={`${styles.bannerArrow} ${styles.bannerArrowRight}`}
        onClick={() => setCurrentSlide((prev) => (prev + 1) % bannerItems.length)}
      >
        ›
      </button>
    </div>
  );
}
