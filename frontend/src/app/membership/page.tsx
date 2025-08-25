"use client";
import { useState, useEffect } from "react";
import Header from "@/components/layout/Header";
import { getMembershipPlans, getUserMembership, getPaymentMethods } from "@/lib/api/membership";

/**
 * 멤버십 페이지
 * 멤버십 플랜 선택, 결제 수단 관리, 구독 상태 확인
 */
export default function MembershipPage() {
  const [membershipPlans, setMembershipPlans] = useState<any[]>([]);
  const [userMembership, setUserMembership] = useState<any>(null);
  const [paymentMethods, setPaymentMethods] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedPlan, setSelectedPlan] = useState<any>(null);

  // 멤버십 데이터 로드
  useEffect(() => {
    const loadMembershipData = async () => {
      try {
        setIsLoading(true);
        setError(null);
        
        // 병렬로 여러 API 호출
        const [plansData, membershipData, paymentData] = await Promise.all([
          getMembershipPlans(),
          getUserMembership(),
          getPaymentMethods()
        ]);
        
        setMembershipPlans((plansData as any) || []);
        setUserMembership((membershipData as any) || null);
        setPaymentMethods((paymentData as any) || []);
        
      } catch (err) {
        console.error('멤버십 데이터 로드 실패:', err);
        setError('멤버십 데이터를 불러오는데 실패했습니다.');
      } finally {
        setIsLoading(false);
      }
    };

    loadMembershipData();
  }, []);

  // 멤버십 플랜 선택
  const handlePlanSelect = (plan: any) => {
    setSelectedPlan(plan);
  };

  // 멤버십 구독 시작
  const handleSubscribe = async () => {
    if (!selectedPlan) {
      alert('멤버십 플랜을 선택해주세요.');
      return;
    }
    
    try {
      // TODO: 결제 수단 선택 및 구독 처리
      alert(`${selectedPlan.name} 플랜 구독을 시작합니다.`);
    } catch (err) {
      console.error('구독 실패:', err);
      alert('구독 처리에 실패했습니다.');
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-xl text-gray-600">로딩 중...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-xl text-red-600">{error}</div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <Header />
      
      <main className="pt-16">
        <div className="max-w-6xl mx-auto px-6 py-8">
          {/* 페이지 제목 */}
          <h1 className="text-3xl font-bold text-gray-800 mb-8">멤버십</h1>

          {/* 현재 멤버십 상태 */}
          {userMembership && (
            <div className="bg-white rounded-lg p-6 mb-8 shadow-sm border border-gray-200">
              <h2 className="text-xl font-semibold text-gray-800 mb-4">현재 멤버십</h2>
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-lg font-medium text-gray-800">
                    {userMembership.planName || '기본 플랜'}
                  </p>
                  <p className="text-gray-600">
                    만료일: {userMembership.expiryDate || '무제한'}
                  </p>
                </div>
                <button className="px-4 py-2 bg-red-500 hover:bg-red-600 text-white rounded-lg transition-colors">
                  구독 취소
                </button>
              </div>
            </div>
          )}

          {/* 멤버십 플랜 선택 */}
          <div className="bg-white rounded-lg p-6 mb-8 shadow-sm border border-gray-200">
            <h2 className="text-xl font-semibold text-gray-800 mb-6">멤버십 플랜 선택</h2>
            
            {membershipPlans.length > 0 ? (
              <div className="grid md:grid-cols-3 gap-6">
                {membershipPlans.map((plan: any) => (
                  <div 
                    key={plan.id}
                    className={`border-2 rounded-lg p-6 cursor-pointer transition-all ${
                      selectedPlan?.id === plan.id
                        ? 'border-purple-500 bg-purple-50'
                        : 'border-gray-200 hover:border-gray-300'
                    }`}
                    onClick={() => handlePlanSelect(plan)}
                  >
                    <div className="text-center">
                      <h3 className="text-xl font-bold text-gray-800 mb-2">
                        {plan.name || '기본 플랜'}
                      </h3>
                      <div className="text-3xl font-bold text-purple-600 mb-4">
                        ₩{plan.price?.toLocaleString() || '0'}
                        <span className="text-lg text-gray-500">/월</span>
                      </div>
                      
                      <ul className="text-left space-y-2 mb-6">
                        {plan.features?.map((feature: string, index: number) => (
                          <li key={index} className="flex items-center text-gray-600">
                            <svg className="w-5 h-5 text-green-500 mr-2" fill="currentColor" viewBox="0 0 20 20">
                              <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                            </svg>
                            {feature}
                          </li>
                        )) || (
                          <>
                            <li className="flex items-center text-gray-600">
                              <svg className="w-5 h-5 text-green-500 mr-2" fill="currentColor" viewBox="0 0 20 20">
                                <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                              </svg>
                              HD 화질 스트리밍
                            </li>
                            <li className="flex items-center text-gray-600">
                              <svg className="w-5 h-5 text-green-500 mr-2" fill="currentColor" viewBox="0 0 20 20">
                                <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                              </svg>
                              광고 없는 시청
                            </li>
                            <li className="flex items-center text-gray-600">
                              <svg className="w-5 h-5 text-green-500 mr-2" fill="currentColor" viewBox="0 0 20 20">
                                <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                              </svg>
                              동시 시청 가능
                            </li>
                          </>
                        )}
                      </ul>
                      
                      <button 
                        className={`w-full py-3 rounded-lg font-semibold transition-colors ${
                          selectedPlan?.id === plan.id
                            ? 'bg-purple-600 text-white hover:bg-purple-700'
                            : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                        }`}
                        onClick={() => handlePlanSelect(plan)}
                      >
                        {selectedPlan?.id === plan.id ? '선택됨' : '선택하기'}
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-center py-12 text-gray-500">
                멤버십 플랜을 불러올 수 없습니다
              </div>
            )}
          </div>

          {/* 결제 수단 관리 */}
          <div className="bg-white rounded-lg p-6 mb-8 shadow-sm border border-gray-200">
            <h2 className="text-xl font-semibold text-gray-800 mb-6">결제 수단</h2>
            
            {paymentMethods.length > 0 ? (
              <div className="space-y-4">
                {paymentMethods.map((method: any) => (
                  <div key={method.id} className="flex items-center justify-between p-4 border border-gray-200 rounded-lg">
                    <div className="flex items-center space-x-3">
                      <div className="w-10 h-10 bg-gray-200 rounded-full flex items-center justify-center">
                        <span className="text-gray-600 text-sm">💳</span>
                      </div>
                      <div>
                        <p className="font-medium text-gray-800">
                          {method.cardType || '신용카드'} •••• {method.last4Digits || '****'}
                        </p>
                        <p className="text-sm text-gray-600">
                          만료: {method.expiryMonth || '**'}/{method.expiryYear || '**'}
                        </p>
                      </div>
                    </div>
                    <button className="px-3 py-1 text-red-600 hover:text-red-700 text-sm">
                      삭제
                    </button>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-center py-8 text-gray-500">
                등록된 결제 수단이 없습니다
              </div>
            )}
            
            <button className="mt-4 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors">
              + 결제 수단 추가
            </button>
          </div>

          {/* 구독 버튼 */}
          {selectedPlan && (
            <div className="text-center">
              <button
                onClick={handleSubscribe}
                className="px-8 py-4 bg-purple-600 hover:bg-purple-700 text-white text-xl font-bold rounded-lg transition-colors"
              >
                {selectedPlan.name} 구독 시작하기
              </button>
              <p className="mt-2 text-sm text-gray-600">
                월 ₩{selectedPlan.price?.toLocaleString() || '0'}에 구독됩니다
              </p>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
