"use client";
import { useState } from "react";
import { MembershipPlan, changeMembershipPlan, MembershipPlanChangeResponse } from "@/lib/api/membership";
import { useAuth } from "@/lib/AuthContext";
import PlanChangeSuccessModal from "./PlanChangeSuccessModal";
import styles from "./PlanChangeModal.module.css";

interface PlanChangeModalProps {
  isOpen: boolean;
  onClose: () => void;
  currentPlan: MembershipPlan | null;
  targetPlan: MembershipPlan | null;
  userMembership: any; // UserMembership 타입
  onPlanChanged: () => void;
  onUpgradePayment: (plan: MembershipPlan) => void;
}

export default function PlanChangeModal({
  isOpen,
  onClose,
  currentPlan,
  targetPlan,
  userMembership,
  onPlanChanged,
  onUpgradePayment
}: PlanChangeModalProps) {
  const { user } = useAuth();
  const [isChanging, setIsChanging] = useState(false);
  const [changeResult, setChangeResult] = useState<MembershipPlanChangeResponse | null>(null);
  const [showSuccessModal, setShowSuccessModal] = useState(false);

  // 플랜명 한글 치환
  const translatePlanName = (name?: string | null) => {
    if (!name) return '';
    const map: Record<string, string> = {
      'Basic Monthly': '베이직',
      'Premium Monthly': '프리미엄',
    };
    return map[name] || name;
  };

  // 플랜 변경 유형 판단 (가격 기준)
  const getChangeType = () => {
    if (!currentPlan || !targetPlan) return 'DOWNGRADE';
    return targetPlan.monthlyPrice > currentPlan.monthlyPrice ? 'UPGRADE' : 'DOWNGRADE';
  };



  // 다음 결제일 포맷팅
  const formatNextBillingDate = () => {
    if (!userMembership?.nextBillingAt) return '';
    const date = new Date(userMembership.nextBillingAt);
    // ko-KR 포맷: "YYYY. MM. DD." → 공백 제거 및 마지막 점 제거
    const localized = date.toLocaleDateString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    });
    return localized.replace(/\.\s/g, '.').replace(/\.$/, '');
  };

  // 플랜 변경 처리
  const handlePlanChange = async () => {
    if (!targetPlan || isChanging) return;

    const changeType = getChangeType();
    
    // 업그레이드인 경우 결제 모달 표시
    if (changeType === 'UPGRADE') {
      onUpgradePayment(targetPlan);
      return;
    }

    // 다운그레이드인 경우 바로 처리
    setIsChanging(true);
    try {
      const result = await changeMembershipPlan(targetPlan.code);
      setChangeResult(result);
      
      onPlanChanged();
      onClose();
      setShowSuccessModal(true);
      
    } catch (error) {
      console.error('플랜 변경 실패:', error);
      alert('플랜 변경에 실패했습니다. 다시 시도해주세요.');
    } finally {
      setIsChanging(false);
    }
  };



  // 모달 닫기
  const handleClose = () => {
    if (isChanging) return;
    onClose();
    setChangeResult(null);
  };

  // 성공 모달 닫기
  const handleSuccessModalClose = () => {
    setShowSuccessModal(false);
    setChangeResult(null);
  };

  if (!isOpen || !currentPlan || !targetPlan) return null;

  const changeType = getChangeType();
  const nextBillingDate = formatNextBillingDate();

  return (
    <>
      <div className={styles.modalOverlay}>
        <div className={styles.modalContent}>
          {/* 닫기 버튼 */}
          <button 
            className={styles.closeButton}
            onClick={handleClose}
            disabled={isChanging}
          >
            ×
          </button>

          {/* 제목 */}
          <h2 className={styles.modalTitle}>
            {changeType === 'UPGRADE' ? (
              <>
                지금부터 <span className={styles.planNameHighlight}>{translatePlanName(targetPlan.name)}</span>을 사용하시겠어요?
              </>
            ) : (
              <>
                다음 달부터 <span className={styles.planNameHighlight}>{translatePlanName(targetPlan.name)}</span>을 사용하시겠어요?
              </>
            )}
          </h2>

          {/* 정보 목록 */}
          <div className={styles.infoList}>
            {changeType === 'UPGRADE' ? (
              <div className={styles.infoItem}>
                <div className={styles.checkIcon}>✓</div>
                <span className={styles.infoText}>
                  차액 결제 시 <span className={styles.planNameHighlight}>{translatePlanName(targetPlan.name)}</span> 혜택을 즉시 사용 가능합니다.
                </span>
              </div>
            ) : (
              <>
                <div className={styles.infoItem}>
                  <div className={styles.checkIcon}>✓</div>
                  <span className={styles.infoText}>
                    이번 달은 <span className={styles.planNameHighlight}>{translatePlanName(currentPlan.name)}</span> 혜택이 유지됩니다.
                  </span>
                </div>
                
                <div className={styles.infoItem}>
                  <div className={styles.checkIcon}>✓</div>
                  <span className={styles.infoText}>
                    {nextBillingDate}부터 <span className={styles.planNameHighlight}>{translatePlanName(targetPlan.name)}</span>으로 자동 전환됩니다.
                  </span>
                </div>
              </>
            )}
            
            {changeType === 'UPGRADE' && (
              <div className={styles.infoItem}>
                <div className={styles.checkIcon}>✓</div>
                <span className={styles.infoText}>
                  업그레이드 시 남은 기간에 대한 차액이 즉시 결제됩니다.
                </span>
              </div>
            )}
            
            <div className={styles.infoItem}>
              <div className={styles.checkIcon}>✓</div>
              <span className={styles.infoText}>
                {changeType === 'DOWNGRADE' ? (
                  <>
                    {user?.username || '사용자'} 프로필을 제외한 나머지 프로필은 비활성화 처리되며, 관련된 정보(등급, 보고싶다, 재생기록 등)을 이용할 수 없게 됩니다.
                  </>
                ) : (
                  <>
                    {user?.username || '사용자'} 프로필을 제외한 나머지 프로필이 활성화 처리되며, 관련된 정보(등급, 보고싶다, 재생기록 등)을 이용할 수 있게 됩니다.
                  </>
                )}
              </span>
            </div>
            
            <div className={styles.infoItem}>
              <div className={styles.checkIcon}>✓</div>
              <span className={styles.infoText}>
                다운로드, 소장·대여를 포함한 구매 내역, 별점, 게시글은 삭제되지 않습니다.
              </span>
            </div>
          </div>

          {/* 변경 버튼 */}
          <button
            className={styles.changeButton}
            onClick={handlePlanChange}
            disabled={isChanging}
          >
            {isChanging ? '변경 중...' : '변경하기'}
          </button>
        </div>
      </div>

      {/* 성공 알림 모달 */}
      <PlanChangeSuccessModal
        isOpen={showSuccessModal}
        onClose={handleSuccessModalClose}
        newPlan={targetPlan}
        effectiveDate={changeResult?.effectiveDate || userMembership?.nextBillingAt || ''}
      />
    </>
  );
}
