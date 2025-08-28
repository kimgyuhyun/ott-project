"use client";
import React from "react";

interface PaymentFailureModalProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  message?: string;
}

export default function PaymentFailureModal({ isOpen, onClose, title = '멤버십 구매에 실패했습니다', message = '결제 실패' }: PaymentFailureModalProps) {
  if (!isOpen) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center" style={{ backgroundColor: 'var(--background-dim-1, rgba(0,0,0,0.7))' }}>
      <div className="rounded-lg p-8 w-[90%] max-w-md border text-center" style={{ 
        backgroundColor: 'var(--background-1, #121212)', 
        borderColor: 'var(--border-1, #323232)' 
      }}>
        <h3 className="text-2xl font-bold mb-6" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>{title}</h3>
        <p className="mb-8" style={{ color: 'var(--foreground-3, #ABABAB)' }}>{message}</p>
        <button
          onClick={onClose}
          className="px-6 py-3 rounded-lg font-bold"
          style={{ backgroundColor: 'var(--foreground-slight, #816BFF)', color: 'var(--foreground-1, #F7F7F7)' }}
        >
          확인
        </button>
      </div>
    </div>
  );
}


