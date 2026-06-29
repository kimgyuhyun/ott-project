import { api } from "./index";

// 관리자 전용 API 함수들
// 백엔드: SimpleAdminAnimeSyncController (/api/admin/anime/**), ROLE_ADMIN 전용

// 단일 동기화 결과 (SyncResult)
export interface SyncResult {
  success: boolean;
  message: string;
  malId: number;
}

// 일괄 동기화 통계 (CollectionResult)
export interface CollectionStatistics {
  [key: string]: unknown;
}

// 일괄 동기화 결과 (BulkSyncResult)
export interface BulkSyncResult {
  success: boolean;
  message: string;
  statistics: CollectionStatistics | null;
}

/**
 * 단일 애니메이션 동기화 (Jikan MAL ID 기준)
 * POST /api/admin/anime/sync/{malId}
 */
export async function syncAnime(malId: number): Promise<SyncResult> {
  return api.post<SyncResult>(`/admin/anime/sync/${malId}`);
}

/**
 * 인기 애니메이션 일괄 동기화
 * POST /api/admin/anime/sync-popular?limit=
 */
export async function syncPopularAnime(limit: number = 50): Promise<BulkSyncResult> {
  return api.post<BulkSyncResult>(`/admin/anime/sync-popular?limit=${limit}`);
}
