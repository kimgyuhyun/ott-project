// 리뷰/리뷰댓글 도메인 타입 (백엔드 DTO와 1:1 대응)
//
// 대응 관계
// - Review        ← ReviewResponseDto
// - ReviewComment ← ReviewCommentsResponseDto

export type ReviewStatus = "ACTIVE" | "DELETED" | "REPORTED";
export type CommentStatus = "ACTIVE" | "DELETED" | "REPORTED";

// 리뷰 (ReviewResponseDto)
export interface Review {
  id: number;
  aniId: number;
  userId: number;
  userName: string;
  userProfileImage?: string | null;
  content: string;
  rating: number;
  status?: ReviewStatus;
  likeCount: number;
  isLikedByCurrentUser: boolean;
  createdAt?: string;
  updatedAt?: string;
}

// 리뷰 댓글 (ReviewCommentsResponseDto)
export interface ReviewComment {
  id: number;
  reviewId: number;
  parentId?: number | null;
  userId: number;
  userName: string;
  userProfileImage?: string | null;
  content: string;
  commentStatus?: CommentStatus;
  replacesCount?: number;
  likeCount: number;
  isLikedByCurrentUser: boolean;
  createdAt?: string;
  updatedAt?: string;
  replies?: ReviewComment[]; // 대댓글 목록(클라이언트에서 채움)
}
