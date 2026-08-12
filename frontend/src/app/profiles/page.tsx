"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Image from "next/image";
import {
  MAX_PROFILES,
  ViewingProfile,
  createProfile,
  deleteProfile,
  getMyProfiles,
  renameProfile,
  selectProfile,
} from "@/lib/api/profiles";
import { getErrorMessage, getErrorStatus } from "@/lib/errorMessage";
import styles from "./profiles.module.css";

/**
 * 서버가 돌려준 에러 본문에서 사람이 읽을 메시지만 꺼낸다.
 * api 헬퍼는 응답 본문을 문자열 그대로 실어 주므로, 그대로 쓰면 화면에 JSON 이 노출된다.
 */
function readableError(e: unknown): string {
  const raw = getErrorMessage(e);
  if (!raw) return "요청을 처리하지 못했습니다.";
  try {
    const parsed = JSON.parse(raw) as { message?: string };
    if (parsed?.message) return parsed.message;
  } catch {
    // JSON 이 아니면 원문을 그대로 쓴다.
  }
  return raw;
}

/**
 * 프로필 선택 화면
 *
 * 로그인 후 사용할 시청 프로필을 고른다. 편집 모드에서는 이름 변경과 삭제를 한다.
 *
 * 주의: 선택한 프로필은 아직 화면 상태일 뿐이다. 시청기록·찜·별점은 계정 단위로 저장되므로
 * 어떤 프로필을 골라도 같은 데이터가 보인다. 그래서 화면 아래에 그 사실을 적어 둔다.
 */
export default function ProfilesPage() {
  const router = useRouter();
  const [profiles, setProfiles] = useState<ViewingProfile[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isEditing, setIsEditing] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [isAdding, setIsAdding] = useState(false);
  const [draftName, setDraftName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [isBusy, setIsBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setProfiles(await getMyProfiles());
    } catch (e) {
      const status = getErrorStatus(e);
      if (status === 401) {
        router.replace("/login");
        return;
      }
      setError(readableError(e));
    } finally {
      setIsLoading(false);
    }
  }, [router]);

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", "dark");
    load();
  }, [load]);

  const handleSelect = async (profileId: number) => {
    if (isBusy) return;
    setIsBusy(true);
    setError(null);
    try {
      await selectProfile(profileId);
      router.push("/");
    } catch (e) {
      setError(readableError(e));
      setIsBusy(false);
    }
  };

  const handleCreate = async () => {
    if (isBusy) return;
    setIsBusy(true);
    setError(null);
    try {
      setProfiles([...profiles, await createProfile(draftName)]);
      setIsAdding(false);
      setDraftName("");
    } catch (e) {
      setError(readableError(e));
    } finally {
      setIsBusy(false);
    }
  };

  const handleRename = async (profileId: number) => {
    if (isBusy) return;
    setIsBusy(true);
    setError(null);
    try {
      const updated = await renameProfile(profileId, draftName);
      setProfiles(profiles.map((p) => (p.id === profileId ? updated : p)));
      setEditingId(null);
      setDraftName("");
    } catch (e) {
      setError(readableError(e));
    } finally {
      setIsBusy(false);
    }
  };

  const handleDelete = async (profileId: number) => {
    if (isBusy) return;
    setIsBusy(true);
    setError(null);
    try {
      await deleteProfile(profileId);
      setProfiles(profiles.filter((p) => p.id !== profileId));
    } catch (e) {
      setError(readableError(e));
    } finally {
      setIsBusy(false);
    }
  };

  const startEditingName = (profile: ViewingProfile) => {
    setEditingId(profile.id);
    setDraftName(profile.name);
  };

  const toggleEditMode = () => {
    setIsEditing(!isEditing);
    setEditingId(null);
    setIsAdding(false);
    setDraftName("");
    setError(null);
  };

  if (isLoading) {
    return (
      <div className={styles.page}>
        <p className={styles.label}>프로필을 불러오는 중…</p>
      </div>
    );
  }

  return (
    <div className={styles.page}>
      <p className={styles.label}>프로필 선택</p>
      <h1 className={styles.title}>
        {isEditing ? "프로필을 편집해주세요." : "사용할 프로필을 선택해주세요."}
      </h1>

      <p className={styles.message}>{error}</p>

      <div className={styles.profileRow}>
        {profiles.map((profile) => (
          <div key={profile.id} className={styles.profileItem}>
            <button
              type="button"
              className={styles.avatarButton}
              disabled={isBusy}
              onClick={() => (isEditing ? startEditingName(profile) : handleSelect(profile.id))}
              aria-label={isEditing ? `${profile.name} 이름 변경` : `${profile.name} 프로필 사용`}
            >
              <Image
                src="/icons/default-avatar.png"
                alt=""
                width={172}
                height={172}
                className={styles.avatarImage}
              />
            </button>

            {isEditing && (
              <button
                type="button"
                className={styles.deleteButton}
                onClick={() => handleDelete(profile.id)}
                disabled={isBusy || profiles.length <= 1}
                title={profiles.length <= 1 ? "마지막 프로필은 삭제할 수 없습니다." : "프로필 삭제"}
                aria-label={`${profile.name} 프로필 삭제`}
              >
                ×
              </button>
            )}

            {editingId === profile.id ? (
              <input
                className={styles.nameInput}
                value={draftName}
                maxLength={20}
                autoFocus
                onChange={(e) => setDraftName(e.target.value)}
                onBlur={() => handleRename(profile.id)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") handleRename(profile.id);
                  if (e.key === "Escape") {
                    setEditingId(null);
                    setDraftName("");
                  }
                }}
              />
            ) : (
              <p className={styles.profileName}>{profile.name}</p>
            )}
          </div>
        ))}

        {profiles.length < MAX_PROFILES && (
          <div className={styles.profileItem}>
            {isAdding ? (
              <>
                <div className={`${styles.avatarButton} ${styles.addButton}`}>+</div>
                <input
                  className={styles.nameInput}
                  value={draftName}
                  maxLength={20}
                  autoFocus
                  placeholder="프로필 이름"
                  onChange={(e) => setDraftName(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") handleCreate();
                    if (e.key === "Escape") {
                      setIsAdding(false);
                      setDraftName("");
                    }
                  }}
                />
              </>
            ) : (
              <>
                <button
                  type="button"
                  className={`${styles.avatarButton} ${styles.addButton}`}
                  onClick={() => {
                    setIsAdding(true);
                    setDraftName("");
                  }}
                  disabled={isBusy}
                  aria-label="새 프로필 만들기"
                >
                  +
                </button>
                <p className={styles.profileName}>새 프로필</p>
              </>
            )}
          </div>
        )}
      </div>

      <button type="button" className={styles.editButton} onClick={toggleEditMode}>
        {isEditing ? "완료" : "프로필 편집"}
      </button>

      <p className={styles.notice}>
        지금은 프로필을 골라도 시청기록·보고싶다·별점은 계정 하나로 함께 쌓입니다.
        프로필별로 나누는 작업은 아직입니다.
      </p>
    </div>
  );
}
