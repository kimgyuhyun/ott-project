import { api } from "./index";
import type { PagedResponse } from "@/types/common";
import type { AnimeStatus } from "@/types/anime";

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

// ===== 애니 큐레이션 =====
// 백엔드: AdminAnimeController (/api/admin/anime/**), ROLE_ADMIN 전용
//
// 사용자향 /api/anime 와 다른 점(관리자 목록이 이쪽을 써야 하는 이유)
// - /api/anime 는 WHERE is_active = TRUE 가 하드코딩돼 있어 비활성 작품이 아예 안 보인다.
//   그 목록으로는 비활성화한 작품을 되돌릴 수 없다.
// - isActive/curated/malId 처럼 운영자가 판단 근거로 삼는 값을 여기서만 내려준다.

// 유입 경로 (enum SyncOrigin) — 전용 컬럼이 아니라 malId 유무에서 파생된다
export type SyncOrigin = "JIKAN" | "MANUAL";

// 큐레이션 목록 항목 (AdminAnimeListItemDto)
// 줄거리는 없다 — fullSynopsis 가 TEXT 라 목록에 실으면 응답이 본문 덩어리가 된다.
// 수정 폼은 getAnimeForCuration 으로 상세를 따로 받는다.
export interface AdminAnimeItem {
  id: number;
  malId: number | null;
  title: string | null;
  titleEn: string | null;
  titleJp: string | null;
  posterUrl: string | null;
  status: AnimeStatus;
  year: number | null;
  isActive: boolean;
  isExclusive: boolean;
  isPopular: boolean;
  isNew: boolean;
  isCompleted: boolean;
  isSubtitle: boolean;
  isDub: boolean;
  isSimulcast: boolean;
  curated: boolean;
  syncOrigin: SyncOrigin;
  updatedAt: string;
}

// 큐레이션 단건 상세 (AdminAnimeDetailDto) — 수정 폼이 채워야 하는 값 전부
export interface AdminAnimeDetail extends AdminAnimeItem {
  synopsis: string | null;
  fullSynopsis: string | null;
  backdropUrl: string | null;
}

// 검색 조건 (AnimeCurationSearchCondition) — 모든 필드가 선택이며 자유 조합된다
export interface AnimeCurationSearchCondition {
  titleKeyword?: string;   // 한/영/일 제목 통합 부분일치
  status?: AnimeStatus;
  year?: number;
  isActive?: boolean;
  isExclusive?: boolean;
  isPopular?: boolean;
  isNew?: boolean;
  curated?: boolean;
  syncOrigin?: SyncOrigin;
}

// 단건 수정 요청 (AnimeCurationUpdateRequest)
// 전달하지 않은 필드는 변경되지 않는다(부분 수정). 그래서 false 는 "끄기"라는 실제 값이다.
export interface AnimeCurationUpdateRequest {
  // 콘텐츠 — TMDB 보강이 덮어쓰는 필드와 같은 집합. 실제로 바뀌면 백엔드가 curated 를 켠다.
  title?: string;
  titleEn?: string;
  titleJp?: string;
  synopsis?: string;
  fullSynopsis?: string;
  posterUrl?: string;
  backdropUrl?: string;
  // 배지 — 수집 시 하드코딩/휴리스틱으로 찍힌 값이라 사람이 바로잡는다(isDub 은 평점으로 추측된다)
  isExclusive?: boolean;
  isPopular?: boolean;
  isNew?: boolean;
  isCompleted?: boolean;
  isSubtitle?: boolean;
  isDub?: boolean;
  isSimulcast?: boolean;
  isActive?: boolean;
}

// 벌크 미리보기 응답 (AnimeBulkCurationPreviewResponse)
export interface AnimeBulkCurationPreview {
  affectedCount: number;
  sample: AdminAnimeItem[];
}

// 벌크 수정 요청 (AnimeBulkCurationRequest)
// 제목/포스터는 대상이 아니다(여러 작품을 같은 제목으로 만드는 건 의미가 없다).
export interface AnimeBulkCurationRequest {
  condition: AnimeCurationSearchCondition;
  isActive?: boolean;
  isExclusive?: boolean;
  isPopular?: boolean;
  isNew?: boolean;
  isCompleted?: boolean;
  isSubtitle?: boolean;
  isDub?: boolean;
  isSimulcast?: boolean;
  expectedCount: number;
}

// 벌크 수정 결과 (BulkCurationResult)
export interface BulkCurationResult {
  affectedCount: number;
}

/**
 * 조건 객체를 쿼리스트링으로 편다.
 * 값이 없는 조건은 아예 빼야 한다 — 백엔드는 파라미터 부재를 "이 조건은 걸지 않음"으로 읽는다.
 */
function toQueryString(condition: AnimeCurationSearchCondition, page: number, size: number): string {
  const params = new URLSearchParams();
  Object.entries(condition).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      params.append(key, String(value));
    }
  });
  params.append("page", String(page));
  params.append("size", String(size));
  return params.toString();
}

