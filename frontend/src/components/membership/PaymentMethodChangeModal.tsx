"use client";
import { useState } from "react";
import { PaymentMethodResponse } from "@/lib/api/membership";
import styles from "./PaymentMethodChangeModal.module.css";

interface PaymentMethodChangeModalProps {
  isOpen: boolean;
  onClose: () => void;
  paymentMethods: PaymentMethodResponse[];
  onRefresh: () => void;
}

export default function PaymentMethodChangeModal({
  isOpen,
  onClose,
  paymentMethods,
  onRefresh,
}: PaymentMethodChangeModalProps) {
  const [isProcessing, setIsProcessing] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState('simple');
  const [selectedPaymentService, setSelectedPaymentService] = useState('');
  const [isAgreed, setIsAgreed] = useState(false);

  if (!isOpen) return null;

  const handleSetDefault = async (id: number) => {
    if (isProcessing) return;
    setIsProcessing(true);
    
    try {
      // TODO: setDefaultPaymentMethod API 호출
      alert('기본 결제수단으로 설정되었습니다.');
      onRefresh();
    } catch (error) {
      alert('기본 결제수단 설정에 실패했습니다.');
    } finally {
      setIsProcessing(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (isProcessing) return;
    
    if (!confirm('정말로 이 결제수단을 삭제하시겠습니까?')) {
      return;
    }

    setIsProcessing(true);
    try {
      // TODO: deletePaymentMethod API 호출
      alert('결제수단이 삭제되었습니다.');
      onRefresh();
    } catch (error) {
      alert('결제수단 삭제에 실패했습니다.');
    } finally {
      setIsProcessing(false);
    }
  };

  const getPaymentMethodIcon = (type: string) => {
    return type === 'card' ? '💳' : '📱';
  };

  const getPaymentMethodText = (method: PaymentMethodResponse) => {
    if (method.type === 'card') {
      return `${method.brand || '카드'} ${method.last4 ? `****${method.last4}` : ''}`;
    }
    return '휴대폰 결제';
  };

  return (
    <div className={styles.modalOverlay} onClick={onClose}>
      <div className={styles.modalContent} onClick={(e) => e.stopPropagation()}>
        {/* 모달 헤더 */}
        <div className={styles.modalHeader}>
          <h3 className={styles.modalTitle}>결제 수단 변경</h3>
          <button className={styles.closeButton} onClick={onClose}>
            ×
          </button>
        </div>

        {/* 결제 수단 선택 섹션 */}
        <div className={styles.paymentMethodSection}>
          {/* 간편 결제 */}
          <div className={styles.paymentMethodOption}>
            <label className={styles.paymentMethodLabel}>
              <input
                type="radio"
                name="paymentMethod"
                value="simple"
                checked={paymentMethod === 'simple'}
                onChange={() => setPaymentMethod('simple')}
                className={styles.paymentMethodRadio}
              />
              <span className={styles.paymentMethodText}>간편 결제</span>
            </label>
            
            {/* 간편 결제 추가 영역 */}
            <div className={styles.addPaymentArea}>
              <div className={styles.addPaymentContent}>
                <span className={styles.addIcon}>⊕</span>
                <span className={styles.addText}>간편 결제 추가</span>
              </div>
            </div>
          </div>

                      {/* 다른 결제 수단 */}
            <div className={styles.paymentMethodOption}>
              <label className={styles.paymentMethodLabel}>
                              <input
                type="radio"
                name="paymentMethod"
                value="other"
                checked={paymentMethod === 'other'}
                onChange={() => setPaymentMethod('other')}
                className={styles.paymentMethodRadio}
              />
                <span className={styles.paymentMethodText}>다른 결제 수단</span>
              </label>
              
              {paymentMethod === 'other' && (
                <div className={styles.otherPaymentGrid}>
                  <div 
                    className={`${styles.paymentMethodCard} ${selectedPaymentService === 'kakao' ? styles.paymentMethodCardSelected : ''}`}
                    onClick={() => setSelectedPaymentService('kakao')}
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
                    onClick={() => setSelectedPaymentService('toss')}
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
                    onClick={() => setSelectedPaymentService('nice')}
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

        {/* 하단 정보 및 버튼 */}
        <div className={styles.modalFooter}>
          <div className={styles.infoSection}>
            <ul className={styles.infoList}>
              <li>변경한 결제 수단으로 다음 정기 결제일에 자동 결제됩니다.</li>
              <li>결제 수단을 변경해도 멤버십이 끊기거나 결제일이 바뀌지 않습니다.</li>
              <li>변경한 수단에서 정기결제가 이루어지지 않은 경우엔 멤버십이 해지될 수 있습니다.</li>
            </ul>
          </div>
          
          <div className={styles.agreementSection}>
            <label className={styles.agreementLabel}>
              <input
                type="checkbox"
                checked={isAgreed}
                onChange={(e) => setIsAgreed(e.target.checked)}
                className={styles.agreementCheckbox}
              />
              <span className={styles.agreementText}>매월 정기 결제에 동의합니다.</span>
            </label>
          </div>
          
          <button 
            className={`${styles.changeButton} ${isAgreed ? styles.changeButtonEnabled : styles.changeButtonDisabled}`}
            disabled={!isAgreed}
            onClick={() => {
              if (isAgreed) {
                // TODO: 결제수단 변경 API 호출
                alert('결제수단이 변경되었습니다.');
                onClose();
              }
            }}
          >
            변경하기
          </button>
        </div>
      </div>
    </div>
  );
}
