const API_BASE = '';

async function apiCall<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const url = `${API_BASE}${endpoint}`;
  const response = await fetch(url, {
    ...options,
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
  });
  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`API Error: ${response.status} ${errorText}`);
  }
  return response.json().catch(() => undefined as unknown as T);
}

export function createOrUpdateRating(animeId: number, score: number) {
  const fixed = Number((score ?? 0).toFixed(1));
  return apiCall<number>(`/api/anime/${animeId}/ratings?score=${encodeURIComponent(fixed)}`, {
    method: 'POST',
  });
}

export function getMyRating(animeId: number) {
  return apiCall<number | null>(`/api/anime/${animeId}/ratings/me`);
}

export function getRatingStats(animeId: number) {
  console.log('📡 getRatingStats call ->', `/api/anime/${animeId}/ratings/stats`);
  return apiCall<{ distribution: Record<string, number>, average: number }>(`/api/anime/${animeId}/ratings/stats`);
}

export function deleteMyRating(animeId: number) {
  return apiCall<void>(`/api/anime/${animeId}/ratings`, { method: 'DELETE' });
}


