/**
 * 시청 프로필 API
 *
 * 계정 하나에 프로필 여러 개를 두고 로그인 후 사용할 프로필을 고른다.
 * 지금 단계에서 선택은 화면 상태일 뿐이라 시청기록·찜·별점은 프로필별로 갈리지 않는다.
 */
import { api } from "./index";

export interface ViewingProfile {
  id: number;
  name: string;
}

/** 계정당 프로필 상한. 백엔드 ViewingProfile.MAX_PER_ACCOUNT 와 같아야 한다. */
export const MAX_PROFILES = 4;

/** 내 프로필 목록. 하나도 없으면 서버가 계정 이름으로 기본 프로필을 만들어 돌려준다. */
export async function getMyProfiles(): Promise<ViewingProfile[]> {
  return api.get<ViewingProfile[]>("/api/profiles");
}

/** 프로필 생성 */
export async function createProfile(name: string): Promise<ViewingProfile> {
  return api.post<ViewingProfile>("/api/profiles", { name });
}

/** 프로필 이름 변경 */
export async function renameProfile(
  profileId: number,
  name: string,
): Promise<ViewingProfile> {
  return api.patch<ViewingProfile>(`/api/profiles/${profileId}`, { name });
}

/** 프로필 삭제. 마지막 하나는 서버가 409 로 거절한다. */
export async function deleteProfile(profileId: number): Promise<void> {
  return api.delete<void>(`/api/profiles/${profileId}`);
}

/** 사용할 프로필 선택. 선택 결과는 서버 세션에 보관된다. */
export async function selectProfile(
  profileId: number,
): Promise<ViewingProfile> {
  return api.post<ViewingProfile>(`/api/profiles/${profileId}/select`);
}
