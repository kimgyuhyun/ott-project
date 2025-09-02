"use client";
import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import Header from "@/components/layout/Header";
import { useMembershipData } from "@/hooks/useMembershipData";
import { cancelMembership, cancelScheduledPlanChange, resumeMembership } from "@/lib/api/membership";
import CancelPlanChangeModal from "@/components/membership/CancelPlanChangeModal";
import PaymentMethodChangeModal from "@/components/membership/PaymentMethodChangeModal";
import CancelMembershipModal from "@/components/membership/CancelMembershipModal";
import { useAuth } from "@/hooks/useAuth";
import styles from "./manage.module.css";

export default function MembershipManagePage() {
  const { membershipPlans, userMembership, paymentMethods, isLoading, error, reloadUserMembership } = useMembershipData();
  const { user } = useAuth();
  const [isCancelling, setIsCancelling] = useState(false);
  const [isPaymentMethodModalOpen, setIsPaymentMethodModalOpen] = useState(false);
  const [showCancelChangeModal, setShowCancelChangeModal] = useState(false);
  const [isCancellingChange, setIsCancellingChange] = useState(false);
  const [showCancelMembershipModal, setShowCancelMembershipModal] = useState(false);
  const [isResuming, setIsResuming] = useState(false);
  const [notice, setNotice] = useState<{ type: 'success' | 'error', title: string, message: string } | null>(null);

  
  // 플랜 이름 한국어 매핑
  const translatePlanName = (name?: string | null) => {
    if (!name) return '';
    const map: Record<string, string> = {
      'Basic Monthly': '베이직',
      'Premium Monthly': '프리미엄',
    };
    return map[name] || name;
  };

  // 결제수단 타입 한국어 매핑
  const translatePaymentMethodType = (type: string) => {
    const map: Record<string, string> = {
      'CARD': '카드',
      'KAKAO_PAY': '카카오페이',
      'TOSS_PAY': '토스페이',
      'NICE_PAY': '나이스페이',
      'BANK_TRANSFER': '계좌이체',
    };
    return map[type] || type;
  };

  // 결제수단 아이콘 매핑
  const getPaymentMethodIcon = (type: string) => {
    const map: Record<string, string> = {
      'CARD': '💳',
      'KAKAO_PAY': '🟡',
      'TOSS_PAY': '🔵',
      'NICE_PAY': '🟢',
      'BANK_TRANSFER': '🏦',
    };
    return map[type] || '📱';
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

  // 전환 예약 취소 처리
  const handleCancelScheduledChange = async () => {
    if (isCancellingChange) return;
    setIsCancellingChange(true);
    try {
      await cancelScheduledPlanChange();
      setShowCancelChangeModal(false);
      await reloadUserMembership();
      setNotice({ type: 'success', title: '전환 예약 취소', message: '플랜 전환 예약이 취소되었습니다.' });
    } catch (e) {
      setNotice({ type: 'error', title: '실패', message: '전환 예약 취소에 실패했습니다. 다시 시도해주세요.' });
    } finally {
      setIsCancellingChange(false);
    }
  };



  // 멤버십 해지 핸들러
  const handleCancelMembership = async () => {
    if (!userMembership || isCancelling) return;

    setIsCancelling(true);
    try {
      await cancelMembership();
      await reloadUserMembership();
      setShowCancelMembershipModal(false);
      setNotice({ type: 'success', title: '해지 완료', message: '멤버십 해지에 성공했습니다.' });
    } catch (error) {
      console.error('멤버십 해지 실패:', error);
      setNotice({ type: 'error', title: '실패', message: '멤버십 해지에 실패했습니다. 잠시 후 다시 시도해주세요.' });
    } finally {
      setIsCancelling(false);
    }
  };

  // 멤버십 정기결제 재시작 핸들러
  const handleResumeMembership = async () => {
    if (isResuming) return;
    
    setIsResuming(true);
    try {
      await resumeMembership();
      await reloadUserMembership();
      setNotice({ type: 'success', title: '정기결제 재시작', message: '멤버십 정기결제가 다시 시작되었습니다.' });
    } catch (error) {
      console.error('멤버십 재시작 실패:', error);
      setNotice({ type: 'error', title: '실패', message: '정기결제 재시작에 실패했습니다. 잠시 후 다시 시도해주세요.' });
    } finally {
      setIsResuming(false);
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

  // 멤버십이 해지된 경우 (autoRenew: false)
  if (userMembership && !userMembership.autoRenew) {
    return (
      <div className={styles.manageContainer}>
        <Header />
        <div className={styles.mainContent}>
          {notice && (
            <div className={styles.centerNoticeOverlay} onClick={() => setNotice(null)}>
              <div className={`${styles.centerNoticeBox} ${notice.type === 'success' ? styles.centerNoticeSuccess : styles.centerNoticeError}`} onClick={(e) => e.stopPropagation()}>
                <div className={styles.centerNoticeHeader}>
                  <h4 className={styles.centerNoticeTitle}>{notice.title}</h4>
                  <button className={styles.centerNoticeClose} onClick={() => setNotice(null)}>✕</button>
                </div>
                <div className={styles.centerNoticeMessage}>{notice.message}</div>
              </div>
            </div>
          )}
          {/* 페이지 헤더 */}
          <div className={styles.pageHeader}>
            <h1 className={styles.pageTitle}>내 멤버십 관리</h1>
          </div>

          {/* 해지 완료 화면 */}
          <div className={styles.cancelledContainer}>
            <h2 className={styles.cancelledTitle}>멤버십 정기결제가 해지되었습니다.</h2>
            <p className={styles.cancelledDesc}>만료 예정일까지 이용이 가능합니다.</p>
            
            <button 
              className={styles.restartButton}
              onClick={handleResumeMembership}
              disabled={isResuming}
            >
              {isResuming ? '처리 중...' : '멤버십 정기결제 다시 시작'}
            </button>
            
            <div className={styles.expirationInfo}>
              <p className={styles.expirationText}>
                멤버십 만료 예정일 : {(() => {
                  const endDate = userMembership.endAt || userMembership.nextBillingAt;
                  if (endDate) {
                    return new Date(endDate).toLocaleDateString('ko-KR', {
                      year: 'numeric',
                      month: '2-digit', 
                      day: '2-digit'
                    }).replace(/\. /g, '.').replace('.', '');
                  }
                  return '정보 없음';
                })()}
              </p>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.manageContainer}>
      <Header />
      <div className={styles.mainContent}>
        {notice && (
          <div className={styles.centerNoticeOverlay} onClick={() => setNotice(null)}>
            <div className={`${styles.centerNoticeBox} ${notice.type === 'success' ? styles.centerNoticeSuccess : styles.centerNoticeError}`} onClick={(e) => e.stopPropagation()}>
              <div className={styles.centerNoticeHeader}>
                <h4 className={styles.centerNoticeTitle}>{notice.title}</h4>
                <button className={styles.centerNoticeClose} onClick={() => setNotice(null)}>✕</button>
              </div>
              <div className={styles.centerNoticeMessage}>{notice.message}</div>
            </div>
          </div>
        )}
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
              <h4 className={styles.planName}>
                {userMembership.nextPlanName 
                  ? translatePlanName(userMembership.nextPlanName)
                  : translatePlanName(planForUser?.name) || userMembership.planName
                }
              </h4>
              <Link href="/membership" className={styles.changePaymentMethodButton}>멤버십 변경</Link>
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
                      {getPaymentMethodIcon(paymentMethods[0].type)}
                    </span>
                  </div>
                  <span className={styles.paymentMethodText}>
                    {paymentMethods[0].type === 'CARD' 
                      ? `${paymentMethods[0].brand || '카드'} ${paymentMethods[0].last4 ? `****${paymentMethods[0].last4}` : ''}`
                      : translatePaymentMethodType(paymentMethods[0].type)
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
              {nextBillingDate ? (
                userMembership.nextPlanName ? (
                  <>
                    {nextBillingDate} {translatePlanName(userMembership.nextPlanName)} 결제 예정
                  </>
                ) : (
                  nextBillingDate
                )
              ) : (
                '정보 없음'
              )}
            </div>
          </div>


        </div>



        {/* 멤버십 해지 버튼 */}
        <div className={styles.cancelSection}>
          <button 
            className={styles.cancelMembershipButton}
            onClick={() => setShowCancelMembershipModal(true)}
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

      <CancelPlanChangeModal
        isOpen={showCancelChangeModal}
        nextPlanName={translatePlanName(userMembership.nextPlanName)}
        onCancel={() => setShowCancelChangeModal(false)}
        onConfirm={handleCancelScheduledChange}
        isProcessing={isCancellingChange}
      />

      <CancelMembershipModal
        isOpen={showCancelMembershipModal}
        onClose={() => setShowCancelMembershipModal(false)}
        onConfirm={handleCancelMembership}
        planName={translatePlanName(userMembership?.planName || planForUser?.name)}
        primaryProfileName={user?.username}
      />

    </div>
  );
}