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
  const openModal = () => setIsModalOpen(true);
  const closeModal = () => setIsModalOpen(false);
  const openPaymentModal = () => {
    // 비로그인 상태이거나 user 객체가 없으면 결제 단계로 가지 않고 로그인 페이지로 리다이렉트
    if (isAuthenticated !== true || !user) {
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
          <div className="min-h-screen" style={{ backgroundColor: 'var(--background-1, #121212)' }}>
      <Header />
      <main className="relative pt-16">
        <div className="min-h-screen flex items-center justify-center">
          <div className="text-center">
            <div className="animate-spin rounded-full h-32 w-32 border-b-2 mx-auto mb-4" style={{ borderColor: 'var(--foreground-slight, #816BFF)' }}></div>
            <p style={{ color: 'var(--foreground-1, #F7F7F7)' }} className="text-xl">로딩 중...</p>
          </div>
        </div>
      </main>
    </div>
    );
  }

  // 에러가 있을 때
  if (error) {
    return (
          <div className="min-h-screen" style={{ backgroundColor: 'var(--background-1, #121212)' }}>
      <Header />
      <main className="relative pt-16">
        <div className="min-h-screen flex items-center justify-center">
          <div className="text-center">
            <p style={{ color: 'var(--foreground-1, #F7F7F7)' }} className="text-xl mb-4">{error}</p>
            <button 
              onClick={() => window.location.reload()} 
              className="px-6 py-3 rounded-lg"
              style={{ backgroundColor: 'var(--foreground-slight, #816BFF)', color: 'var(--foreground-1, #F7F7F7)' }}
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
    <div className="min-h-screen" style={{ backgroundColor: 'var(--background-1, #121212)' }}>
      <Header />
      
      {/* 메인 콘텐츠 영역 */}
      <main className="relative pt-16">
        {/* 히어로 섹션 */}
        <div className="min-h-screen flex items-center justify-center px-6" style={{ backgroundColor: 'var(--background-1, #121212)' }}>
          {/* 텍스트 콘텐츠 */}
          <div className="text-center">
            <h1 className="text-5xl md:text-6xl font-bold mb-4" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>
              동시방영 신작부터
            </h1>
            <h2 className="text-3xl md:text-4xl font-medium mb-8" style={{ color: 'var(--foreground-3, #ABABAB)' }}>
              역대 인기작까지 한 곳에서
            </h2>
            
            {/* 멤버십 시작 버튼 */}
            <button
              onClick={openModal}
              className="text-xl font-bold px-12 py-4 rounded-lg transition-all duration-300 transform hover:scale-105 shadow-2xl"
              style={{ backgroundColor: 'var(--foreground-slight, #816BFF)' }}
            >
              <span style={{ color: 'var(--foreground-1, #F7F7F7)' }}>멤버십 시작하기</span>
            </button>
            
            {/* 로그인 안내 메시지 제거 */}
          </div>
        </div>

        {/* 멤버십 설명 구간 */}
        <section className="py-20 px-6" style={{ backgroundColor: 'var(--background-1, #121212)' }}>
          <div className="max-w-6xl mx-auto text-center">
            <h2 className="text-4xl font-bold mb-4" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>
              나에게 맞는 멤버십을 확인하세요
            </h2>
            <p className="text-xl mb-16" style={{ color: 'var(--foreground-3, #ABABAB)' }}>
              멤버십은 언제든 해지가 가능해요.
            </p>
            
            {/* 멤버십 플랜 카드 */}
            <div className="grid md:grid-cols-2 gap-8 max-w-4xl mx-auto">
              {/* 베이직 플랜 */}
              <div className="rounded-lg p-8 border-2" style={{ backgroundColor: 'var(--background-2, #000000)', borderColor: 'var(--border-1, #323232)' }}>
                <h3 className="text-2xl font-bold mb-4" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>베이직</h3>
                <p className="text-3xl font-bold mb-6" style={{ color: 'var(--foreground-slight, #816BFF)' }}>월 9,900원</p>
                <ul className="space-y-3 text-left">
                  <li className="flex items-center" style={{ color: 'var(--foreground-3, #ABABAB)' }}>
                    <span className="mr-3" style={{ color: 'var(--foreground-slight, #816BFF)' }}>✓</span>
                    프로필 1인·동시재생 1회선
                  </li>
                  <li className="flex items-center" style={{ color: 'var(--foreground-3, #ABABAB)' }}>
                    <span className="mr-3" style={{ color: 'var(--foreground-slight, #816BFF)' }}>✓</span>
                    최신화 시청
                  </li>
                  <li className="flex items-center" style={{ color: 'var(--foreground-3, #ABABAB)' }}>
                    <span className="mr-3" style={{ color: 'var(--foreground-slight, #816BFF)' }}>✓</span>
                    다운로드 지원
                  </li>
                  <li className="flex items-center" style={{ color: 'var(--foreground-3, #ABABAB)' }}>
                    <span className="mr-3" style={{ color: 'var(--foreground-slight, #816BFF)' }}>✓</span>
                    FHD 화질 지원
                  </li>
                  <li className="flex items-center" style={{ color: 'var(--foreground-3, #ABABAB)' }}>
                    <span className="mr-3" style={{ color: 'var(--foreground-slight, #816BFF)' }}>✓</span>
                    TV 앱 지원
                  </li>
                </ul>
              </div>
              
              {/* 프리미엄 플랜 */}
              <div className="rounded-lg p-8 border-2" style={{ backgroundColor: 'var(--background-2, #000000)', borderColor: 'var(--border-1, #323232)' }}>
                <h3 className="text-2xl font-bold mb-4" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>프리미엄</h3>
                <p className="text-3xl font-bold mb-6" style={{ color: 'var(--foreground-slight, #816BFF)' }}>월 14,900원</p>
                <ul className="space-y-3 text-left">
                  <li className="flex items-center" style={{ color: 'var(--foreground-3, #ABABAB)' }}>
                    <span className="mr-3" style={{ color: 'var(--foreground-slight, #816BFF)' }}>✓</span>
                    프로필 4인·동시재생 4회선
                  </li>
                  <li className="flex items-center" style={{ color: 'var(--foreground-3, #ABABAB)' }}>
                    <span className="mr-3" style={{ color: 'var(--foreground-slight, #816BFF)' }}>✓</span>
                    최신화 시청
                  </li>
                  <li className="flex items-center" style={{ color: 'var(--foreground-3, #ABABAB)' }}>
                    <span className="mr-3" style={{ color: 'var(--foreground-slight, #816BFF)' }}>✓</span>
                    다운로드 지원
                  </li>
                  <li className="flex items-center" style={{ color: 'var(--foreground-3, #ABABAB)' }}>
                    <span className="mr-3" style={{ color: 'var(--foreground-slight, #816BFF)' }}>✓</span>
                    FHD 화질 지원
                  </li>
                  <li className="flex items-center" style={{ color: 'var(--foreground-3, #ABABAB)' }}>
                    <span className="mr-3" style={{ color: 'var(--foreground-slight, #816BFF)' }}>✓</span>
                    TV 앱 지원
                  </li>
                </ul>
              </div>
            </div>
            
            {/* 멤버십 유의사항 */}
            <div className="mt-16">
              <p style={{ color: 'var(--foreground-3, #ABABAB)' }} className="text-lg">멤버십 유의사항</p>
            </div>
          </div>
        </section>
      </main>

      {/* 멤버십 선택 모달 */}
      {isModalOpen && (
        <div className="fixed inset-0 flex items-center justify-center z-50 p-4" style={{ backgroundColor: 'var(--background-dim-1, rgba(0,0,0,0.7))' }}>
          <div className="rounded-lg p-8 max-w-md w-full border" style={{ backgroundColor: 'var(--background-1, #121212)', borderColor: 'var(--border-1, #323232)' }}>
            {/* 모달 닫기 버튼 */}
            <button
              onClick={closeModal}
              className="absolute top-4 right-4 text-2xl"
              style={{ color: 'var(--foreground-3, #ABABAB)' }}
            >
              ×
            </button>
            
            {/* 모달 제목 */}
            <div className="text-center mb-6">
              <h3 className="text-2xl font-bold mb-2" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>멤버십 선택</h3>
              <p style={{ color: 'var(--foreground-3, #ABABAB)' }}>언제든 해지가 가능해요!</p>
            </div>
            
            {/* 멤버십 플랜 선택 */}
            <div className="space-y-4 mb-8">
              {/* 베이직 플랜 */}
              <div
                className={`p-4 rounded-lg border-2 cursor-pointer transition-all duration-200`}
                style={{
                  backgroundColor: hoveredPlan === 'basic' && selectedPlan !== 'basic' ? 'var(--background-2, #000000)' : 'var(--background-1, #121212)',
                  borderColor:
                    selectedPlan === 'basic'
                      ? 'var(--foreground-slight, #816BFF)'
                      : hoveredPlan === 'basic'
                        ? 'var(--border-2, #505050)'
                        : 'var(--border-1, #323232)',
                  boxShadow: selectedPlan === 'basic' ? '0 0 20px rgba(129, 107, 255, 0.2)' : 'none'
                }}
                onMouseEnter={() => setHoveredPlan('basic')}
                onMouseLeave={() => setHoveredPlan(null)}
                onClick={() => handlePlanSelect('basic')}
              >
                <div className="flex justify-between items-center">
                  <div>
                    <h4 className="text-lg font-semibold" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>베이직</h4>
                    <p className="text-sm" style={{ color: 'var(--foreground-3, #ABABAB)' }}>프로필 1인 · 동시재생 1회선</p>
                  </div>
                  <div className="text-right">
                    <p className="text-lg font-bold" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>월 9,900원</p>
                  </div>
                </div>
              </div>
              
              {/* 프리미엄 플랜 */}
              <div
                className={`p-4 rounded-lg border-2 cursor-pointer transition-all duration-200`}
                style={{
                  backgroundColor: hoveredPlan === 'premium' && selectedPlan !== 'premium' ? 'var(--background-2, #000000)' : 'var(--background-1, #121212)',
                  borderColor:
                    selectedPlan === 'premium'
                      ? 'var(--foreground-slight, #816BFF)'
                      : hoveredPlan === 'premium'
                        ? 'var(--border-2, #505050)'
                        : 'var(--border-1, #323232)',
                  boxShadow: selectedPlan === 'premium' ? '0 0 20px rgba(129, 107, 255, 0.2)' : 'none'
                }}
                onMouseEnter={() => setHoveredPlan('premium')}
                onMouseLeave={() => setHoveredPlan(null)}
                onClick={() => handlePlanSelect('premium')}
              >
                <div className="flex justify-between items-center">
                  <div>
                    <h4 className="text-lg font-semibold" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>프리미엄</h4>
                    <p className="text-sm" style={{ color: 'var(--foreground-3, #ABABAB)' }}>프로필 4인 · 동시재생 4회선</p>
                  </div>
                  <div className="text-right">
                    <p className="text-lg font-bold" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>월 14,900원</p>
                  </div>
                </div>
              </div>
            </div>
            
            {/* 구독 시작 버튼 */}
            <button
              onClick={openPaymentModal}
              className="w-full py-4 rounded-lg font-bold transition-colors"
              style={{ backgroundColor: 'var(--foreground-slight, #816BFF)' }}
            >
              <span style={{ color: 'var(--foreground-1, #F7F7F7)' }}>
                {selectedPlan === 'basic' ? '베이직' : '프리미엄'} 멤버십 시작하기
              </span>
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
      {isCardRegistrationModalOpen && (
        <div className="fixed inset-0 flex items-center justify-center z-50 p-4" style={{ backgroundColor: 'var(--background-dim-1, rgba(0,0,0,0.7))' }}>
          <div className="rounded-lg p-6 max-w-md w-full border max-h-[90vh] overflow-y-auto" style={{ backgroundColor: 'var(--background-1, #121212)', borderColor: 'var(--border-1, #323232)' }}>
            {/* 모달 헤더 */}
            <div className="flex justify-between items-center mb-6">
              <button
                onClick={() => {
                  closeCardRegistrationModal();
                  setIsPaymentModalOpen(true);
                }}
                className="text-xl hover:opacity-80 transition-opacity"
                style={{ color: 'var(--foreground-1, #F7F7F7)' }}
              >
                ←
              </button>
              <h3 className="text-xl font-bold" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>간편 결제 등록</h3>
              <button
                onClick={closeCardRegistrationModal}
                className="text-xl hover:opacity-80 transition-opacity"
                style={{ color: 'var(--foreground-1, #F7F7F7)' }}
              >
                ×
              </button>
            </div>

            {/* 카드 정보 입력 */}
            <div className="space-y-6">
              <div>
                <h4 className="font-semibold mb-3" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>카드 정보 입력</h4>
                
                {/* 카드 번호 */}
                <div className="mb-4">
                  <label className="block text-sm mb-2" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>카드 번호</label>
                  <div className="flex space-x-2">
                    <input
                      type="text"
                      placeholder="0000"
                      maxLength={4}
                      className="flex-1 p-2 rounded outline-none"
                      style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
                    />
                    <span style={{ color: 'var(--foreground-1, #F7F7F7)' }}>-</span>
                    <input
                      type="text"
                      placeholder="0000"
                      maxLength={4}
                      className="flex-1 p-2 rounded outline-none"
                      style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
                    />
                    <span style={{ color: 'var(--foreground-1, #F7F7F7)' }}>-</span>
                    <input
                      type="text"
                      placeholder="0000"
                      maxLength={4}
                      className="flex-1 p-2 rounded outline-none"
                      style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
                    />
                    <span style={{ color: 'var(--foreground-1, #F7F7F7)' }}>-</span>
                    <input
                      type="text"
                      placeholder="0000"
                      maxLength={4}
                      className="flex-1 p-2 rounded outline-none"
                      style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
                    />
                  </div>
                </div>

                {/* 유효기간 */}
                <div className="mb-4">
                  <label className="block text-sm mb-2" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>유효기간</label>
                  <input
                    type="text"
                    placeholder="MM/YY"
                    maxLength={5}
                    className="w-full p-2 rounded outline-none"
                    style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
                  />
                </div>

                {/* 생년월일 */}
                <div className="mb-4">
                  <label className="block text-sm mb-2" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>생년월일</label>
                  <input
                    type="text"
                    placeholder="YYMMDD (6자리)"
                    maxLength={6}
                    className="w-full p-2 rounded outline-none"
                    style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
                  />
                </div>
              </div>

              {/* 카드 비밀번호 */}
              <div>
                <h4 className="font-semibold mb-3" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>카드 비밀번호</h4>
                <div className="mb-4">
                  <label className="block text-sm mb-2" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>카드 비밀번호</label>
                  <p className="text-xs mb-2" style={{ color: 'var(--foreground-3, #ABABAB)' }}>비밀번호 앞 2자리</p>
                  <input
                    type="password"
                    maxLength={2}
                    className="w-full p-2 rounded outline-none"
                    style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
                  />
                </div>
              </div>

              {/* 동의 체크박스 */}
              <div className="mb-6">
                <label className="flex items-start cursor-pointer">
                  <input
                    type="checkbox"
                    defaultChecked
                    className="mr-3 mt-1"
                    style={{ accentColor: 'var(--foreground-slight, #816BFF)' }}
                  />
                  <span className="text-sm" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>
                    결제사 정보 제공에 동의합니다.
                  </span>
                </label>
              </div>

              {/* 등록하기 버튼 */}
              <button
                onClick={handleCardRegistrationSubmit}
                className="w-full py-4 rounded-lg font-bold transition-colors"
                style={{ backgroundColor: 'var(--button-slight-1, #323232)' }}
              >
                <span style={{ color: 'var(--foreground-1, #F7F7F7)' }}>등록하기</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
