"use client";
import { useState, useEffect } from "react";
import Header from "@/components/layout/Header";
import { useAuth } from "@/lib/AuthContext";
import { subscribeMembership, registerPaymentMethod } from "@/lib/api/membership";
import { useMembershipData } from "@/hooks/useMembershipData";
import { useCheckout } from "@/hooks/useCheckout";
import PaymentMethodItem from "@/components/membership/PaymentMethodItem";
import PaymentModal from "@/components/membership/PaymentModal";
import PaymentFailureModal from "@/components/membership/PaymentFailureModal";
import CardRegistrationModal from "@/components/membership/CardRegistrationModal";
import styles from "./membership.module.css";

/**
 * 멤버십 페이지
 * 어두운 테마의 애니메이션 스트리밍 서비스 멤버십 페이지
 */
export default function MembershipPage() {
  const { isAuthenticated, user, isInitialized } = useAuth();
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [selectedPlan, setSelectedPlan] = useState<string>('basic');
  const [hoveredPlan, setHoveredPlan] = useState<string | null>(null);
  const [isPaymentModalOpen, setIsPaymentModalOpen] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<string>('simple');
  const [selectedPaymentService, setSelectedPaymentService] = useState<string>('toss');
  const [isCardRegistrationModalOpen, setIsCardRegistrationModalOpen] = useState(false);
  const [isPaymentFailureOpen, setIsPaymentFailureOpen] = useState(false);
  const [paymentFailureMsg, setPaymentFailureMsg] = useState<string>('결제 실패');
  
  // 멤버십 페이지는 항상 다크 모드 (라프텔 방식)
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', 'dark');
  }, []);
  
  // 데이터 훅 사용
  const { membershipPlans, userMembership, paymentMethods, isLoading, error, reloadPaymentMethods, reloadUserMembership } = useMembershipData();
  const { requestPay } = useCheckout();

  // 멤버십 플랜 선택
  const handlePlanSelect = (plan: string) => {
    setSelectedPlan(plan);
  };

  // 모달 열기/닫기
  const openModal = () => {
    console.log('멤버십 모달 열기 시도');
    console.log('isAuthenticated:', isAuthenticated);
    console.log('user:', user);
    setIsModalOpen(true);
  };
  const closeModal = () => setIsModalOpen(false);
  const openPaymentModal = () => {
    // 비로그인 상태이거나 user 객체가 없으면 결제 단계로 가지 않고 로그인 페이지로 리다이렉트
    if (!isAuthenticated || !user) {
      console.log('결제 모달 접근 차단: 로그인 필요 또는 user 객체 없음');
      window.location.href = '/login';
      return;
    }
    setIsPaymentModalOpen(true);
    closeModal();
  };
  const closePaymentModal = () => setIsPaymentModalOpen(false);
  const openCardRegistrationModal = () => setIsCardRegistrationModalOpen(true);
  const closeCardRegistrationModal = () => setIsCardRegistrationModalOpen(false);

  // 결제 수단 선택
  const handlePaymentMethodChange = (method: string) => {
    setPaymentMethod(method);
  };

  // 결제 서비스 선택
  const handlePaymentServiceSelect = (service: string) => {
    setSelectedPaymentService(service);
  };

  // 카드 등록
  const handleCardRegistration = async (cardData: any) => {
    try {
      await registerPaymentMethod({
        type: 'CARD',
        cardNumber: cardData.cardNumber,
        expiryMonth: cardData.expiryMonth,
        expiryYear: cardData.expiryYear,
        birthDate: cardData.birthDate,
        password: cardData.password
      });
      
      alert('카드가 등록되었습니다!');
      closeCardRegistrationModal();
      // 결제수단 목록 새로고침
      reloadPaymentMethods();
    } catch (err) {
      alert('카드 등록에 실패했습니다.');
      console.error('카드 등록 오류:', err);
    }
  };

  // 카드 등록 처리
  const handleCardRegistrationSubmit = () => {
    // 실제 구현에서는 입력 필드의 값을 수집해야 합니다
    // 현재는 더미 데이터로 처리
    const cardData = {
      cardNumber: '1234567890123456',
      expiryMonth: 12,
      expiryYear: 25,
      birthDate: '901231',
      password: '12'
    };
    
    handleCardRegistration(cardData);
  };

  // 멤버십 구독 시작
  const handleSubscribe = async () => {
    try {
      const result = await subscribeMembership(selectedPlan);
      alert(`${selectedPlan === 'basic' ? '베이직' : '프리미엄'} 멤버십을 시작합니다!`);
      closeModal();
      // 사용자 멤버십 상태 새로고침
      reloadUserMembership();
    } catch (err) {
      alert('멤버십 구독에 실패했습니다.');
      console.error('구독 오류:', err);
    }
  };

  // 결제 진행 (SDK 호출)
  const handlePayment = async () => {
    try {
      const planCode = selectedPlan === 'basic' ? 'BASIC_MONTHLY' : 'PREMIUM_MONTHLY';
      await requestPay(planCode, selectedPaymentService);
    } catch (err) {
      const msg = (err as Error)?.message || '결제 실패';
      setPaymentFailureMsg(msg);
      setIsPaymentFailureOpen(true);
      console.error('결제 오류:', err);
    }
  };

  // 플랜 정보 가져오기
  const getPlanInfo = () => {
    const plan = membershipPlans.find(p => p.code === selectedPlan);
    if (plan) {
      return {
        name: plan.name,
        price: plan.monthlyPrice.toLocaleString(),
        features: [
          `프로필 ${plan.maxConcurrentStreams}인 · 동시재생 ${plan.maxConcurrentStreams}회선`,
          '최신화 시청',
          '다운로드 지원',
          `${plan.quality} 화질 지원`,
          'TV 앱 지원'
        ]
      };
    }
    
    // 기본값 (API 데이터가 없을 때)
    if (selectedPlan === 'basic') {
      return {
        name: '베이직 멤버십',
        price: '9,900',
        features: ['프로필 1인 · 동시재생 1회선', '최신화 시청', '다운로드 지원', 'FHD 화질 지원', 'TV 앱 지원']
      };
    } else {
      return {
        name: '프리미엄 멤버십',
        price: '14,900',
        features: ['프로필 4인 · 동시재생 4회선', '최신화 시청', '다운로드 지원', 'FHD 화질 지원', 'TV 앱 지원']
      };
    }
  };

  // useMembershipData 훅에서 처리하므로 제거

  const planInfo = getPlanInfo();

  // 로딩 중일 때
  if (isLoading) {
    return (
      <div className={styles.membershipContainer}>
        <Header />
        <main className="relative pt-16">
          <div className={styles.loadingContainer}>
            <div className="text-center">
              <div className={styles.loadingSpinner}></div>
              <p className={styles.loadingText}>로딩 중...</p>
            </div>
          </div>
        </main>
      </div>
    );
  }

  // 에러가 있을 때
  if (error) {
    return (
      <div className={styles.membershipContainer}>
        <Header />
        <main className="relative pt-16">
          <div className={styles.errorContainer}>
            <div className="text-center">
              <p className={styles.errorText}>{error}</p>
              <button 
                onClick={() => window.location.reload()} 
                className={styles.retryButton}
              >
                다시 시도
              </button>
            </div>
          </div>
        </main>
      </div>
    );
  }

  return (
    <div className={styles.membershipContainer}>
      <Header />
      
      {/* 메인 콘텐츠 영역 */}
      <main className="relative pt-16">
        {/* 히어로 섹션 */}
        <div className={styles.heroSection}>
          {/* 텍스트 콘텐츠 */}
          <div className={styles.textContent}>
            <h1 className={styles.mainTitle}>
              동시방영 신작부터
            </h1>
            <h2 className={styles.subTitle}>
              역대 인기작까지 한 곳에서
            </h2>
            
            {/* 멤버십 시작 버튼 */}
            <button
              onClick={openModal}
              className={styles.startButton}
            >
              <span className={styles.startButtonText}>멤버십 시작하기</span>
            </button>
            
            {/* 로그인 안내 메시지 제거 */}
          </div>
        </div>

        {/* 멤버십 설명 구간 */}
        <section className={styles.descriptionSection}>
          <div className={styles.descriptionContainer}>
            <h2 className={styles.descriptionTitle}>
              나에게 맞는 멤버십을 확인하세요
            </h2>
            <p className={styles.descriptionText}>
              멤버십은 언제든 해지가 가능해요.
            </p>
            
            {/* 멤버십 플랜 카드 */}
            <div className={styles.plansGrid}>
              {/* 베이직 플랜 */}
              <div className={styles.planCard}>
                <h3 className={styles.planTitle}>베이직</h3>
                <p className={styles.planPrice}>월 9,900원</p>
                <ul className={styles.planFeatures}>
                  <li className={styles.planFeature}>
                    <span className={styles.featureCheck}>✓</span>
                    프로필 1인·동시재생 1회선
                  </li>
                  <li className={styles.planFeature}>
                    <span className={styles.featureCheck}>✓</span>
                    최신화 시청
                  </li>
                  <li className={styles.planFeature}>
                    <span className={styles.featureCheck}>✓</span>
                    다운로드 지원
                  </li>
                  <li className={styles.planFeature}>
                    <span className={styles.featureCheck}>✓</span>
                    FHD 화질 지원
                  </li>
                  <li className={styles.planFeature}>
                    <span className={styles.featureCheck}>✓</span>
                    TV 앱 지원
                  </li>
                </ul>
              </div>
              
              {/* 프리미엄 플랜 */}
              <div className={styles.planCard}>
                <h3 className={styles.planTitle}>프리미엄</h3>
                <p className={styles.planPrice}>월 14,900원</p>
                <ul className={styles.planFeatures}>
                  <li className={styles.planFeature}>
                    <span className={styles.featureCheck}>✓</span>
                    프로필 4인·동시재생 4회선
                  </li>
                  <li className={styles.planFeature}>
                    <span className={styles.featureCheck}>✓</span>
                    최신화 시청
                  </li>
                  <li className={styles.planFeature}>
                    <span className={styles.featureCheck}>✓</span>
                    다운로드 지원
                  </li>
                  <li className={styles.planFeature}>
                    <span className={styles.featureCheck}>✓</span>
                    FHD 화질 지원
                  </li>
                  <li className={styles.planFeature}>
                    <span className={styles.featureCheck}>✓</span>
                    TV 앱 지원
                  </li>
                </ul>
              </div>
            </div>
            
            {/* 멤버십 유의사항 */}
            <div className={styles.noticeSection}>
              <h2 className={styles.noticeTitle}>
                멤버십 유의사항
              </h2>
              
              {/* 멤버십 구독 및 결제 안내 */}
              <div className={styles.noticeBox}>
                <h3 className={styles.noticeBoxTitle}>
                  멤버십 구독 및 결제 안내
                </h3>
                <div className={styles.noticeBoxContent}>
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
              <div className={styles.noticeBox}>
                <h3 className={styles.noticeBoxTitle}>
                  콘텐츠 이용 안내
                </h3>
                <div className={styles.noticeBoxContent}>
                  <p>• 라프텔에서 제공되는 모든 콘텐츠는 대한민국 내에서만 이용 가능하며, 그 외 국가에서는 이용 불가합니다.</p>
                  <p>• 일부 콘텐츠는 콘텐츠 제공사 또는 저작권자의 요청 등에 따라 별도 사전 고지 없이 서비스가 중단될 수 있습니다.</p>
                  <p>• 콘텐츠 제공사 또는 저작권자의 요청에 따라 멤버십에서 제외되는 콘텐츠가 있을 수 있습니다.</p>
                  <p>• 콘텐츠별 영상 화질, 음성 및 음향 방식, 언어 제공 등은 상이하며, 모든 콘텐츠가 동일한 사양으로 제공되지는 않습니다.</p>
                  <p>• 멤버십에는 청소년 관람 불가 콘텐츠가 포함되어 있으며, 본인 확인 및 성인 인증된 회원만 청소년 관람 불가 콘텐츠 이용이 가능합니다.</p>
                  <p>• 멤버십 종류에 따라 동시 시청 가능 기기 수가 다르며, 이를 초과하는 즉시 시청이 제한됩니다.</p>
                  <p>• 다운로드한 콘텐츠는 일정 기간 동안만 시청 가능하며, 멤버십이 만료되면 더 이상 이용할 수 없습니다.</p>
                  <p>• 일부 콘텐츠는 콘텐츠 제공사 또는 저작권자의 요청에 따라 스트리밍으로만 제공되며, 다운로드 이용이 불가할 수 있습니다.</p>
                  <p>• 콘텐츠 제공사 또는 저작권자의 요청으로 특정 기기에서는 일부 콘텐츠 시청이 제한될 수 있습니다.</p>
                </div>
              </div>

              {/* 재생 및 이용 환경 안내 */}
              <div className={styles.noticeBox}>
                <h3 className={styles.noticeBoxTitle}>
                  재생 및 이용 환경 안내
                </h3>
                <div className={styles.noticeBoxContent}>
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
              <div className={styles.noticeBox}>
                <h3 className={styles.noticeBoxTitle}>
                  기타 안내
                </h3>
                <div className={styles.noticeBoxContent}>
                  <p>• 기타 궁금하신 점은 고객센터를 통해 1:1 문의해 주시기 바랍니다.</p>
                </div>
              </div>
            </div>
          </div>
        </section>
      </main>

      {/* 멤버십 선택 모달 */}
      {isModalOpen && (
        <div className={styles.membershipSelectionModal}>
          <div className={styles.membershipSelectionContainer}>
            {/* 모달 닫기 버튼 */}
            <button
              onClick={closeModal}
              className={styles.modalCloseButton}
            >
              ×
            </button>
            
            {/* 모달 제목 */}
            <div className={styles.modalTitle}>
              <h3 className={styles.modalTitleText}>멤버십 선택</h3>
              <p className={styles.modalSubtitle}>언제든 해지가 가능해요!</p>
            </div>
            
            {/* 멤버십 플랜 선택 */}
            <div className="space-y-4 mb-8">
              {/* 베이직 플랜 */}
              <div
                className={`${styles.planSelectionCard} ${selectedPlan === 'basic' ? styles.planSelectionCardSelected : ''}`}
                onMouseEnter={() => setHoveredPlan('basic')}
                onMouseLeave={() => setHoveredPlan(null)}
                onClick={() => handlePlanSelect('basic')}
              >
                <div className={styles.planSelectionCardContent}>
                  <div className={styles.planSelectionInfo}>
                    <h4>베이직</h4>
                    <p>프로필 1인 · 동시재생 1회선</p>
                  </div>
                  <div className={styles.planSelectionPrice}>
                    월 9,900원
                  </div>
                </div>
              </div>
              
              {/* 프리미엄 플랜 */}
              <div
                className={`${styles.planSelectionCard} ${selectedPlan === 'premium' ? styles.planSelectionCardSelected : ''}`}
                onMouseEnter={() => setHoveredPlan('premium')}
                onMouseLeave={() => setHoveredPlan(null)}
                onClick={() => handlePlanSelect('premium')}
              >
                <div className={styles.planSelectionCardContent}>
                  <div className={styles.planSelectionInfo}>
                    <h4>프리미엄</h4>
                    <p>프로필 4인 · 동시재생 4회선</p>
                  </div>
                  <div className={styles.planSelectionPrice}>
                    월 14,900원
                  </div>
                </div>
              </div>
            </div>
            
            {/* 구독 시작 버튼 */}
            <button
              onClick={openPaymentModal}
              className={styles.subscriptionStartButton}
            >
              {selectedPlan === 'basic' ? '베이직' : '프리미엄'} 멤버십 시작하기
            </button>
          </div>
        </div>
      )}

      {/* 결제 모달 */}
      <PaymentModal
        isOpen={isPaymentModalOpen}
        onClose={closePaymentModal}
        planInfo={planInfo}
        paymentMethod={paymentMethod}
        onChangePaymentMethod={handlePaymentMethodChange}
        selectedPaymentService={selectedPaymentService}
        onSelectPaymentService={handlePaymentServiceSelect}
        onOpenCardRegistration={openCardRegistrationModal}
        onPay={handlePayment}
      />

      <PaymentFailureModal
        isOpen={isPaymentFailureOpen}
        onClose={() => setIsPaymentFailureOpen(false)}
        message={paymentFailureMsg}
      />

      {/* 카드 등록 모달 */}
      <CardRegistrationModal
        isOpen={isCardRegistrationModalOpen}
        onClose={closeCardRegistrationModal}
        onBack={() => {
          closeCardRegistrationModal();
          setIsPaymentModalOpen(true);
        }}
        onSubmit={handleCardRegistrationSubmit}
      />
    </div>
  );
}
