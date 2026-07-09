"use client";
import { useCallback, useEffect, useState } from "react";
import {
  createContent,
  deleteContent,
  getContents,
  setContentPublish,
  updateContent,
  type AdminContent,
  type AdminContentRequest,
  type ContentType,
} from "@/lib/api/admin";
import styles from "../admin.module.css";

type ResultState = { ok: boolean; text: string } | null;

const TYPES: { key: ContentType; label: string; hint: string }[] = [
  { key: "FAQ", label: "FAQ", hint: "자주 묻는 질문(제목=질문, 본문=답변)" },
  { key: "BENEFIT", label: "혜택", hint: "구독 혜택 소개(제목=혜택명, 본문=설명)" },
  { key: "CTA", label: "CTA", hint: "행동 유도 배너(버튼 텍스트/URL 포함)" },
];

const LOCALES = ["ko", "en"];

// 폼 초깃값 (position 은 백엔드 생성 검증상 1~5 필요 → 기본 1)
function emptyForm(type: ContentType): AdminContentRequest {
  return {
    type,
    locale: "ko",
    position: 1,
    published: false,
    title: "",
    content: "",
    actionText: "",
    actionUrl: "",
  };
}

/**
 * 관리자 CMS 콘텐츠 관리 화면 (FAQ / 혜택 / CTA)
 * - 유형 탭 전환, 로케일 필터, 목록 조회
 * - 생성 / 수정 / 삭제 / 공개 토글
 *
 * 참고: 생성 시 백엔드(AdminContent.createAdminContent)가 position 1~5, 제목/본문 필수를 검증한다.
 */
