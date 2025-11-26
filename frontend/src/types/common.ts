// 공통 타입 정의

export interface User { // 다른 파일에서 import로 사용 가능
  // TypeScript 타입 정의
  // ?가 있으면 선택 필드, 없으면 필수 필드
  id: number; // id는 number 타입이고 필수 필드
  // Java/TypeScript의 숫자 타입(하나뿐) 정수/실수 구분이없음 백엔드에서 보내는 모든 정수 실수타입을 받을수있음음
  email: string; // email은 string 타입이고 필수 필드
  nickname: string; // nickname은 string 타입이고 필수 필드
  profileImage?: string; // profileImage는 string 타입이고 선택 필드 / 프로필 설정 안한 유저도 있기에
  membershipType?: string; // membershipType는 string 타입이고 선택 필드 / 멤버십 구독 안한 유저도 있기에
  membershipExpiryDate?: string; // membershipExpiryDate는 string 타입이고 선택 필드 //멤버십 구독을 안했으면 만료일이 없기에
  createdAt: string; // createdAt는 string 타입이고 필수 필드
  updatedAt: string; // updatedAt는 string 타입이고 필수 필드
  // 날짜도 문자열로 받음 백엔드에서 LocalDateTime -> JSON 변환 시 문자열로 전송 "2024-01-15ㅆ10:30:00" 형태태
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
