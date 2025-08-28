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
        className="absolute inset-0 backdrop-blur-sm"
        style={{ backgroundColor: 'var(--background-dim-1, rgba(0,0,0,0.7))' }}
        onClick={onClose}
      />
      
      {/* 모달 컨테이너 */}
      <div className="relative rounded-2xl p-8 max-w-md w-full mx-4 shadow-2xl" style={{ 
        backgroundColor: 'var(--background-1, #121212)',
        border: '1px solid var(--border-1, #323232)'
      }}>
        {/* 닫기 버튼 */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 transition-colors"
          style={{ color: 'var(--foreground-3, #ABABAB)' }}
          aria-label="닫기"
        >
          <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>

        {/* 헤더 */}
        <div className="text-center mb-8">
          <h2 className="text-2xl font-bold mb-2" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>
            멤버십 선택
          </h2>
          <p className="text-sm" style={{ color: 'var(--foreground-3, #ABABAB)' }}>
            언제든 해지가 가능해요!
          </p>
        </div>

        {/* 플랜 선택 */}
        <div className="space-y-4 mb-8">
          {/* Basic 플랜 */}
          <div
            className={`p-4 rounded-lg border-2 cursor-pointer transition-all`}
            style={{
              backgroundColor: selectedPlan === 'basic' 
                ? 'var(--background-highlight, rgba(129, 107, 255, 0.1))' 
                : 'var(--background-2, #000000)',
              borderColor: selectedPlan === 'basic' 
                ? 'var(--foreground-slight, #816BFF)' 
                : 'var(--border-1, #323232)'
            }}
            onClick={() => setSelectedPlan('basic')}
          >
            <div className="flex justify-between items-center">
              <span className="font-medium" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>{plans.basic.name}</span>
              <span className="text-sm" style={{ color: 'var(--foreground-3, #ABABAB)' }}>{plans.basic.features}</span>
            </div>
            <div className="mt-2">
              <span className="font-semibold" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>{plans.basic.price}</span>
            </div>
          </div>

          {/* Premium 플랜 */}
          <div
            className={`p-4 rounded-lg border-2 cursor-pointer transition-all`}
            style={{
              backgroundColor: selectedPlan === 'premium' 
                ? 'var(--background-highlight, rgba(129, 107, 255, 0.1))' 
                : 'var(--background-2, #000000)',
              borderColor: selectedPlan === 'premium' 
                ? 'var(--foreground-slight, #816BFF)' 
                : 'var(--border-1, #323232)'
            }}
            onClick={() => setSelectedPlan('premium')}
          >
            <div className="flex justify-between items-center">
              <span className="font-medium" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>{plans.premium.name}</span>
              <span className="text-sm" style={{ color: 'var(--foreground-3, #ABABAB)' }}>{plans.premium.features}</span>
            </div>
            <div className="mt-2">
              <span className="font-semibold" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>{plans.premium.price}</span>
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
          className="w-full py-4 font-semibold rounded-lg transition-colors duration-200 transform hover:scale-105"
          style={{ backgroundColor: 'var(--foreground-slight, #816BFF)', color: 'var(--foreground-1, #F7F7F7)' }}
        >
          {plans[selectedPlan].name} 멤버십 시작하기
        </button>
      </div>
    </div>
  );
}