/**
 * 큐레이션 검색
 * GET /api/admin/anime/search
 * - 조건을 하나도 주지 않으면 전체 목록이다(검색에서는 정상).
 */
export async function searchAnimeForCuration(
  condition: AnimeCurationSearchCondition = {},
  page: number = 0,
  size: number = 20,
): Promise<PagedResponse<AdminAnimeItem>> {
  return api.get<PagedResponse<AdminAnimeItem>>(
    `/admin/anime/search?${toQueryString(condition, page, size)}`,
  );
}

/**
 * 큐레이션 단건 조회 (수정 폼용)
 * GET /api/admin/anime/{animeId}
 * - 목록에 없는 줄거리/배경이미지까지 준다. 수정 폼은 목록 항목이 아니라 이걸로 채워야 한다.
 */
export async function getAnimeForCuration(animeId: number): Promise<AdminAnimeDetail> {
  return api.get<AdminAnimeDetail>(`/admin/anime/${animeId}`);
}

/**
 * 큐레이션 단건 수정
 * PATCH /api/admin/anime/{animeId}
 * - 콘텐츠(제목/줄거리/이미지)를 실제로 바꾸면 백엔드가 curated 를 켠다
 *   → 이후 TMDB 자동 보강이 그 작품을 통째로 건너뛴다. 그래서 보강이 채우던 값도 여기서 관리해야 한다.
 */
export async function updateAnimeCuration(
  animeId: number,
  request: AnimeCurationUpdateRequest,
): Promise<AdminAnimeDetail> {
  return api.patch<AdminAnimeDetail>(`/admin/anime/${animeId}`, request);
}

/**
 * 벌크 수정 미리보기
 * POST /api/admin/anime/bulk/preview
 * - 조건 없이 호출하면 400. 여기서 받은 affectedCount 를 그대로 applyBulkCuration 의 expectedCount 로 넘겨야 한다.
 */
export async function previewBulkCuration(
  condition: AnimeCurationSearchCondition,
): Promise<AnimeBulkCurationPreview> {
  return api.post<AnimeBulkCurationPreview>(`/admin/anime/bulk/preview`, condition);
}

/**
 * 조건 기반 벌크 수정
 * PATCH /api/admin/anime/bulk
 *
 * 미리보기를 먼저 호출해야 한다 — expectedCount 가 실행 시점의 실제 대상 건수와 다르면 백엔드가 409 를 낸다.
 * (미리보기와 실행 사이에 동기화 배치가 대상을 늘릴 수 있고, 그러면 운영자가 승인한 것보다 많이 바뀐다)
 * 409 를 받으면 미리보기부터 다시 해서 새 건수를 확인시켜야 한다.
 */
export async function applyBulkCuration(
  request: AnimeBulkCurationRequest,
): Promise<BulkCurationResult> {
  return api.patch<BulkCurationResult>(`/admin/anime/bulk`, request);
}

// ===== 에피소드 관리 =====
// 백엔드: AdminEpisodeController (/api/admin/animes/**), ROLE_ADMIN 전용
// 경로가 /admin/anime(단수)가 아니라 /admin/animes(복수)다 — 컨트롤러가 그렇게 선언돼 있다.

// 에피소드 단건 (AdminEpisodeDetailDto)
export interface AdminEpisode {
  id: number;
  animeId: number;
  episodeNumber: number;
  title: string;
  thumbnailUrl: string;
  videoUrl: string;
  isActive: boolean;
  isReleased: boolean;
}

// 에피소드 부분 수정 요청 (EpisodeUpdateRequest)
// 보내지 않은(undefined) 필드는 백엔드가 건드리지 않는다. 빈 문자열은 400 이다(not-null 컬럼).
// episodeNumber 는 수정 대상이 아니다 — 시청 기록·진행률·댓글이 에피소드에 붙어 있다.
export interface EpisodeUpdateRequest {
  title?: string;
  thumbnailUrl?: string;
  videoUrl?: string;
  isActive?: boolean;
  isReleased?: boolean;
}

/**
 * 작품의 화수 목록 조회
 * GET /api/admin/animes/{animeId}/episodes
 * - 화수 오름차순으로 온다.
 */
export async function listEpisodesForAdmin(animeId: number): Promise<AdminEpisode[]> {
  return api.get<AdminEpisode[]>(`/admin/animes/${animeId}/episodes`);
}

/**
 * 화수 부분 수정
 * PATCH /api/admin/animes/{animeId}/episodes/{episodeId}
 * - 영상 경로(videoUrl)를 고치는 유일한 경로다. 시드 SQL 로 들어간 값도 여기서 바로잡는다.
 */
export async function updateEpisodeForAdmin(
  animeId: number,
  episodeId: number,
  request: EpisodeUpdateRequest,
): Promise<AdminEpisode> {
  return api.patch<AdminEpisode>(`/admin/animes/${animeId}/episodes/${episodeId}`, request);
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
