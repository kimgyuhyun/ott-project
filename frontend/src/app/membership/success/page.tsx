"use client";
import { useEffect, useState, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Header from "@/components/layout/Header";
import styles from "./success.module.css";

function PaymentSuccessContent() {
  const router = useRouter();
  const searchParams = useSearchParams();

  useEffect(() => {
    // URL 파라미터에서 결제 정보 추출
    const impUid = searchParams.get('imp_uid');
    const merchantUid = searchParams.get('merchant_uid');
    const success = searchParams.get('success');

    if (success === 'true' && impUid && merchantUid) {
      // 결제 성공 처리
      console.log('결제 성공:', { impUid, merchantUid });
    }
  }, [searchParams]);

  return (
    <div className={styles.container}>
      <Header />
      <main className={styles.main}>
        <div className={styles.successCard}>
          <div className={styles.successIcon}>
            <svg width="64" height="64" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="10" fill="#4CAF50" stroke="#4CAF50" strokeWidth="2"/>
              <path d="M9 12l2 2 4-4" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </div>
          
          <h1 className={styles.title}>결제가 완료되었습니다!</h1>
          <p className={styles.message}>
            멤버십이 정상적으로 활성화되었습니다.<br />
            이제 모든 콘텐츠를 자유롭게 이용하실 수 있습니다.
          </p>
          
          <div className={styles.paymentInfo}>
            <div className={styles.infoRow}>
              <span className={styles.label}>결제 수단:</span>
              <span className={styles.value}>카카오페이</span>
            </div>
            <div className={styles.infoRow}>
              <span className={styles.label}>결제 금액:</span>
              <span className={styles.value}>9,900원</span>
            </div>
          </div>

          <div className={styles.actions}>
            <button 
              onClick={() => router.push('/membership/guide')}
              className={styles.primaryButton}
            >
              멤버십 확인
            </button>
            <button 
              onClick={() => router.push('/')}
              className={styles.secondaryButton}
            >
              아니요
            </button>
          </div>
        </div>
      </main>
    </div>
  );
}

export default function PaymentSuccessPage() {
  return (
    <Suspense fallback={<div className={styles.container}><main className={styles.main}>로딩 중...</main></div>}>
      <PaymentSuccessContent />
    </Suspense>
  );
}
