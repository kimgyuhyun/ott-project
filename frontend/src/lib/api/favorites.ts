/**
 * 애니메이션 보고싶다(보관함) API
 */

export interface FavoriteAnime {
  id: number;
  animeId: number;
  title: string;
  posterUrl: string;
  rating: number;
  favoritedAt: string;
}

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

/**
 * 보고싶다 토글 (추가/삭제)
 */
export async function toggleFavorite(animeId: number): Promise<boolean> {
  const response = await fetch(`/api/anime/${animeId}/favorite`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error('보고싶다 토글 실패');
  }

  return response.json();
}

/**
 * 내 보고싶다 목록 조회
 */
export async function getMyFavorites(
  page: number = 0,
  size: number = 20,
  sort: string = 'favoritedAt'
): Promise<PagedResponse<FavoriteAnime>> {
  const params = new URLSearchParams({
    page: page.toString(),
    size: size.toString(),
    sort,
  });

  const response = await fetch(`/api/mypage/favorites/anime?${params}`, {
    method: 'GET',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error('보고싶다 목록 조회 실패');
  }

  return response.json();
}

/**
 * 특정 애니메이션의 보고싶다 여부 확인
 */
export async function isFavorited(animeId: number): Promise<boolean> {
  const response = await fetch(`/api/anime/${animeId}/detail`, {
    method: 'GET',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    return false;
  }

  const data = await response.json();
  return data.isFavorited || false;
}
