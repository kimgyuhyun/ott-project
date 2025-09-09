"use client";
import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import Header from "@/components/layout/Header";
import { useMembershipData } from "@/hooks/useMembershipData";
import { changeMembershipPlan, resumeMembership } from "@/lib/api/membership";
import PlanChangeModal from "@/components/membership/PlanChangeModal";
import PaymentModal from "@/components/membership/PaymentModal";
import ProrationPaymentModal from "@/components/membership/ProrationPaymentModal";
import styles from "./guide.module.css";

export default function MembershipGuidePage() {
  const { membershipPlans, userMembership, paymentHistory, isLoading, error, reloadUserMembership } = useMembershipData();
  
  // 확장된 플랜 (화살표로 접었다 펼쳤다)
  const [expandedPlan, setExpandedPlan] = useState<string | null>(null);
  
  // 멤버십 변경 모달 상태
  const [isChangeModalOpen, setIsChangeModalOpen] = useState(false);
  const [selectedPlan, setSelectedPlan] = useState<any>(null);
  
  // 결제 모달 상태
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [showProrationPaymentModal, setShowProrationPaymentModal] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState('simple');
  const [selectedPaymentService, setSelectedPaymentService] = useState('');
  
  // 멤버십 재시작 상태
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

  // 결제 성공 파라미터 배너 (1회 노출)
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  useEffect(() => {
    const url = new URL(window.location.href);
    const paymentId = url.searchParams.get('paymentId');
    const status = url.searchParams.get('status');
    const shownKey = 'membership_guide_success_shown';
    if (paymentId && status === 'success' && !sessionStorage.getItem(shownKey)) {
      setSuccessMsg('결제가 완료되었습니다. 멤버십이 즉시 적용되었습니다.');
      sessionStorage.setItem(shownKey, '1');
      reloadUserMembership();
    }
  }, [reloadUserMembership]);

  const planForUser = useMemo(() => {
    if (!userMembership) return null;
    const matched = membershipPlans.find(p => {
      const byCode = (p as any).code && userMembership.planCode && String((p as any).code).toUpperCase() === String(userMembership.planCode).toUpperCase();
      const byName = p.name && userMembership.planName && String(p.name).toLowerCase() === String(userMembership.planName).toLowerCase();
      return byCode || byName;
    });
    return matched || null;
  }, [membershipPlans, userMembership]);

  const nextPaymentText = useMemo(() => {
    if (!userMembership?.nextBillingAt) return null;
    try {
      const d = new Date(userMembership.nextBillingAt);
      const y = d.getFullYear();
      const m = String(d.getMonth() + 1).padStart(2, '0');
      const day = String(d.getDate()).padStart(2, '0');
      const base = `${y}.${m}.${day}`;
      
      // autoRenew가 false면 "만료 예정", true면 "결제 예정"
      const suffix = userMembership.autoRenew ? '결제 예정' : '만료 예정';
      
      // 다음 전환 플랜이 예약되어 있으면 플랜명을 함께 표기
      if (userMembership.nextPlanCode) {
        const nextPlan = membershipPlans.find(p => (p as any).code && String((p as any).code).toUpperCase() === String(userMembership.nextPlanCode).toUpperCase());
        const nextPlanName = translatePlanName(nextPlan?.name);
        if (nextPlanName) {
          return `${base} ${nextPlanName} ${suffix}`;
        }
      }
      return `${base} ${suffix}`;
    } catch {
      return null;
    }
  }, [membershipPlans, userMembership]);


  // 플랜 기능 리스트 생성 (DB 데이터 기반)
  const getPlanFeatures = (plan: any) => {
    if (!plan) return [];
    
    return [
      `프로필 ${plan.concurrentStreams}인·동시재생 ${plan.concurrentStreams}회선`,
      '최신화 시청',
      '다운로드 지원',
      `${plan.maxQuality} 화질 지원`,
      'TV 앱 지원'
    ];
  };

  // 차액 계산 (간단한 예시)
  const calculateProrationAmount = (currentPlan: any, targetPlan: any) => {
    if (!currentPlan || !targetPlan) return 0;
    const priceDifference = targetPlan.monthlyPrice - currentPlan.monthlyPrice;
    // 남은 기간에 대한 차액 계산 (간단히 월 가격의 50%로 가정)
    return Math.floor(priceDifference * 0.5);
  };

  // 멤버십 변경 처리
  const handlePlanChange = (plan: any) => {
    if (!plan) return;
    setSelectedPlan(plan);
    setIsChangeModalOpen(true);
  };

  // 업그레이드 시 차액 결제 모달 열기
  const handleUpgradePayment = (plan: any) => {
    setSelectedPlan(plan);
    setIsChangeModalOpen(false);
    setShowProrationPaymentModal(true);
  };

  // 차액 결제 완료 후 처리
  const handleProrationPaymentSuccess = async () => {
    setShowProrationPaymentModal(false);
    reloadUserMembership();
    alert('플랜 업그레이드가 완료되었습니다.');
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

  // 환불 상태 확인
  const isRefunded = useMemo(() => {
    if (!paymentHistory || paymentHistory.length === 0) return false;
    // 최근 결제가 환불된 상태인지 확인
    const latestPayment = paymentHistory[0];
    return latestPayment.status === 'REFUNDED';
  }, [paymentHistory]);

  // 환불 + 해지 상태 확인
  const isRefundedAndCancelled = useMemo(() => {
    return isRefunded && userMembership?.status === 'CANCELED';
  }, [isRefunded, userMembership?.status]);





  const summaryCard = (
    <div className={styles.summaryCard}>
      <div className={styles.summaryCardHeader}>
        <div>
          <h3 className={styles.summaryCardTitle}>{translatePlanName(planForUser?.name) || '멤버십'}</h3>
          <p className={styles.summaryCardPrice}>
            {planForUser ? `월 ${planForUser.monthlyPrice.toLocaleString()}원` : ''}
          </p>
        </div>
        {userMembership?.status === 'ACTIVE' && (
          <span className={styles.currentBadge}>현재 이용 중 ✓</span>
        )}
      </div>

      <div className={styles.summaryCardFeatures}>
        <ul className="space-y-2">
          {getPlanFeatures(planForUser).map((feature, index) => (
            <li key={index} className={styles.summaryCardFeature}>{feature}</li>
          ))}
        </ul>
      </div>

      <div className={styles.summaryCardFooter}>
        {nextPaymentText && (
          <div className={styles.paymentDateContainer}>
            <div className={styles.paymentDateRow}>
              <span className={styles.paymentDate}>{nextPaymentText}</span>
              <Link href="/membership/manage" className={styles.membershipManageLink}>내 멤버십 관리 &gt;</Link>
            </div>
            {userMembership && !userMembership.autoRenew && (
              <>
                <div className={styles.paymentDateDivider}></div>
                <button 
                  className={styles.restartButton}
                  onClick={handleResumeMembership}
                  disabled={isResuming}
                >
                  {isResuming ? '처리 중...' : '멤버십 다시 시작하기'}
                </button>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );

  return (
    <div className={styles.guideContainer}>
      <Header />
      <main className="relative pt-16">
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
        <div className={styles.mainContent}>
          {/* 히어로 */}
          <div className={styles.heroSection}>
            {/* 배경 이미지 */}
            <div 
              className={styles.heroBackground}
              style={{
                backgroundImage: 'url("data:image/svg+xml,%3Csvg xmlns=\'http://www.w3.org/2000/svg\' viewBox=\'0 0 1200 400\'%3E%3Cdefs%3E%3ClinearGradient id=\'a\' x1=\'0%25\' y1=\'0%25\' x2=\'100%25\' y2=\'100%25\'%3E%3Cstop offset=\'0%25\' stop-color=\'%23000\' stop-opacity=\'0.7\'/%3E%3Cstop offset=\'100%25\' stop-color=\'%23000\' stop-opacity=\'0.9\'/%3E%3C/linearGradient%3E%3C/defs%3E%3Crect width=\'1200\' height=\'400\' fill=\'url(%23a)\'/%3E%3C/svg%3E")'
              }}
            />
            {/* 텍스트 오버레이 */}
            <div className={styles.heroTextOverlay}>
              <h1 className={styles.heroTitle}>라프텔 멤버십으로 스마트한 덕질!</h1>
              <p className={styles.heroDescription}>동시방영 신작부터 역대 인기 애니까지 멤버십으로 최애 애니를 마음껏 감상하세요</p>
            </div>
          </div>

          {/* 성공 배너 */}
          {successMsg && (
            <div className={styles.successBanner}>{successMsg}</div>
          )}

          {/* 오류 배너 */}
          {error && (
            <div className={styles.errorBanner}>
              멤버십 정보를 불러오지 못했습니다.
              <button onClick={() => window.location.reload()} className={styles.retryButton}>재시도</button>
            </div>
          )}

          {/* 환불 완료 안내 */}
          {!isLoading && isRefundedAndCancelled && (
            <div className={styles.refundNotice}>
              <div className={styles.refundNoticeIcon}>💰</div>
              <div className={styles.refundNoticeContent}>
                <h3 className={styles.refundNoticeTitle}>환불이 완료되었습니다</h3>
                <p className={styles.refundNoticeText}>
                  멤버십 결제가 환불되었고, 멤버십이 해지되었습니다. 
                  새로운 멤버십을 구독하시려면 아래 플랜을 선택해주세요.
                </p>
              </div>
            </div>
          )}

          {/* 요약 카드 or 로그인 유도 */}
          {!isLoading && userMembership && !isRefundedAndCancelled ? (
            summaryCard
          ) : !isLoading && !isRefundedAndCancelled ? (
            <div className={styles.loginCard}>
              <p className={styles.loginCardText}>로그인 후 멤버십 상태를 확인할 수 있습니다.</p>
              <Link href="/login" className={styles.loginButton}>로그인하고 멤버십 확인</Link>
            </div>
          ) : null}

          {/* 다른 멤버십 */}
          <div className={styles.otherMembershipSection}>
            <h3 className={styles.otherMembershipTitle}>
              {isRefundedAndCancelled ? '멤버십 플랜' : '다른 멤버십'}
            </h3>
            {(membershipPlans.filter(p => {
              // 환불 완료 상태일 때는 모든 플랜 표시
              if (isRefundedAndCancelled) return true;
              
              if (!userMembership) return true;
              const byCodeDifferent = (p as any).code && userMembership.planCode && String((p as any).code).toUpperCase() !== String(userMembership.planCode).toUpperCase();
              const byNameDifferent = p.name && userMembership.planName && String(p.name).toLowerCase() !== String(userMembership.planName).toLowerCase();
              // If code is present, use it; otherwise fallback to name comparison
              return (p as any).code ? byCodeDifferent : byNameDifferent;
            })).map(p => (
              <div 
                key={(p as any).code || p.name} 
                className={styles.otherMembershipCard}
                onClick={() => setExpandedPlan(expandedPlan === p.name ? null : p.name)}
              >
                {/* 카드 헤더 (항상 보임) */}
                <div className={styles.otherMembershipCardContent}>
                  <div>
                    <h3 className={styles.otherMembershipName}>{translatePlanName(p.name)}</h3>
                  </div>
                  <svg 
                    className={`${styles.arrowIcon} ${expandedPlan === p.name ? styles.arrowIconExpanded : ''}`} 
                    fill="none" 
                    stroke="currentColor" 
                    viewBox="0 0 24 24"
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                  </svg>
                </div>
                
                {/* 카드 확장 내용 (클릭 시 보임) */}
                {expandedPlan === p.name && (
                  <div className={styles.cardExpandedContent}>
                    <p className={styles.expandedPlanPrice}>월 {p.monthlyPrice.toLocaleString()}원</p>
                    
                    <div className={styles.expandedPlanFeatures}>
                      <ul className="space-y-2">
                        {getPlanFeatures(p).map((feature, index) => (
                          <li key={index} className={styles.expandedPlanFeature}>{feature}</li>
                        ))}
                      </ul>
                    </div>
                    
                    {isRefundedAndCancelled ? (
                      <button 
                        onClick={(e) => {
                          e.stopPropagation(); // 카드 클릭 이벤트 전파 방지
                          window.location.href = '/membership';
                        }}
                        className={styles.changePlanButton}
                      >
                        멤버십 구독하기
                      </button>
                    ) : userMembership?.nextPlanCode && (p as any).code && String((p as any).code).toUpperCase() === String(userMembership.nextPlanCode).toUpperCase() ? (
                      <button 
                        onClick={(e) => { e.stopPropagation(); }}
                        className={styles.changePlanButton}
                        disabled
                      >
                        전환 예약됨
                      </button>
                    ) : (
                      <button 
                        onClick={(e) => {
                          e.stopPropagation(); // 카드 클릭 이벤트 전파 방지
                          handlePlanChange(p);
                        }}
                        className={styles.changePlanButton}
                      >
                        멤버십 변경하기
                      </button>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>

          {/* 멤버십 유의사항 */}
          <div className={styles.membershipNotice}>
            <h2 className={styles.noticeTitle}>멤버십 유의사항</h2>
            
            {/* 멤버십 구독 및 결제 안내 */}
            <div className={styles.noticeSection}>
              <h3 className={styles.noticeSectionTitle}>멤버십 구독 및 결제 안내</h3>
              <div className={styles.noticeContent}>
                <p>• 결제 금액은 부가가치세(VAT)가 포함된 가격입니다.</p>
                <p>• 멤버십은 월정액 유료 이용권으로, 결제 즉시 적용되며 이용이 시작됩니다.</p>
                <p>• 매월 정기 결제일에 등록한 결제 수단을 통해 자동으로 결제됩니다.</p>
                <p>• 쿠폰, 분분, 이벤트 등 무료 혜택과 중복 사용은 불가합니다. 무료 이용 중 멤버십을 구매할 경우 남은 무료 이용 기간은 즉시 종료되며 복원되지 않습니다.</p>
                <p>• 미성년자 회원은 법정대리인의 명의 또는 동의를 통해 결제해야 하며, 동의 없이 결제된 경우 법정대리인이 이를 취소할 수 있습니다.</p>
                <p>• 멤버십은 언제든지 해지할 수 있으며, 해지 후에도 남은 이용 기간까지는 서비스를 이용하실 수 있습니다.</p>
                <p>• 멤버십 해지는 결제 예정일 최소 24시간 이전에 신청해야 합니다. 결제 예정일 기준 24시간 이내에는 멤버십 해지하더라도 다음 결제가 진행될 수 있습니다.</p>
                <p>• 통신사 또는 카드 정보 변경, 잔액 부족 등의 사유로 인해 결제가 실패할 경우, 멤버십 정기 결제가 자동으로 해지될 수 있습니다.</p>
                <p>• 결제 당일을 제외하고는 결제 수단은 언제든지 변경할 수 있으며, 변경된 결제 수단은 다음 정기 결제일부터 적용됩니다. (단, 휴대폰 결제로는 변경이 불가합니다.)</p>
                <p>• 인앱 결제 또는 외부 제휴처를 통해 구독한 멤버십을 보유한 경우, 라프텔 웹 결제로 즉시 변경은 불가하며 기존 멤버십을 해지한 뒤 이용 기간이 종료된 후 새로운 멤버십으로 변경할 수 있습니다.</p>
                <p>• 멤버십 결제 후 서비스를 전혀 이용하지 않은 경우에 한해, 결제일로부터 7일 이내에 환불이 가능합니다. 단, 인앱 결제 또는 외부 제휴처를 통해 구독하신 경우, Google Play, App Store, LG U+등 제휴사 고객센터를 통해 환불 요청해 주시기 바랍니다.</p>
                <p>• 멤버십 이용 중에는 남은 기간에 대한 금액 환불이 불가합니다.</p>
              </div>
            </div>

            {/* 콘텐츠 이용 안내 */}
            <div className={styles.noticeSection}>
              <h3 className={styles.noticeSectionTitle}>콘텐츠 이용 안내</h3>
              <div className={styles.noticeContent}>
                <p>• 라프텔에서 제공되는 모든 콘텐츠는 대한민국 내에서만 이용 가능하며, 그 외 국가에서는 이용 불가합니다.</p>
                <p>• 일부 콘텐츠는 콘텐츠 제공사 또는 저작권자의 요청 등에 따라 별도 사전 고지 없이 서비스가 중단될 수 있습니다.</p>
                <p>• 콘텐츠 제공사 또는 저작권자의 요청에 따라 멤버십에서 제외되는 콘텐츠가 있을 수 있습니다.</p>
                <p>• 콘텐츠별 영상 화질, 음성 및 음향 방식, 언어 제공 등은 상이하며, 모든 콘텐츠가 동일한 사양으로 제공되지는 않습니다.</p>
                <p>• 멤버십에는 청소년 관람 불가 콘텐츠가 포함되어 있으며, 본인 확인 및 성인 인증된 회원만 청소년 관람 불가 콘텐츠 이용이 가능합니다.</p>
                <p>• 멤버십 종류에 따라 동시 시청 가능 기기 수가 다르며, 이를 초과하는 즉시 시청이 제한됩니다.</p>
                <p>• 다운로드한 콘텐츠는 일정 기간 동안만 시청 가능하며, 멤버십이 만료되면 더 이상 이용할 수 없습니다.</p>
                <p>• 일부 콘텐츠는 콘텐츠 제공사 또는 저작권자의 요청에 따라 스트리밍으로만 제공되며, 다운로드 이용이 불가할 수 있습니다.</p>
              </div>
            </div>

            {/* 재생 및 이용 환경 안내 */}
            <div className={styles.noticeSection}>
              <h3 className={styles.noticeSectionTitle}>재생 및 이용 환경 안내</h3>
              <div className={styles.noticeContent}>
                <p>• 지원 기기 및 플랫폼은 [이곳]에서 확인하실 수 있으며, 지원되지 않는 환경에서는 일부 기능이 제한되거나 재생이 원활하지 않을 수 있습니다.</p>
                <p>• 해외 직구 등으로 국내 정식 출시되지 않은 기기에서는 호환성 문제로 인해 서비스 이용이 제한될 수 있습니다.</p>
                <p>• 지원하는 스마트폰, 태블릿, TV에서는 라프텔 공식 앱을 설치 후 이용 가능합니다.</p>
                <p>• 모바일 데이터(셀룰러) 환경에서는 데이터 요금이 과도하게 발생할 수 있으므로 Wi-Fi 이용을 권장합니다.</p>
                <p>• 사용하는 인터넷 환경에 따라 재생이 끊기거나 지연될 수 있으며, 원활한 시청을 위해 안정적인 고속 인터넷 환경이 필요합니다.</p>
                <p>• 모든 콘텐츠는 저작권자의 라이선스를 통해 제공되며, 디지털 저작권 관리(DRM) 기술로 보호됩니다.</p>
                <p>• 콘텐츠 재생은 DRM을 지원하는 환경에서만 가능합니다. 루팅되거나 탈옥된 기기, 시스템 설정이 변경된 기기에서는 재생이 제한됩니다.</p>
                <p>• 콘텐츠 보호 정책에 따라 Google Widevine L1 이상, Apple Fairplay 등 고급 보안 인증 및 HDCP 2.2 이상이 지원되지 않는 기기에서는 재생이 불가할 수 있습니다.</p>
                <p>• OS 버전, 기기 사양, 제조사에 따라 서비스가 정상 작동하지 않을 수 있습니다.</p>
                <p>• 영상 화질은 사용하는 인터넷 환경, 디바이스의 성능 등에 따라 달라질 수 있습니다. 모든 콘텐츠가 모든 화질로 동일하게 제공되지는 않습니다.</p>
                <p>• 콘텐츠 다운로드는 모바일/태블릿 앱에서만 가능하며, 일부 콘텐츠는 콘텐츠 제공사 또는 저작권자의 요청에 따라 다운로드 시청이 제한될 수 있습니다.</p>
                <p>• 콘텐츠 제공사 또는 저작권자의 요청으로 특정 기기에서는 일부 콘텐츠 시청이 제한될 수 있습니다.</p>
              </div>
            </div>

            {/* 기타 안내 */}
            <div className={styles.noticeSection}>
              <h3 className={styles.noticeSectionTitle}>기타 안내</h3>
              <div className={styles.noticeContent}>
                <p>• 기타 궁금하신 점은 고객센터를 통해 1:1 문의해 주시기 바랍니다.</p>
              </div>
            </div>
          </div>
        </div>
      </main>

      {/* 멤버십 변경 확인 모달 */}
      <PlanChangeModal
        isOpen={isChangeModalOpen}
        onClose={() => setIsChangeModalOpen(false)}
        currentPlan={planForUser}
        targetPlan={selectedPlan}
        userMembership={userMembership}
        onPlanChanged={reloadUserMembership}
        onUpgradePayment={handleUpgradePayment}
      />

      {/* 차액 결제 모달 - 업그레이드 시 표시 */}
      {showProrationPaymentModal && selectedPlan && planForUser && (
        <ProrationPaymentModal
          isOpen={showProrationPaymentModal}
          onClose={() => setShowProrationPaymentModal(false)}
          planInfo={{
            name: `${translatePlanName(selectedPlan.name)} 업그레이드`,
            price: calculateProrationAmount(planForUser, selectedPlan).toLocaleString(),
            features: [
              `프로필 ${selectedPlan.concurrentStreams}인·동시재생 ${selectedPlan.concurrentStreams}회선`,
              '최신화 시청',
              '다운로드 지원',
              `${selectedPlan.maxQuality} 화질 지원`,
              'TV 앱 지원'
            ],
            code: selectedPlan.code
          }}
          paymentMethod={paymentMethod}
          onChangePaymentMethod={setPaymentMethod}
          selectedPaymentService={selectedPaymentService}
          onSelectPaymentService={setSelectedPaymentService}
          onOpenCardRegistration={() => {}}
          onPay={handleProrationPaymentSuccess}
        />
      )}
    </div>
  );
}


