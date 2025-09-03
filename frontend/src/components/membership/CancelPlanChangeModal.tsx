"use client";
import styles from "./CancelPlanChangeModal.module.css";

interface CancelPlanChangeModalProps {
  isOpen: boolean;
  nextPlanName: string | null | undefined;
  currentPlanName: string | null | undefined;
  onCancel: () => void;
  onConfirm: () => void;
  isProcessing?: boolean;
}

export default function CancelPlanChangeModal({
  isOpen,
  nextPlanName,
  currentPlanName,
  onCancel,
  onConfirm,
  isProcessing,
}: CancelPlanChangeModalProps) {
  if (!isOpen) return null;

  return (
    <div className={styles.overlay}>
      <div className={styles.container}>
        <h3 className={styles.title}>
          {currentPlanName || "현재 플랜"}으로 변경하시겠어요?
        </h3>
        <p className={styles.desc}>
          {nextPlanName || "다음 플랜"} 자동전환을 취소하고 현재 플랜을 계속 이용하게 됩니다.
        </p>
        <div className={styles.actions}>
          <button className={styles.cancelBtn} onClick={onCancel}>아니요</button>
          <button className={styles.confirmBtn} onClick={onConfirm} disabled={!!isProcessing}>
            {isProcessing ? '처리 중...' : '네, 취소할게요'}
          </button>
        </div>
      </div>
    </div>
  );
}


