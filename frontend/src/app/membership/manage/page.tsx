"use client";
import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import Header from "@/components/layout/Header";
import { useMembershipData } from "@/hooks/useMembershipData";
import { cancelMembership } from "@/lib/api/membership";
import PaymentMethodChangeModal from "@/components/membership/PaymentMethodChangeModal";
import styles from "./manage.module.css";

export default function MembershipManagePage() {
  const { membershipPlans, userMembership, paymentMethods, isLoading, error, reloadUserMembership } = useMembershipData();
  const [isCancelling, setIsCancelling] = useState(false);
  const [isPaymentMethodModalOpen, setIsPaymentMethodModalOpen] = useState(false);
  
  // 플랜 이름 한국어 매핑
  const translatePlanName = (name?: string | null) => {
    if (!name) return '';
    const map: Record<string, string> = {
      'Basic Monthly': '베이직',
      'Premium Monthly': '프리미엄',
    };
    return map[name] || name;
  };

  // 사용자의 현재 플랜 정보
  const planForUser = useMemo(() => {
    if (!userMembership) return null;
    const matched = membershipPlans.find(p => {
      const byCode = p.code && userMembership.planCode && p.code === userMembership.planCode;
      const byName = p.name && userMembership.planName && p.name === userMembership.planName;
      return byCode || byName;
    });
    return matched;
  }, [userMembership, membershipPlans]);

  // 결제 예정일 계산 (다음 결제일)
  const nextBillingDate = useMemo(() => {
    if (!userMembership?.nextBillingAt) return null;
    const date = new Date(userMembership.nextBillingAt);
    return date.toLocaleDateString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    }).replace(/\. /g, '.').replace('.', '');
  }, [userMembership]);

  // 결제 수단 변경 핸들러 (모달 열기)
  const handleChangePaymentMethod = () => {
    setIsPaymentMethodModalOpen(true);
  };

  // 멤버십 해지 핸들러
  const handleCancelMembership = async () => {
    if (!userMembership || isCancelling) return;
    
    if (!confirm('정말로 멤버십을 해지하시겠습니까? 해지 즉시 서비스 이용이 중단됩니다.')) {
      return;
    }

    setIsCancelling(true);
    try {
      await cancelMembership();
      alert('멤버십이 해지되었습니다.');
      await reloadUserMembership();
      window.location.href = '/membership';
    } catch (error) {
      console.error('멤버십 해지 실패:', error);
      alert('멤버십 해지에 실패했습니다. 다시 시도해주세요.');
    } finally {
      setIsCancelling(false);
    }
  };

  // 로딩 상태
  if (isLoading) {
    return (
      <div className={styles.manageContainer}>
        <Header />
        <div className={styles.mainContent}>
          <div className={styles.loadingContainer}>
            <div className={styles.loadingSpinner}></div>
            <p className={styles.loadingText}>멤버십 정보를 불러오는 중...</p>
          </div>
        </div>
      </div>
    );
  }

  // 에러 상태
  if (error) {
    return (
      <div className={styles.manageContainer}>
        <Header />
        <div className={styles.mainContent}>
          <div className={styles.errorContainer}>
            <p className={styles.errorText}>{error}</p>
            <button 
              className={styles.retryButton}
              onClick={() => window.location.reload()}
            >
              다시 시도
            </button>
          </div>
        </div>
      </div>
    );
  }

  // 멤버십이 없는 경우
  if (!userMembership) {
    return (
      <div className={styles.manageContainer}>
        <Header />
        <div className={styles.mainContent}>
          <div className={styles.noMembershipContainer}>
            <h2 className={styles.noMembershipTitle}>멤버십이 없습니다</h2>
            <p className={styles.noMembershipText}>
              멤버십을 구독하여 다양한 혜택을 누려보세요.
            </p>
            <Link href="/membership" className={styles.subscribeButton}>
              멤버십 구독하기
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.manageContainer}>
      <Header />
      <div className={styles.mainContent}>
        {/* 페이지 헤더 */}
        <div className={styles.pageHeader}>
          <h1 className={styles.pageTitle}>내 멤버십 관리</h1>
        </div>

        {/* 현재 멤버십 정보 */}
        <div className={styles.currentMembershipCard}>
          {/* 결제 예정 멤버십 */}
          <div className={styles.membershipSection}>
            <h3 className={styles.sectionTitle}>결제 예정 멤버십</h3>
            <div className={styles.planInfo}>
              <h4 className={styles.planName}>{translatePlanName(planForUser?.name) || userMembership.planName}</h4>
            </div>
          </div>

          {/* 결제 수단 */}
          <div className={styles.membershipSection}>
            <h3 className={styles.sectionTitle}>결제 수단</h3>
            <div className={styles.paymentMethodInfo}>
              {/* 현재 결제 수단 정보 */}
              {paymentMethods.length > 0 ? (
                <div className={styles.currentPaymentMethod}>
                  <div className={styles.paymentMethodIcon}>
                    <span className={styles.paymentMethodIconText}>
                      {paymentMethods[0].type === 'card' ? '💳' : '📱'}
                    </span>
                  </div>
                  <span className={styles.paymentMethodText}>
                    {paymentMethods[0].type === 'card' 
                      ? `${paymentMethods[0].brand || '카드'} ${paymentMethods[0].last4 ? `****${paymentMethods[0].last4}` : ''}`
                      : '휴대폰 결제'
                    }
                  </span>
                </div>
              ) : (
                <div className={styles.noPaymentMethod}>
                  <span className={styles.noPaymentMethodText}>등록된 결제 수단이 없습니다</span>
                </div>
              )}
              
              {/* 결제 수단 변경 버튼 */}
              <button 
                className={styles.changePaymentMethodButton}
                onClick={handleChangePaymentMethod}
              >
                결제 수단 변경
              </button>
            </div>
          </div>

          {/* 결제 예정일 */}
          <div className={styles.membershipSection}>
            <h3 className={styles.sectionTitle}>결제 예정일</h3>
            <div className={styles.detailValue}>
              {nextBillingDate || '정보 없음'}
            </div>
          </div>

          {/* 결제 예정 금액 */}
          <div className={styles.membershipSection}>
            <h3 className={styles.sectionTitle}>결제 예정 금액</h3>
            <div className={styles.planPrice}>
              {planForUser ? `${planForUser.monthlyPrice.toLocaleString()}원` : '정보 없음'}
            </div>
          </div>
        </div>

        {/* 멤버십 해지 버튼 */}
        <div className={styles.cancelSection}>
          <button 
            className={styles.cancelMembershipButton}
            onClick={handleCancelMembership}
            disabled={isCancelling}
          >
            {isCancelling ? '처리 중...' : '멤버십 해지'}
          </button>
        </div>

        {/* 돌아가기 링크 */}
        <div className={styles.backLinkContainer}>
          <Link href="/membership" className={styles.backLink}>
            ← 멤버십 페이지로 돌아가기
          </Link>
        </div>
      </div>

      {/* 결제 수단 변경 모달 */}
      <PaymentMethodChangeModal
        isOpen={isPaymentMethodModalOpen}
        onClose={() => setIsPaymentMethodModalOpen(false)}
        paymentMethods={paymentMethods}
        onRefresh={reloadUserMembership}
      />
    </div>
  );
}