export default function AdminContentsPage() {
  const [type, setType] = useState<ContentType>("FAQ");
  const [localeFilter, setLocaleFilter] = useState<string>(""); // "" = 전체
  const [items, setItems] = useState<AdminContent[]>([]);
  const [listLoading, setListLoading] = useState(false);

  // 편집 상태: null = 폼 닫힘, editingId = null 이면 신규
  const [form, setForm] = useState<AdminContentRequest | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<ResultState>(null);

  const load = useCallback(async (t: ContentType, locale: string) => {
    setListLoading(true);
    try {
      const list = await getContents(t, locale || undefined);
      setItems(Array.isArray(list) ? list : []);
    } catch (e) {
      console.error("콘텐츠 조회 실패:", e);
      setItems([]);
    } finally {
      setListLoading(false);
    }
  }, []);

  useEffect(() => {
    load(type, localeFilter);
  }, [type, localeFilter, load]);

  const openCreate = () => {
    setEditingId(null);
    setForm(emptyForm(type));
    setResult(null);
  };

  const openEdit = (it: AdminContent) => {
    setEditingId(it.id);
    setForm({
      type: it.type,
      locale: it.locale,
      position: it.position,
      published: it.published,
      title: it.title,
      content: it.content ?? "",
      actionText: it.actionText ?? "",
      actionUrl: it.actionUrl ?? "",
    });
    setResult(null);
  };

  const closeForm = () => {
    setForm(null);
    setEditingId(null);
  };

  const handleSave = async () => {
    if (!form) return;
    if (!form.title.trim()) {
      setResult({ ok: false, text: "제목을 입력하세요." });
      return;
    }
    if (!form.content.trim()) {
      setResult({ ok: false, text: "본문을 입력하세요." });
      return;
    }
    if (editingId === null && (form.position < 1 || form.position > 5)) {
      setResult({ ok: false, text: "생성 시 순서(position)는 1~5 범위여야 합니다." });
      return;
    }
    setSaving(true);
    setResult(null);
    try {
      if (editingId === null) {
        await createContent(form);
        setResult({ ok: true, text: "생성되었습니다." });
      } else {
        await updateContent(editingId, form);
        setResult({ ok: true, text: "수정되었습니다." });
      }
      closeForm();
      load(type, localeFilter);
    } catch (e) {
      setResult({ ok: false, text: e instanceof Error ? e.message : "저장 실패" });
    } finally {
      setSaving(false);
    }
  };

  const handleTogglePublish = async (it: AdminContent) => {
    try {
      await setContentPublish(it.id, !it.published);
      load(type, localeFilter);
    } catch (e) {
      setResult({ ok: false, text: e instanceof Error ? e.message : "공개 상태 변경 실패" });
    }
  };

  const handleDelete = async (it: AdminContent) => {
    if (!window.confirm(`"${it.title}" 을(를) 삭제할까요? 되돌릴 수 없습니다.`)) return;
    try {
      await deleteContent(it.id);
      if (editingId === it.id) closeForm();
      load(type, localeFilter);
    } catch (e) {
      setResult({ ok: false, text: e instanceof Error ? e.message : "삭제 실패" });
    }
  };

  const activeType = TYPES.find((t) => t.key === type)!;
  const isCta = form?.type === "CTA";

  return (
    <div>
      <h1 className={styles.pageTitle}>콘텐츠 관리</h1>
      <p className={styles.pageSubtitle}>랜딩 페이지의 FAQ · 혜택 · CTA를 코드 수정 없이 DB로 관리합니다.</p>

      {/* 유형 탭 */}
      <div className={styles.tabs}>
        {TYPES.map((t) => (
          <button
            key={t.key}
            className={`${styles.tab} ${type === t.key ? styles.tabActive : ""}`}
            onClick={() => { setType(t.key); closeForm(); }}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/* 목록 */}
      <section className={styles.panel}>
        <div className={styles.row} style={{ justifyContent: "space-between", alignItems: "center" }}>
          <h2 className={styles.panelTitle} style={{ margin: 0 }}>{activeType.label} 목록</h2>
          <div className={styles.row}>
            <select
              className={styles.input}
              style={{ maxWidth: 120 }}
              value={localeFilter}
              onChange={(e) => setLocaleFilter(e.target.value)}
              disabled={listLoading}
            >
              <option value="">전체 언어</option>
              {LOCALES.map((l) => <option key={l} value={l}>{l}</option>)}
            </select>
            <button className={styles.button} onClick={openCreate}>+ 새로 만들기</button>
          </div>
        </div>
        <p className={styles.panelHint}>{activeType.hint}</p>
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th style={{ width: 60 }}>순서</th>
                <th style={{ width: 60 }}>언어</th>
                <th>제목</th>
                <th style={{ width: 90 }}>공개</th>
                <th style={{ width: 130 }}>관리</th>
              </tr>
            </thead>
            <tbody>
              {listLoading ? (
                <tr><td colSpan={5} style={{ textAlign: "center", color: "#9aa0aa", padding: 24 }}>불러오는 중...</td></tr>
              ) : items.length === 0 ? (
                <tr><td colSpan={5} style={{ textAlign: "center", color: "#9aa0aa", padding: 24 }}>등록된 콘텐츠가 없습니다.</td></tr>
              ) : (
                items.map((it) => (
                  <tr key={it.id}>
                    <td>{it.position}</td>
                    <td>{it.locale}</td>
                    <td>{it.title}</td>
                    <td>
                      <button
                        className={`${styles.badge} ${it.published ? styles.badgeOn : styles.badgeOff}`}
                        onClick={() => handleTogglePublish(it)}
                        title="클릭하여 공개 상태 토글"
                      >
                        {it.published ? "공개" : "비공개"}
                      </button>
                    </td>
                    <td>
                      <button className={styles.pagerBtn} onClick={() => openEdit(it)}>수정</button>{" "}
                      <button className={styles.pagerBtn} onClick={() => handleDelete(it)}>삭제</button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* 생성/수정 폼 */}
      {form && (
        <section className={styles.panel}>
          <h2 className={styles.panelTitle}>{editingId === null ? `${activeType.label} 새로 만들기` : `${activeType.label} 수정 (#${editingId})`}</h2>
          <p className={styles.panelHint}>제목·본문은 필수입니다. 생성 시 순서는 1~5 범위만 허용됩니다.</p>

          <div className={styles.formGrid}>
            <label className={styles.field}>
              <span className={styles.fieldLabel}>언어</span>
              <select
                className={styles.input}
                value={form.locale}
                onChange={(e) => setForm({ ...form, locale: e.target.value })}
              >
                {LOCALES.map((l) => <option key={l} value={l}>{l}</option>)}
              </select>
            </label>
            <label className={styles.field}>
              <span className={styles.fieldLabel}>순서 (1~5)</span>
              <input
                className={styles.input}
                type="number"
                min={1}
                max={5}
                value={form.position}
                onChange={(e) => setForm({ ...form, position: Number(e.target.value) })}
              />
            </label>
            <label className={styles.field}>
              <span className={styles.fieldLabel}>공개 여부</span>
              <select
                className={styles.input}
                value={form.published ? "1" : "0"}
                onChange={(e) => setForm({ ...form, published: e.target.value === "1" })}
              >
                <option value="0">비공개</option>
                <option value="1">공개</option>
              </select>
            </label>
          </div>

          <label className={styles.field} style={{ marginTop: 12 }}>
            <span className={styles.fieldLabel}>제목</span>
            <input
              className={styles.input}
              style={{ width: "100%" }}
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
              placeholder={type === "FAQ" ? "질문" : "제목"}
            />
          </label>

          <label className={styles.field} style={{ marginTop: 12 }}>
            <span className={styles.fieldLabel}>본문</span>
            <textarea
              className={styles.textarea}
              value={form.content}
              onChange={(e) => setForm({ ...form, content: e.target.value })}
              placeholder={type === "FAQ" ? "답변" : "설명"}
              rows={5}
            />
          </label>

          {isCta && (
            <div className={styles.formGrid} style={{ marginTop: 12 }}>
              <label className={styles.field}>
                <span className={styles.fieldLabel}>버튼 텍스트</span>
                <input
                  className={styles.input}
                  style={{ width: "100%" }}
                  value={form.actionText ?? ""}
                  onChange={(e) => setForm({ ...form, actionText: e.target.value })}
                  placeholder="예: 지금 시작하기"
                />
              </label>
              <label className={styles.field}>
                <span className={styles.fieldLabel}>버튼 URL</span>
                <input
                  className={styles.input}
                  style={{ width: "100%" }}
                  value={form.actionUrl ?? ""}
                  onChange={(e) => setForm({ ...form, actionUrl: e.target.value })}
                  placeholder="예: /membership"
                />
              </label>
            </div>
          )}

          <div className={styles.row} style={{ marginTop: 16 }}>
            <button className={styles.button} onClick={handleSave} disabled={saving}>
              {saving ? "저장 중..." : "저장"}
            </button>
            <button className={styles.pagerBtn} onClick={closeForm} disabled={saving}>취소</button>
          </div>
        </section>
      )}

      {result && (
        <div className={`${styles.result} ${result.ok ? styles.resultOk : styles.resultErr}`}>
          {result.text}
        </div>
      )}
    </div>
  );
}
