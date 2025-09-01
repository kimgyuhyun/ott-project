"use client";
import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import Header from "@/components/layout/Header";
import { useMembershipData } from "@/hooks/useMembershipData";
import styles from "./guide.module.css";

export default function MembershipGuidePage() {
  const { membershipPlans, userMembership, isLoading, error, reloadUserMembership } = useMembershipData();
  
  // 확장된 플랜 (화살표로 접었다 펼쳤다)
  const [expandedPlan, setExpandedPlan] = useState<string | null>(null);

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
      return `${y}.${m}.${day} 결제 예정`;
    } catch {
      return null;
    }
  }, [userMembership]);

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

  // 멤버십 변경 처리
  const handlePlanChange = async (plan: any) => {
    if (!plan) return;
    
    try {
      // 여기에 실제 플랜 변경 API 호출 로직 추가
      alert(`${translatePlanName(plan.name)} 플랜으로 변경하시겠습니까?`);
      // TODO: 실제 플랜 변경 API 구현
    } catch (error) {
      console.error('플랜 변경 실패:', error);
    }
  };

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
          <span className={styles.paymentDate}>{nextPaymentText}</span>
        )}
        <Link href="/membership/manage" className={styles.membershipManageLink}>내 멤버십 관리 &gt;</Link>
      </div>
    </div>
  );

  return (
    <div className={styles.guideContainer}>
      <Header />
      <main className="relative pt-16">
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

          {/* 요약 카드 or 로그인 유도 */}
          {!isLoading && userMembership ? (
            summaryCard
          ) : (
            <div className={styles.loginCard}>
              <p className={styles.loginCardText}>로그인 후 멤버십 상태를 확인할 수 있습니다.</p>
              <Link href="/login" className={styles.loginButton}>로그인하고 멤버십 확인</Link>
            </div>
          )}

          {/* 다른 멤버십 */}
          <div className={styles.otherMembershipSection}>
            <h3 className={styles.otherMembershipTitle}>다른 멤버십</h3>
            {(membershipPlans.filter(p => {
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
                    
                    <button 
                      onClick={(e) => {
                        e.stopPropagation(); // 카드 클릭 이벤트 전파 방지
                        handlePlanChange(p);
                      }}
                      className={styles.changePlanButton}
                    >
                      멤버십 변경하기
                    </button>
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
                <p>• 멤버십 결제 후 디지털 콘텐츠를 하나도 다운로드하지 않았고(다운로드 시작 포함), 스트리밍 서비스를 통해 전혀 재생하지 않은 경우에 한해, 결제일로부터 7일 이내 라프텔 고객센터에 요청하시면 환불 가능합니다. 단, 인앱 결제 또는 외부 제휴처를 통해 구독하신 경우, Google Play, App Store, LG U+등 제휴사 고객센터를 통해 환불 요청해 주시기 바랍니다.</p>
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
    </div>
  );
}


