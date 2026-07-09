/**
 * 플레이어 도메인 타입 (백엔드 DTO와 1:1 대응)
 *
 * 대응 관계
 * - StreamUrlResponse ← PlayerStreamUrlResponseDto
 * - EpisodeProgress   ← EpisodeProgressResponseDto
 * - SkipMeta          ← SkipMetaResponseDto
 */

// 서명된 재생 URL (PlayerStreamUrlResponseDto)
export interface StreamUrlResponse {
  url: string; // 서명된 master.m3u8 URL
}

// 시청 진행률 (EpisodeProgressResponseDto)
export interface EpisodeProgress {
  positionSec: number; // 현재 위치(초)
  durationSec: number; // 총 길이(초)
  updatedAt?: string | null;
}

// 스킵 구간 메타 (SkipMetaResponseDto) — 미설정 구간은 null
export interface SkipMeta {
  introStart: number | null;
  introEnd: number | null;
  outroStart: number | null;
  outroEnd: number | null;
}
