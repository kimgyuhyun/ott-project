import React, { useMemo, useState } from 'react';
import styles from './CancelMembershipModal.module.css';

type CancelMembershipModalProps = {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => Promise<void> | void;
  planName: string; // '프리미엄' | '베이직'
  primaryProfileName?: string | null;
};

const CancelMembershipModal: React.FC<CancelMembershipModalProps> = ({
  isOpen,
  onClose,
  onConfirm,
  planName,
  primaryProfileName,
}) => {
  const [isSubmitting, setIsSubmitting] = useState(false);

  const isPremium = useMemo(() => planName === '프리미엄', [planName]);
  const benefitItems = useMemo(() => {
    if (isPremium) {
      return [
        '프로필 4인 · 동시재생 4회선',
        '최신화 시청',
        '다운로드 지원',
        'FHD/4K 화질(서비스 규격에 맞게)',
        'TV 앱 지원',
      ];
    }
    return [
      '프로필 1인 · 동시재생 1회선',
      '최신화 시청',
      '다운로드 미지원 또는 제한',
      'HD 화질',
      '모바일/웹 시청(실제 기획에 맞게 요약)',
    ];
  }, [isPremium]);

  const handleConfirm = async () => {
    if (isSubmitting) return;
    setIsSubmitting(true);
    try {
      await onConfirm();
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className={styles.overlay} role="dialog" aria-modal="true" aria-labelledby="cancel-membership-title">
      <div className={styles.container}>
        <h2 id="cancel-membership-title" className={styles.title}>멤버십 해지하기</h2>
        <p className={styles.desc}>
          멤버십을 해지하면 등급이 초기화되고 아래 혜택들과 프로필 정보를 이용할 수 없게 돼요. 그래도 해지하시겠어요?
        </p>

        <div className={styles.benefitCard} aria-label={`${planName} 혜택`}>
          <h3 className={styles.planTitle}>{planName}</h3>
          <ul>
            {benefitItems.map((text) => (
              <li key={text}>{text}</li>
            ))}
          </ul>
        </div>

        {isPremium && (
          <div className={styles.profileNotice}>
            <p>
              [{primaryProfileName ?? '대표 프로필'}] 대표 프로필만 남게 돼요.
            </p>
            <p>
              위 프로필을 제외한 나머지 프로필은 비활성화 처리되며, 관련된 정보(등급, 보고싶다, 재생기록 등)을 이용할 수 없게 됩니다.
            </p>
          </div>
        )}

        <p className={styles.purchaseNotice}>
          다운로드, 소장·대여를 포함한 구매내역, 별점, 게시글은 삭제되지 않습니다.
        </p>

        <div className={styles.actions}>
          <button type="button" className={styles.primaryBtn} onClick={onClose} disabled={isSubmitting}>
            멤버십 계속 이용하기
          </button>
          <button
            type="button"
            className={isSubmitting ? styles.disabled : styles.secondaryBtn}
            onClick={handleConfirm}
            disabled={isSubmitting}
            aria-busy={isSubmitting}
          >
            {isSubmitting ? '처리 중...' : '멤버십 해지하기'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default CancelMembershipModal;


