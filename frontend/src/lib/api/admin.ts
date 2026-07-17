import { api } from "./index";

// 관리자 전용 API 함수들
// 백엔드: AdminAnimeController (/api/admin/anime/**), ROLE_ADMIN 전용

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

/**
 * 애니메이션 데이터 TMDB 보강 (전체)
 * POST /api/admin/anime/enhance-all
 * - 백엔드가 @Async 라 "작업 시작"만 알리고 즉시 반환한다(진행 상황은 서버 로그).
 * - 응답이 평문(text/plain)이라 api 헬퍼는 null 을 돌려주므로 반환값은 사용하지 않는다.
 */
export async function enhanceAllAnime(): Promise<void> {
  await api.post<void>(`/admin/anime/enhance-all`);
}

// ===== 통계 / 감사 로그 =====
// 백엔드: AdminStatsController (/api/admin/stats/**), ROLE_ADMIN 전용

// 일일 통계 스냅샷 (DailyStats)
export interface DailyStats {
  id: number;
  statDate: string; // yyyy-MM-dd
  loginSuccessCount: number;
  loginFailCount: number;
  logoutCount: number;
  signupCount: number;
  activeUserCount: number; // DAU
  createdAt: string;
  updatedAt: string;
}

// 인증 이벤트 (AuthEvent, 감사 로그)
export interface AuthEvent {
  id: number;
  userId: number | null;
  email: string | null;
  eventType: "LOGIN_SUCCESS" | "LOGIN_FAIL" | "LOGOUT" | "SESSION_EXPIRED" | "WITHDRAW";
  provider: "LOCAL" | "GOOGLE" | "NAVER" | "KAKAO" | null;
  ipAddress: string | null;
  userAgent: string | null;
  sessionId: string | null;
  failReason: string | null;
  occurredAt: string;
}

/**
 * 최근 N일 일일 통계 목록 (오름차순)
 * GET /api/admin/stats/daily?days=
 */
export async function getDailyStats(days: number = 30): Promise<DailyStats[]> {
  return api.get<DailyStats[]>(`/admin/stats/daily?days=${days}`);
}

/**
 * 특정 일자 스냅샷 수동 재집계 (멱등)
 * POST /api/admin/stats/daily/rebuild?date=yyyy-MM-dd
 */
export async function rebuildDailyStats(date: string): Promise<DailyStats> {
  return api.post<DailyStats>(`/admin/stats/daily/rebuild?date=${date}`);
}

/**
 * 최근 인증 이벤트 100건 (최신순)
 * GET /api/admin/stats/auth-events
 */
export async function getRecentAuthEvents(): Promise<AuthEvent[]> {
  return api.get<AuthEvent[]>(`/admin/stats/auth-events`);
}

// ===== CMS 콘텐츠 관리 (FAQ / 혜택 / CTA) =====
// 백엔드: AdminContentController (/api/admin/contents/**), ROLE_ADMIN 전용

export type ContentType = "FAQ" | "BENEFIT" | "CTA";

// 관리자 콘텐츠 응답 (AdminContentResponseDto)
export interface AdminContent {
  id: number;
  type: ContentType;
  locale: string; // ko | en
  position: number;
  published: boolean;
  title: string;
  content: string | null;
  actionText: string | null;
  actionUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

// 생성/수정 요청 (AdminContentRequestDto)
export interface AdminContentRequest {
  type: ContentType;
  locale: string;
  position: number;
  published: boolean;
  title: string;
  content: string;
  actionText?: string | null;
  actionUrl?: string | null;
}

/**
 * 관리자용 콘텐츠 목록 (locale 미지정 시 전체 로케일)
 * GET /api/admin/contents?type=&locale=
 */
export async function getContents(type: ContentType, locale?: string): Promise<AdminContent[]> {
  const q = locale ? `?type=${type}&locale=${locale}` : `?type=${type}`;
  return api.get<AdminContent[]>(`/admin/contents${q}`);
}

/**
 * 콘텐츠 생성
 * POST /api/admin/contents
 */
export async function createContent(dto: AdminContentRequest): Promise<AdminContent> {
  return api.post<AdminContent>(`/admin/contents`, dto);
}

/**
 * 콘텐츠 수정 (부분 수정: 전달된 필드만 반영)
 * PUT /api/admin/contents/{id}
 */
export async function updateContent(id: number, dto: Partial<AdminContentRequest>): Promise<AdminContent> {
  return api.put<AdminContent>(`/admin/contents/${id}`, dto);
}

/**
 * 콘텐츠 삭제
 * DELETE /api/admin/contents/{id}
 */
export async function deleteContent(id: number): Promise<void> {
  return api.delete<void>(`/admin/contents/${id}`);
}

/**
 * 공개 여부 토글
 * PUT /api/admin/contents/{id}/publish?value=
 */
export async function setContentPublish(id: number, value: boolean): Promise<AdminContent> {
  return api.put<AdminContent>(`/admin/contents/${id}/publish?value=${value}`);
}

/**
 * 노출 순서 변경
 * PUT /api/admin/contents/{id}/position?position=
 */
export async function setContentPosition(id: number, position: number): Promise<AdminContent> {
  return api.put<AdminContent>(`/admin/contents/${id}/position?position=${position}`);
}
