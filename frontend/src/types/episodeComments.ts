// 에피소드 댓글 관련 타입 정의

export interface EpisodeComment {
  id: number;
  episodeId: number;
  parentId?: number;
  userId: number;
  userName: string;
  userProfileImage?: string;
  content: string;
  commentStatus: 'ACTIVE' | 'DELETED' | 'REPORTED';
  replacesCount?: number;
  likeCount: number;
  isLikedByCurrentUser: boolean;
  createdAt?: string;
  updatedAt?: string;
  replies?: EpisodeComment[]; // 대댓글 목록
}

export interface CreateEpisodeCommentRequest {
  content: string;
}

export interface UpdateEpisodeCommentRequest {
  content: string;
}

export interface PagedEpisodeCommentsResponse {
  items: EpisodeComment[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
}
  