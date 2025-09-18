// 공통 타입 정의

export interface User {
  id: number;
  email: string;
  nickname: string;
  profileImage?: string;
  membershipType?: string;
  membershipExpiryDate?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Anime {
  id: number;
  title: string;
  titleEn?: string;
  description?: string;
  posterUrl?: string;
  bannerUrl?: string;
  releaseDate?: string;
  status?: string;
  type?: string;
  genres?: string[];
  studios?: string[];
  rating?: number;
  episodeCount?: number;
  duration?: number;
  ageRating?: string;
  tags?: string[];
  createdAt: string;
  updatedAt: string;
}

export interface Episode {
  id: number;
  animeId: number;
  episodeNumber: number;
  title: string;
  description?: string;
  duration: number;
  videoUrl?: string;
  thumbnailUrl?: string;
  releaseDate?: string;
  isFree: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Comment {
  id: number;
  content: string;
  author: User;
  createdAt: string;
  updatedAt: string;
  likes?: number;
  dislikes?: number;
  isLiked?: boolean;
  isDisliked?: boolean;
}

export interface Review {
  id: number;
  content: string;
  rating: number;
  author: User;
  animeId: number;
  createdAt: string;
  updatedAt: string;
  likes?: number;
  dislikes?: number;
  isLiked?: boolean;
  isDisliked?: boolean;
}

export interface WeeklyAnime {
  dayOfWeek: string;
  animes: Anime[];
}

export interface SearchResult {
  animes: Anime[];
  totalCount: number;
  currentPage: number;
  totalPages: number;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
  error?: string;
}

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  size: number;
  first: boolean;
  last: boolean;
}

// 에러 타입
export interface ApiError {
  status: number;
  message: string;
  body?: string;
}

// 이벤트 핸들러 타입
export type EventHandler<T = Event> = (event: T) => void;
export type MouseEventHandler<T = HTMLButtonElement> = (event: React.MouseEvent<T>) => void;
export type ChangeEventHandler<T = HTMLInputElement> = (event: React.ChangeEvent<T>) => void;
export type KeyboardEventHandler<T = HTMLInputElement> = (event: React.KeyboardEvent<T>) => void;
