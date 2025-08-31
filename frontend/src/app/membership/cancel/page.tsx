"use client";
import { useEffect, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Header from "@/components/layout/Header";
import styles from "./cancel.module.css";

export default function PaymentCancelPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [countdown, setCountdown] = useState(5);

  useEffect(() => {
    // URL 파라미터에서 결제 정보 추출
    const impUid = searchParams.get('imp_uid');
    const merchantUid = searchParams.get('merchant_uid');
    const errorMsg = searchParams.get('error_msg');

    if (errorMsg) {
      console.log('결제 취소:', { impUid, merchantUid, errorMsg });
    }

    // 5초 후 멤버십 페이지로 리다이렉트
    const timer = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          router.push('/membership');
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [router, searchParams]);

  return (
    <div className={styles.container}>
      <Header />
      <main className={styles.main}>
        <div className={styles.cancelCard}>
          <div className={styles.cancelIcon}>
            <svg width="64" height="64" viewBox="0 0 24 24" fill="none">
              <circle cx="12" cy="12" r="10" fill="#FF6B6B" stroke="#FF6B6B" strokeWidth="2"/>
              <path d="M15 9l-6 6M9 9l6 6" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </div>
          
          <h1 className={styles.title}>결제가 취소되었습니다</h1>
          <p className={styles.message}>
            결제가 취소되었습니다.<br />
            다시 시도하시거나 다른 결제 수단을 선택해 주세요.
          </p>
          
          <div className={styles.actions}>
            <button 
              onClick={() => router.push('/membership')}
              className={styles.primaryButton}
            >
              멤버십 다시 선택
            </button>
            <button 
              onClick={() => router.push('/')}
              className={styles.secondaryButton}
            >
              홈으로 이동
            </button>
          </div>

          <p className={styles.redirectMessage}>
            {countdown}초 후 자동으로 멤버십 페이지로 이동합니다.
          </p>
        </div>
      </main>
    </div>
  );
}
