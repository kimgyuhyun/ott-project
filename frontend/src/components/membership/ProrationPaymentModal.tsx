"use client";
import PaymentMethodItem from "@/components/membership/PaymentMethodItem";
import { useState, useEffect } from "react";
import { useProrationPayment } from "@/hooks/useProrationPayment";
import { useAuth } from "@/hooks/useAuth";
import styles from "./ProrationPaymentModal.module.css";

interface PlanInfo {
  name: string;
  price: string;
  features: string[];
  code: string;
}

interface ProrationPaymentModalProps {
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

export default function ProrationPaymentModal({
  isOpen,
  onClose,
  planInfo,
  paymentMethod,
  onChangePaymentMethod,
  selectedPaymentService,
  onSelectPaymentService,
  onOpenCardRegistration,
  onPay,
}: ProrationPaymentModalProps) {
  const [agreed, setAgreed] = useState(false);
  const [showSuccess, setShowSuccess] = useState(false);
  const [showError, setShowError] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  
  const { processProrationPayment, isLoading, error } = useProrationPayment();
  const { user } = useAuth();

  // 에러 발생 시 에러 모달 표시
  useEffect(() => {
    if (error) {
      setErrorMessage(error);
      setShowError(true);
    }
  }, [error]);

  const handlePayment = async () => {
    if (!agreed) return;
    // '다른 결제 수단' 선택 시에는 PG 선택 필수, 간편 결제는 선택 없어도 진행
    if (paymentMethod === 'other' && !selectedPaymentService) return;

    console.log('ProrationPaymentModal - planInfo:', planInfo);
    console.log('ProrationPaymentModal - planCode:', planInfo.code);

    try {
      const result = await processProrationPayment({
        planCode: planInfo.code,
        // 간편 결제인 경우 선택값이 없으면 기본 PG로 'kakao' 사용
        paymentService: paymentMethod === 'other' ? selectedPaymentService : (selectedPaymentService || 'kakao'),
        successUrl: `${window.location.origin}/membership/success`,
        cancelUrl: `${window.location.origin}/membership/cancel`
      });

      if (result.success) {
        onPay();
        setShowSuccess(true);
      } else {
        setErrorMessage(result.errorMessage || '차액 결제에 실패했습니다.');
        setShowError(true);
      }
    } catch (err) {
      setErrorMessage('차액 결제 처리 중 오류가 발생했습니다.');
      setShowError(true);
    }
  };

  const handleCloseError = () => {
    setShowError(false);
    setErrorMessage('');
  };

  if (!isOpen) return null;

  return (
    <>
      <div className={styles.prorationPaymentModalOverlay}>
        <div className={styles.prorationPaymentModalContainer}>
          {/* 모달 헤더 */}
          <div className={styles.modalHeader}>
            <h3 className={styles.modalTitle}>플랜 업그레이드 차액 결제</h3>
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
              <span className={styles.paymentAmountLabel}>차액 결제</span>
              <span className={styles.paymentAmountValue}>차액 {planInfo.price}원</span>
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
                    <div className={styles.paymentMethodIcon}>
                      <img 
                        src="/images/logos/kakao.svg" 
                        alt="카카오페이" 
                        className={styles.paymentMethodLogo}
                      />
                    </div>
                    <div className={styles.paymentMethodLabel}>카카오페이</div>
                  </div>
                  
                  <div 
                    className={`${styles.paymentMethodCard} ${selectedPaymentService === 'toss' ? styles.paymentMethodCardSelected : ''}`}
                    onClick={() => onSelectPaymentService('toss')}
                  >
                    <div className={styles.paymentMethodIcon}>
                      <img 
                        src="/images/logos/tosspaylogo.jpg" 
                        alt="토스페이" 
                        className={styles.paymentMethodLogo}
                      />
                    </div>
                    <div className={styles.paymentMethodLabel}>토스페이</div>
                  </div>
                  
                  <div 
                    className={`${styles.paymentMethodCard} ${selectedPaymentService === 'nice' ? styles.paymentMethodCardSelected : ''}`}
                    onClick={() => onSelectPaymentService('nice')}
                  >
                    <div className={styles.paymentMethodIcon}>
                      <img 
                        src="/images/logos/nicepay.png" 
                        alt="나이스페이먼츠" 
                        className={styles.paymentMethodLogo}
                      />
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
                플랜 업그레이드 차액 결제에 동의합니다.
              </span>
            </label>
          </div>

          {/* 결제 버튼 */}
          <button
            onClick={handlePayment}
            disabled={!agreed || (paymentMethod === 'other' && !selectedPaymentService) || isLoading}
            className={`${styles.paymentButton} ${agreed && (paymentMethod !== 'other' || selectedPaymentService) && !isLoading ? styles.paymentButtonEnabled : styles.paymentButtonDisabled}`}
          >
            <span>
              {isLoading ? '차액 결제 처리 중...' : `차액 ${planInfo.price}원 결제하기`}
            </span>
          </button>

          {/* 멤버십 안내 섹션 */}
          <div className={styles.membershipNoticeSection}>
            <div className={styles.noticeBox}>
              <h4 className={styles.noticeBoxTitle}>플랜 업그레이드 안내</h4>
              <div className={styles.noticeBoxContent}>
                <p>• 업그레이드 시 남은 기간에 대한 차액이 즉시 결제됩니다.</p>
                <p>• 결제 완료 후 즉시 새로운 플랜 혜택을 이용하실 수 있습니다.</p>
                <p>• 다음 정기 결제일부터는 새로운 플랜 가격으로 자동 결제됩니다.</p>
                <p>• 차액 결제 후에는 다운그레이드가 불가능합니다.</p>
                <p>• 결제 금액은 부가가치세(VAT)가 포함된 가격입니다.</p>
              </div>
            </div>

            <div className={styles.noticeBox}>
              <h4 className={styles.noticeBoxTitle}>환불 정책</h4>
              <div className={styles.noticeBoxContent}>
                <p>• 차액 결제 후에는 환불이 불가능합니다.</p>
                <p>• 플랜 변경은 다음 결제일부터 다운그레이드만 가능합니다.</p>
                <p>• 기타 문의사항은 고객센터를 통해 문의해 주시기 바랍니다.</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 성공 모달 */}
      {showSuccess && (
        <div className={styles.successModal}>
          <div className={styles.successContent}>
            <h3>플랜 업그레이드가 완료되었습니다.</h3>
            <p>플랜이 즉시 업그레이드되었습니다.</p>
            <div className={styles.successButtonGroup}>
              <button 
                onClick={() => {
                  setShowSuccess(false);
                  onClose();
                  window.location.href = '/membership/guide';
                }}
                className={styles.successButton}
              >
                멤버십 확인하러 가기
              </button>
              <button 
                onClick={() => {
                  setShowSuccess(false);
                  onClose();
                  window.location.href = '/';
                }}
                className={styles.cancelButton}
              >
                아니요
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 에러 모달 */}
      {showError && (
        <div className={styles.errorModal}>
          <div className={styles.errorContent}>
            <h3>차액 결제 실패</h3>
            <p>{errorMessage}</p>
            <button onClick={handleCloseError}>확인</button>
          </div>
        </div>
      )}
    </>
  );
}
