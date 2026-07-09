// 마이페이지/유저 도메인 타입 (백엔드 DTO/응답과 1:1 대응)

// 프로필 (/api/users/me/profile 의 동적 Map)
export interface UserProfile {
  id: number;
  email: string;
  name: string;
  username: string;
  role?: string;
  authProvider?: string;
  emailVerified?: boolean;
  enabled?: boolean;
  createdAt?: string;
  profileImage?: string; // 프로필 화면에서 참조(현재 profile 응답엔 없어 폴백 이미지 사용)
}

// 사용자 설정 (UserSettingsDto)
export interface UserSettings {
  autoSkipIntro?: boolean;
  autoSkipEnding?: boolean;
  defaultQuality?: string;
  autoNextEpisode?: boolean;
  theme?: string;
  notificationWorkUpdates?: boolean;
  notificationCommunityActivity?: boolean;
}

// 최근 시청 원본 (RecentAnimeWatchDto)
export interface RecentAnimeWatch {
  animeId: number;
  episodeId: number;
  episodeNumber: number;
  positionSec: number;
  durationSec: number;
  updatedAt: string;
}

// 최근 시청 응답 (Map { items, ... })
export interface RecentAnimeResponse {
  items: RecentAnimeWatch[];
}

// 최근 시청 화면용(상세 조인으로 aniId/title/posterUrl 보강)
export type WatchHistoryItem = RecentAnimeWatch & {
  aniId?: number;
  title?: string;
  posterUrl?: string;
};

// 보고싶다(찜) (FavoriteAnimeDto)
export interface FavoriteAnime {
  aniId: number;
  title: string;
  posterUrl?: string;
  rating?: number;
  ratingCount?: number;
  isDub?: boolean;
  isSubtitle?: boolean;
  isExclusive?: boolean;
  isNew?: boolean;
  isPopular?: boolean;
  isCompleted?: boolean;
  animeStatus?: string;
  year?: number;
  type?: string;
  favoritedAt?: string;
}

// 정주행 (BingeWatchDto)
export interface BingeWatch {
  aniId: number;
  title: string;
  posterUrl?: string;
  totalEpisodes?: number;
  watchedEpisodes?: number;
  completedAt?: string;
}

// 마이페이지 통계 (MypageStatsDto)
export interface MypageStats {
  ratingCount: number;
  reviewCount: number;
  commentCount: number;
}

// 내 별점 (MyRatingItemDto)
export interface MyRating {
  animeId: number;
  title: string;
  posterUrl?: string;
  score: number;
  updatedAt?: string;
}

// 내 리뷰 (MyReviewItemDto)
export interface MyReview {
  reviewId: number;
  animeId: number;
  title: string;
  posterUrl?: string;
  content: string;
  score?: number;
  likeCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

// 내 댓글 (MyCommentItemDto)
export interface MyComment {
  commentId: number;
  targetType?: string;
  targetId?: number;
  parentId?: number;
  animeId?: number;
  title?: string;
  episodeTitle?: string;
  posterUrl?: string;
  episodeThumbUrl?: string;
  content: string;
  likeCount?: number;
  userProfileImage?: string;
  createdAt?: string;
}
