"use client";
import { useEffect, useState, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Header from "@/components/layout/Header";
import styles from "./success.module.css";
import { completePayment, checkPaymentStatus } from '@/lib/api/membership';
import { processProrationPayment } from '@/lib/api/proration';

// 모바일 결제(m_redirect_url)는 결제창 콜백이 실행되지 않으므로,
// 이 페이지가 리다이렉트 파라미터(paymentId/type/imp_uid)로 확정 API를 직접 호출한다.
// 파라미터가 없으면(데스크톱 팝업 흐름: 콜백에서 이미 확정 후 이동) 기존처럼 성공만 표시한다.
type ConfirmState = 'confirming' | 'success' | 'error';

function PaymentSuccessContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [confirmState, setConfirmState] = useState<ConfirmState>('confirming');
  const [errorMessage, setErrorMessage] = useState<string>('');

  useEffect(() => {
    const impUid = searchParams.get('imp_uid');
    const paymentId = searchParams.get('paymentId');
    const type = searchParams.get('type');
    const impSuccess = searchParams.get('imp_success') ?? searchParams.get('success');

    // 데스크톱(팝업) 흐름: 콜백에서 이미 확정 완료 → 그대로 성공 표시
    if (!paymentId || !impUid) {
      setConfirmState('success');
      return;
    }

    // 아임포트가 실패로 리다이렉트한 경우
    if (impSuccess === 'false') {
      setErrorMessage(searchParams.get('error_msg') || '결제에 실패했습니다.');
      setConfirmState('error');
      return;
    }

    // 모바일 리다이렉트 흐름: 서버 확정 API 호출(서버가 아임포트 재검증 후 지급/플랜 변경)
    (async () => {
      const id = Number(paymentId);
      try {
        if (type === 'proration') {
          await processProrationPayment(id, impUid);
        } else {
          await completePayment(id, impUid);
        }
        setConfirmState('success');
      } catch {
        // 이미 확정된 결제(웹훅 선처리/재방문)면 성공으로 간주
        try {
          const status = await checkPaymentStatus(id);
          if (status.status === 'SUCCEEDED') {
            setConfirmState('success');
          } else {
            setErrorMessage('결제 확정에 실패했습니다. 잠시 후 멤버십 상태를 확인해주세요.');
            setConfirmState('error');
          }
        } catch {
          setErrorMessage('결제 확정에 실패했습니다. 잠시 후 멤버십 상태를 확인해주세요.');
          setConfirmState('error');
        }
      }
    })();
  }, [searchParams]);

  if (confirmState === 'confirming') {
    return (
      <div className={styles.container}>
        <Header />
        <main className={styles.main}>
          <div className={styles.successCard}>
            <h1 className={styles.title}>결제를 확정하는 중입니다...</h1>
            <p className={styles.message}>잠시만 기다려주세요.</p>
          </div>
        </main>
      </div>
    );
  }

  if (confirmState === 'error') {
    return (
      <div className={styles.container}>
        <Header />
        <main className={styles.main}>
          <div className={styles.successCard}>
            <h1 className={styles.title}>결제 확정에 문제가 발생했습니다</h1>
            <p className={styles.message}>{errorMessage}</p>
            <div className={styles.actions}>
              <button
                onClick={() => router.push('/membership')}
                className={styles.primaryButton}
              >
                멤버십 페이지로
              </button>
              <button
                onClick={() => router.push('/')}
                className={styles.secondaryButton}
              >
                홈으로
              </button>
            </div>
          </div>
        </main>
      </div>
    );
  }

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
