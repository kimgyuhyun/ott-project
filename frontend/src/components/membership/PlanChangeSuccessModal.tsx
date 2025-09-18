"use client";
import { useEffect } from "react";
import { MembershipPlan } from "@/lib/api/membership";
import styles from "./PlanChangeSuccessModal.module.css";

interface PlanChangeSuccessModalProps {
  isOpen: boolean;
  onClose: () => void;
  newPlan: MembershipPlan | null;
  effectiveDate: string;
}

export default function PlanChangeSuccessModal({
  isOpen,
  onClose,
  newPlan,
  effectiveDate
}: PlanChangeSuccessModalProps) {
  
  // 3초 후 자동으로 모달 닫기
  useEffect(() => {
    if (isOpen) {
      const timer = setTimeout(() => {
        onClose();
      }, 3000);
      
      return () => clearTimeout(timer);
    }
    return () => {};
  }, [isOpen, onClose]);

  if (!isOpen || !newPlan) return null;

  // 플랜 이름 한국어 매핑
  const translatePlanName = (name: string) => {
    const map: Record<string, string> = {
      'Basic Monthly': '베이직',
      'Premium Monthly': '프리미엄',
    };
    return map[name] || name;
  };

  // 적용일 포맷팅
  const formatEffectiveDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('ko-KR', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    }).replace(/\. /g, '.').replace('.', '');
  };

  const planName = translatePlanName(newPlan.name);
  const formattedDate = formatEffectiveDate(effectiveDate);

  return (
    <div className={styles.modalOverlay}>
      <div className={styles.modalContent}>
        {/* 성공 아이콘 */}
        <div className={styles.successIcon}>
          ✅
        </div>

        {/* 성공 메시지 */}
        <h2 className={styles.successTitle}>
          플랜 변경이 완료되었습니다!
        </h2>

        {/* 상세 정보 */}
        <div className={styles.successMessage}>
          <p className={styles.mainMessage}>
            다음달부터 <span className={styles.planName}>{planName}멤버십</span>을 사용합니다
          </p>
          <p className={styles.dateMessage}>
            적용일: {formattedDate}
          </p>
        </div>

        {/* 확인 버튼 */}
        <button
          className={styles.confirmButton}
          onClick={onClose}
        >
          확인
        </button>
      </div>
    </div>
  );
}
