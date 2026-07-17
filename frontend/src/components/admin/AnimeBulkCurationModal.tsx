"use client";
import { useState } from "react";
import { previewBulkCuration, applyBulkCuration } from "@/lib/api/admin";
import type {
  AnimeBulkCurationPreview,
  AnimeBulkCurationRequest,
  AnimeCurationSearchCondition,
} from "@/lib/api/admin";
import styles from "@/app/admin/admin.module.css";

interface Props {
  /** 지금 목록에 걸려 있는 검색 조건. 운영자가 눈으로 본 목록이 곧 수정 대상이 된다. */
  condition: AnimeCurationSearchCondition;
  /** 조건에 걸린 전체 건수(목록의 total). 미리보기 전 대략의 규모를 보여주는 용도. */
  matchedCount: number;
  onClose: () => void;
  onApplied: (affectedCount: number) => void;
}

/** 벌크로 바꿀 수 있는 값. ""(유지)는 요청에서 아예 뺀다. */
type TriState = "" | "true" | "false";

/**
 * 조건 기반 벌크 큐레이션 모달
 *
 * 흐름이 2단계인 것은 UI 취향이 아니라 백엔드 계약이다.
 * - 실행에는 expectedCount 가 필요하고, 서버가 실행 직전 다시 세어 다르면 409 를 낸다.
 *   미리보기와 실행 사이에 동기화 배치가 대상을 늘릴 수 있어서, 운영자가 승인한 건수와
 *   실제로 바뀌는 건수가 어긋나는 걸 막는 장치다.
 * - 그래서 "미리보기 없이 바로 실행"이 존재할 수 없고, 409 를 받으면 미리보기부터 다시 해야 한다.
 *
 * 대상 조건을 이 모달에서 따로 고르지 않고 목록의 검색 조건을 그대로 쓰는 이유
 * - 운영자가 방금 눈으로 확인한 목록과 수정 대상이 항상 같아진다.
 * - 조건 없는 벌크(백엔드가 400 으로 거부)를 구조적으로 시도할 수 없게 된다.
 */
export default function AnimeBulkCurationModal({ condition, matchedCount, onClose, onApplied }: Props) {
  const [isActive, setIsActive] = useState<TriState>("");
  const [isExclusive, setIsExclusive] = useState<TriState>("");
  const [isPopular, setIsPopular] = useState<TriState>("");
  const [isNew, setIsNew] = useState<TriState>("");

  const [preview, setPreview] = useState<AnimeBulkCurationPreview | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const buildChanges = () => {
    const changes: Omit<AnimeBulkCurationRequest, "condition" | "expectedCount"> = {};
    if (isActive) changes.isActive = isActive === "true";
    if (isExclusive) changes.isExclusive = isExclusive === "true";
    if (isPopular) changes.isPopular = isPopular === "true";
    if (isNew) changes.isNew = isNew === "true";
    return changes;
  };

  const hasChanges = Object.keys(buildChanges()).length > 0;

  const handlePreview = async () => {
    setLoading(true);
    setError(null);
    try {
      setPreview(await previewBulkCuration(condition));
    } catch (e) {
      setError(e instanceof Error ? e.message : "미리보기에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  const handleApply = async () => {
    if (!preview) return;
    setLoading(true);
    setError(null);
    try {
      const res = await applyBulkCuration({
        condition,
        ...buildChanges(),
        expectedCount: preview.affectedCount, // 방금 운영자가 확인한 건수를 그대로 되돌려 보낸다
      });
      onApplied(res.affectedCount);
    } catch (e) {
      const status = (e as { response?: { status?: number } })?.response?.status;
      if (status === 409) {
        // 승인한 건수와 실제 대상이 달라졌다. 새 건수를 다시 확인시켜야 하므로 1단계로 되돌린다.
        setPreview(null);
        setError("미리보기 이후 대상 건수가 바뀌었습니다. 영향 범위를 다시 확인해 주세요.");
      } else {
        setError(e instanceof Error ? e.message : "일괄 수정에 실패했습니다.");
      }
    } finally {
      setLoading(false);
    }
  };

  const changeSummary = () => {
    const labels: string[] = [];
    if (isActive) labels.push(isActive === "true" ? "노출로 변경" : "숨김으로 변경");
    if (isExclusive) labels.push(isExclusive === "true" ? "독점 켜기" : "독점 끄기");
    if (isPopular) labels.push(isPopular === "true" ? "인기 켜기" : "인기 끄기");
    if (isNew) labels.push(isNew === "true" ? "신작 켜기" : "신작 끄기");
    return labels.join(", ");
  };

  const renderSelect = (label: string, value: TriState, onChange: (v: TriState) => void,
                        onText: string, offText: string) => (
    <div className={styles.modalField}>
      <label className={styles.filterLabel}>{label}</label>
      <select
        className={styles.select}
        value={value}
        onChange={(e) => { onChange(e.target.value as TriState); setPreview(null); }}
        disabled={loading}
      >
        <option value="">변경하지 않음</option>
        <option value="true">{onText}</option>
        <option value="false">{offText}</option>
      </select>
    </div>
  );

  return (
    <div className={styles.modalOverlay} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <h3 className={styles.modalTitle}>조건 일괄 수정</h3>
        <p className={styles.modalHint}>
          지금 검색 조건에 걸린 <strong>{matchedCount.toLocaleString()}건</strong>이 대상입니다.
          대상을 좁히려면 닫고 검색 조건을 바꾸세요. 제목·포스터는 일괄 수정 대상이 아닙니다.
        </p>

        {renderSelect("노출 여부", isActive, setIsActive, "사용자에게 노출", "숨김")}
        {renderSelect("독점", isExclusive, setIsExclusive, "켜기", "끄기")}
        {renderSelect("인기", isPopular, setIsPopular, "켜기", "끄기")}
        {renderSelect("신작", isNew, setIsNew, "켜기", "끄기")}

        {error && <div className={`${styles.result} ${styles.resultErr}`}>{error}</div>}

        {preview && (
          <div className={styles.result} style={{ background: "#0e0f13", border: "1px solid #2c2f38", color: "#c7cad1" }}>
            <div>
              <span className={styles.previewCount}>{preview.affectedCount.toLocaleString()}건</span>
              <span style={{ marginLeft: 8 }}>이 다음과 같이 바뀝니다 — {changeSummary()}</span>
            </div>
            {preview.sample.length > 0 && (
              <div className={styles.sampleList}>
                {preview.sample.map((it) => (
                  <div key={it.id}>· {it.title || it.titleEn || `(제목 없음) ID ${it.id}`}</div>
                ))}
                {preview.affectedCount > preview.sample.length && (
                  <div style={{ color: "#9aa0aa" }}>
                    … 외 {(preview.affectedCount - preview.sample.length).toLocaleString()}건
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        <div className={styles.modalActions}>
          <button className={styles.pagerBtn} onClick={onClose} disabled={loading}>취소</button>
          {!preview ? (
            <button className={styles.button} onClick={handlePreview} disabled={loading || !hasChanges}>
              {loading ? "확인 중..." : "영향 범위 확인"}
            </button>
          ) : (
            // 실행 버튼은 건수를 눈으로 확인한 뒤에만 나타난다
            <button className={styles.dangerButton} onClick={handleApply} disabled={loading}>
              {loading ? "적용 중..." : `${preview.affectedCount.toLocaleString()}건 수정 실행`}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
