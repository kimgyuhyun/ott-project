"use client";
import PaymentMethodItem from "@/components/membership/PaymentMethodItem";
import { useState } from "react";
import styles from "./PaymentModal.module.css";

interface PlanInfo {
  name: string;
  price: string;
  features: string[];
}

interface PaymentModalProps {
  isOpen: boolean;
  onClose: () => void;
  planInfo: PlanInfo;
  paymentMethod: string;
  onChangePaymentMethod: (method: string) => void;
  selectedPaymentService: string;
  onSelectPaymentService: (service: string) => void;
  onOpenCardRegistration: () => void;
  onPay: () => void;
}

export default function PaymentModal({
  isOpen,
  onClose,
  planInfo,
  paymentMethod,
  onChangePaymentMethod,
  selectedPaymentService,
  onSelectPaymentService,
  onOpenCardRegistration,
  onPay,
}: PaymentModalProps) {
  if (!isOpen) return null;
  const [agreed, setAgreed] = useState(false);

  return (
    <div className={styles.paymentModalOverlay}>
      <div className={styles.paymentModalContainer}>
        {/* 모달 헤더 */}
        <div className={styles.modalHeader}>
          <h3 className={styles.modalTitle}>결제</h3>
          <button
            onClick={onClose}
            className={styles.closeButton}
          >
            ×
          </button>
        </div>

        {/* 멤버십 정보 */}
        <div className={styles.membershipInfo}>
          <h4 className={styles.membershipInfoTitle}>{planInfo.name}</h4>
          <ul className={styles.membershipInfoList}>
            {planInfo.features.map((feature, index) => (
              <li key={index} className={styles.membershipInfoItem}>{feature}</li>
            ))}
          </ul>
        </div>

        {/* 결제 금액 정보 */}
        <div className={styles.paymentAmountInfo}>
          <div className={styles.paymentAmountRow}>
            <span className={styles.paymentAmountLabel}>정기 결제 (매월)</span>
            <span className={styles.paymentAmountValue}>월 {planInfo.price}원</span>
          </div>
          <div className={styles.paymentTotalRow}>
            <span className={styles.paymentTotalValue}>{planInfo.price}원</span>
          </div>
        </div>

        {/* 결제 수단 선택 */}
        <div className={styles.paymentMethodSection}>
          <h4 className={styles.paymentMethodTitle}>결제 수단</h4>

          {/* 간편 결제 */}
          <div className={styles.paymentMethodOption}>
            <label className={styles.paymentMethodLabel}>
              <input
                type="radio"
                name="paymentMethod"
                value="simple"
                checked={paymentMethod === 'simple'}
                onChange={() => onChangePaymentMethod('simple')}
                className={styles.paymentMethodRadio}
              />
              <span className={styles.paymentMethodText}>간편 결제</span>
            </label>
            {paymentMethod === 'simple' && (
              <div className={styles.simplePaymentAdd}>
                <div 
                  className={styles.simplePaymentButton}
                  onClick={onOpenCardRegistration}
                >
                  <div className={styles.simplePaymentContent}>
                    <span className={styles.simplePaymentIcon}>+</span>
                    <span className={styles.simplePaymentText}>간편 결제 추가</span>
                  </div>
                </div>
              </div>
            )}
          </div>

          {/* 다른 결제 수단 */}
          <div className={styles.paymentMethodOption}>
            <label className={styles.paymentMethodLabel}>
              <input
                type="radio"
                name="paymentMethod"
                value="other"
                checked={paymentMethod === 'other'}
                onChange={() => onChangePaymentMethod('other')}
                className={styles.paymentMethodRadio}
              />
              <span className={styles.paymentMethodText}>다른 결제 수단</span>
            </label>

            {paymentMethod === 'other' && (
              <div className={styles.otherPaymentGrid}>
                <div 
                  className={`${styles.paymentMethodCard} ${selectedPaymentService === 'kakao' ? styles.paymentMethodCardSelected : ''}`}
                  onClick={() => onSelectPaymentService('kakao')}
                >
                  <div className={styles.paymentMethodIcon} style={{ backgroundColor: '#FEE500' }}>
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <rect width="24" height="24" rx="4" fill="#FEE500"/>
                      <path d="M12 7c-3.59 0-6.5 1.94-6.5 4.33 0 1.58 1.15 2.95 2.9 3.74l-.46 2.76 3.05-2.07h1.01c3.59 0 6.5-1.94 6.5-4.33S15.59 7 12 7z" fill="#000000"/>
                    </svg>
                  </div>
                  <div className={styles.paymentMethodLabel}>카카오페이</div>
                </div>
                
                <div 
                  className={`${styles.paymentMethodCard} ${selectedPaymentService === 'toss' ? styles.paymentMethodCardSelected : ''}`}
                  onClick={() => onSelectPaymentService('toss')}
                >
                  <div className={styles.paymentMethodIcon} style={{ backgroundColor: '#FFFFFF' }}>
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <rect width="24" height="24" rx="4" fill="#FFFFFF"/>
                      <path d="M12 5c4.418 0 8 3.134 8 7-5.2-.1-8 3.8-12 0 0-3.866 3.134-7 8-7z" fill="#0064FF"/>
                      <circle cx="10.4" cy="9.2" r="1.6" fill="#FFFFFF"/>
                    </svg>
                  </div>
                  <div className={styles.paymentMethodLabel}>토스페이</div>
                </div>
                
                <div 
                  className={`${styles.paymentMethodCard} ${selectedPaymentService === 'nice' ? styles.paymentMethodCardSelected : ''}`}
                  onClick={() => onSelectPaymentService('nice')}
                >
                  <div className={styles.paymentMethodIcon} style={{ backgroundColor: '#0A68F5' }}>
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                      <rect width="24" height="24" rx="4" fill="#0A68F5"/>
                      <text x="12" y="15" textAnchor="middle" fontSize="11" fill="#FFFFFF" fontWeight="bold">NP</text>
                    </svg>
                  </div>
                  <div className={styles.paymentMethodLabel}>나이스페이먼츠</div>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* 약관 동의 */}
        <div className={styles.agreementSection}>
          <label className={styles.agreementLabel}>
            <input
              type="checkbox"
              checked={agreed}
              onChange={(e) => setAgreed(e.target.checked)}
              className={styles.agreementCheckbox}
            />
            <span className={styles.agreementText}>
              가격 및 유의사항을 확인하였으며, 매월 정기결제에 동의합니다.
            </span>
          </label>
        </div>

        {/* 결제 버튼 */}
        <button
          onClick={() => {
            if (!agreed) return;
            onPay();
          }}
          disabled={!agreed}
          className={`${styles.paymentButton} ${agreed ? styles.paymentButtonEnabled : styles.paymentButtonDisabled}`}
        >
          <span>{planInfo.price}원 결제하기</span>
        </button>

        {/* 멤버십 안내 섹션 */}
        <div className={styles.membershipNoticeSection}>
          <div className={styles.noticeBox}>
            <h4 className={styles.noticeBoxTitle}>멤버십 구독 및 결제 안내</h4>
            <div className={styles.noticeBoxContent}>
              <p>• 결제 금액은 부가가치세(VAT)가 포함된 가격입니다.</p>
              <p>• 멤버십은 월정액 유료 이용권으로, 결제 즉시 적용되며 이용이 시작됩니다.</p>
              <p>• 매월 정기 결제일에 등록한 결제 수단을 통해 자동으로 결제됩니다.</p>
              <p>• 쿠폰, 분분, 이벤트 등 무료 혜택과 중복 사용은 불가합니다.</p>
              <p>• 미성년자 회원은 법정대리인의 명의 또는 동의를 통해 결제해야 합니다.</p>
              <p>• 멤버십은 언제든지 해지할 수 있으며, 해지 후에도 남은 이용 기간까지는 서비스를 이용하실 수 있습니다.</p>
              <p>• 멤버십 해지는 결제 예정일 최소 24시간 이전에 신청해야 합니다.</p>
              <p>• 결제 실패 시 멤버십 정기 결제가 자동으로 해지될 수 있습니다.</p>
              <p>• 결제 당일을 제외하고는 결제 수단은 언제든지 변경할 수 있습니다.</p>
              <p>• 환불은 결제일로부터 7일 이내, 콘텐츠를 이용하지 않은 경우에만 가능합니다.</p>
            </div>
          </div>

          <div className={styles.noticeBox}>
            <h4 className={styles.noticeBoxTitle}>콘텐츠 이용 안내</h4>
            <div className={styles.noticeBoxContent}>
              <p>• 라프텔에서 제공되는 모든 콘텐츠는 대한민국 내에서만 이용 가능합니다.</p>
              <p>• 일부 콘텐츠는 별도 사전 고지 없이 서비스가 중단될 수 있습니다.</p>
              <p>• 콘텐츠별 영상 화질, 음성 및 음향 방식, 언어 제공 등은 상이합니다.</p>
              <p>• 멤버십에는 청소년 관람 불가 콘텐츠가 포함되어 있습니다.</p>
              <p>• 멤버십 종류에 따라 동시 시청 가능 기기 수가 다릅니다.</p>
              <p>• 다운로드한 콘텐츠는 일정 기간 동안만 시청 가능합니다.</p>
              <p>• 일부 콘텐츠는 스트리밍으로만 제공됩니다.</p>
            </div>
          </div>

          <div className={styles.noticeBox}>
            <h4 className={styles.noticeBoxTitle}>재생 및 이용 환경 안내</h4>
            <div className={styles.noticeBoxContent}>
              <p>• 지원 기기 및 플랫폼은 [이곳]에서 확인하실 수 있습니다.</p>
              <p>• 해외 직구 등으로 국내 정식 출시되지 않은 기기에서는 호환성 문제가 있을 수 있습니다.</p>
              <p>• 지원하는 기기에서는 라프텔 공식 앱을 설치 후 이용 가능합니다.</p>
              <p>• 모바일 데이터 환경에서는 Wi-Fi 이용을 권장합니다.</p>
              <p>• 안정적인 고속 인터넷 환경이 필요합니다.</p>
              <p>• 모든 콘텐츠는 DRM 기술로 보호됩니다.</p>
              <p>• 루팅되거나 탈옥된 기기에서는 재생이 제한됩니다.</p>
              <p>• 고급 보안 인증이 지원되지 않는 기기에서는 재생이 불가할 수 있습니다.</p>
              <p>• OS 버전, 기기 사양, 제조사에 따라 서비스가 정상 작동하지 않을 수 있습니다.</p>
              <p>• 영상 화질은 인터넷 환경과 디바이스 성능에 따라 달라집니다.</p>
              <p>• 콘텐츠 다운로드는 모바일/태블릿 앱에서만 가능합니다.</p>
            </div>
          </div>

          <div className={styles.noticeBox}>
            <div className={styles.noticeBoxTitle}>기타 안내</div>
            <div className={styles.noticeBoxContent}>
              <p>• 기타 궁금하신 점은 고객센터를 통해 1:1 문의해 주시기 바랍니다.</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}


