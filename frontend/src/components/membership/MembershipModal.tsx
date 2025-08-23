"use client";
import { useState } from "react";

interface MembershipModalProps {
  isOpen: boolean;
  onClose: () => void;
}

/**
 * 멤버십 선택 모달
 * Basic과 Premium 플랜 중 선택 가능
 */
export default function MembershipModal({ isOpen, onClose }: MembershipModalProps) {
  const [selectedPlan, setSelectedPlan] = useState<'basic' | 'premium'>('basic');

  if (!isOpen) return null;

  const plans = {
    basic: {
      name: '베이직',
      features: '프로필 1인 · 동시재생 1회선',
      price: '월 9,900원',
      color: 'border-purple-500'
    },
    premium: {
      name: '프리미엄',
      features: '프로필 4인 · 동시재생 4회선',
      price: '월 14,900원',
      color: 'border-gray-600'
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* 배경 오버레이 */}
      <div 
        className="absolute inset-0 bg-black/50 backdrop-blur-sm"
        onClick={onClose}
      />
      
      {/* 모달 컨테이너 */}
      <div className="relative bg-gray-800 rounded-2xl p-8 max-w-md w-full mx-4 shadow-2xl">
        {/* 닫기 버튼 */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-white/70 hover:text-white transition-colors"
          aria-label="닫기"
        >
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>

        {/* 헤더 */}
        <div className="text-center mb-8">
          <h2 className="text-2xl font-bold text-gray-200 mb-2">
            멤버십 선택
          </h2>
          <p className="text-gray-400 text-sm">
            언제든 해지가 가능해요!
          </p>
        </div>

        {/* 플랜 선택 */}
        <div className="space-y-4 mb-8">
          {/* Basic 플랜 */}
          <div
            className={`p-4 rounded-lg border-2 cursor-pointer transition-all ${
              selectedPlan === 'basic' 
                ? 'border-purple-500 bg-purple-500/10' 
                : 'border-gray-600 bg-gray-700/50 hover:border-gray-500'
            }`}
            onClick={() => setSelectedPlan('basic')}
          >
            <div className="flex justify-between items-center">
              <span className="text-white font-medium">{plans.basic.name}</span>
              <span className="text-white/80 text-sm">{plans.basic.features}</span>
            </div>
            <div className="mt-2">
              <span className="text-white font-semibold">{plans.basic.price}</span>
            </div>
          </div>

          {/* Premium 플랜 */}
          <div
            className={`p-4 rounded-lg border-2 cursor-pointer transition-all ${
              selectedPlan === 'premium' 
                ? 'border-purple-500 bg-purple-500/10' 
                : 'border-gray-600 bg-gray-700/50 hover:border-gray-500'
            }`}
            onClick={() => setSelectedPlan('premium')}
          >
            <div className="flex justify-between items-center">
              <span className="text-white font-medium">{plans.premium.name}</span>
              <span className="text-white/80 text-sm">{plans.premium.features}</span>
            </div>
            <div className="mt-2">
              <span className="text-white font-semibold">{plans.premium.price}</span>
            </div>
          </div>
        </div>

        {/* 시작하기 버튼 */}
        <button
          onClick={() => {
            // TODO: 멤버십 결제 로직 구현
            console.log(`${selectedPlan} 멤버십 시작`);
            onClose();
          }}
          className="w-full py-4 bg-purple-600 hover:bg-purple-700 text-white font-semibold rounded-lg transition-colors duration-200 transform hover:scale-105"
        >
          {plans[selectedPlan].name} 멤버십 시작하기
        </button>
      </div>
    </div>
  );
}
