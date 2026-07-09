/**
 * 애니 도메인 타입 (백엔드 DTO와 1:1 대응, 단일 진실 원천)
 *
 * 대응 관계
 * - AnimeDetail   ← AnimeDetailDto
 * - AnimeListItem ← AnimeListDto
 * - Episode       ← EpisodeDto
 * - GenreSimple   ← GenreSimpleDto
 * - StudioSimple  ← StudioSimpleDto
 * - TagSimple     ← TagSimpleDto
 *
 * 원칙
 * - 백엔드가 항상 채워 보내지 않는 필드는 옵셔널(?)로 둔다.
 * - 목록/상세의 기본 식별자는 `id`가 아니라 `aniId`다(백엔드 계약).
 */

// 방영 상태 (enum AnimeStatus)
export type AnimeStatus = "ONGOING" | "COMPLETED" | "UPCOMING" | "HIATUS";

// 장르 (GenreSimpleDto)
export interface GenreSimple {
  id: number;
  name: string;
  color?: string | null; // UI 색상 코드
}

// 제작사 (StudioSimpleDto)
export interface StudioSimple {
  id: number;
  name: string;
  logoUrl?: string | null;
  country?: string | null;
}

// 태그 (TagSimpleDto)
export interface TagSimple {
  id: number;
  name: string;
  color?: string | null;
}

// 에피소드 (EpisodeDto)
export interface Episode {
  id: number;
  episodeNumber: number;
  title: string;
  thumbnailUrl?: string | null;
  videoUrl?: string | null;
  isActive?: boolean;
  isReleased?: boolean;
  animeId: number;
  createdAt?: string;
  updatedAt?: string;
  // EpisodeDto 에는 없는 필드지만 일부 화면에서 폴백(예: 24분)으로 참조하므로 옵셔널로 둔다.
  duration?: number;
}

// 애니 상세 (AnimeDetailDto)
export interface AnimeDetail {
  aniId: number; // 목록 식별자(기본 식별자)
  detailId?: number; // 상세 식별자
  title: string;
  titleEn?: string | null;
  titleJp?: string | null;
  posterUrl?: string | null;

  rating?: number | null;
  ratingCount?: number | null;
  ageRating?: string | null;
  animeStatus?: AnimeStatus | null;

  isCompleted?: boolean;
  isExclusive?: boolean;
  isPopular?: boolean;
  isNew?: boolean;
  isSubtitle?: boolean;
  isDub?: boolean;
  isSimulcast?: boolean;
  isActive?: boolean;

  fullSynopsis?: string | null;
  tags?: string[]; // AnimeDetailDto.tags 는 문자열 목록(List<String>)
  voiceActors?: string | null;

  releaseDate?: string | null;
  endDate?: string | null;
  broadcastDay?: string | null;
  broadcastTime?: string | null;
  season?: string | null;
  year?: number | null;
  type?: string | null;
  duration?: number | null;
  releaseQuarter?: string | null;
  source?: string | null;
  country?: string | null;
  language?: string | null;
  director?: string | null;

  totalEpisodes?: number | null;
  currentEpisodes?: number | null;

  genres?: GenreSimple[];
  studios?: StudioSimple[];
  episodes?: Episode[];

  createdAt?: string;
  updatedAt?: string;
  isFavorited?: boolean;
}

// 페이지 응답 래퍼는 공용 타입에서 재export (하위 호환)
export type { PagedResponse } from "./common";

// 애니 목록 아이템 (AnimeListDto)
export interface AnimeListItem {
  aniId: number;
  title: string;
  titleEn?: string | null;
  titleJp?: string | null;
  posterUrl?: string | null;

  rating?: number | null;
  ratingCount?: number | null;

  isDub?: boolean;
  isSubtitle?: boolean;
  isExclusive?: boolean;
  isNew?: boolean;
  isPopular?: boolean;
  isCompleted?: boolean;

  animeStatus?: AnimeStatus | null;
  year?: number | null;
  type?: string | null;
}
