"use client";
import { useState, useEffect, useRef } from "react";
import { getReviewComments, createComment, updateComment, deleteComment, toggleCommentLike, getCommentReplies, createReply } from "@/lib/api/comments";
import { getCurrentUser } from "@/lib/api/auth";
import styles from "./CommentList.module.css";

interface Comment {
  id: number;
  userName: string;
  userProfileImage?: string;
  content: string;
  likeCount: number;
  isLikedByCurrentUser: boolean;
  replacesCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

interface CommentListProps {
  reviewId: number;
  myRating?: number; // synced rating from parent
}

export default function CommentList({ reviewId, myRating = 0 }: CommentListProps) {
  const [comments, setComments] = useState<Comment[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [currentUser, setCurrentUser] = useState<any>(null);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [editingComment, setEditingComment] = useState<Comment | null>(null);
  const [editingReply, setEditingReply] = useState<Comment | null>(null);
  const [newComment, setNewComment] = useState({ content: '' });
  const scrollYRef = useRef<number>(0);

  const formatRelativeTime = (iso?: string, updatedIso?: string) => {
    if (!iso) return '';
    try {
      const created = new Date(iso);
      const updated = updatedIso ? new Date(updatedIso) : null;
      const diff = Date.now() - created.getTime();
      const minutes = Math.floor(diff / 60000);
      const hours = Math.floor(minutes / 60);
      const days = Math.floor(hours / 24);
      const years = Math.floor(days / 365);
      let base = years > 0 ? `${years}년 전` : days > 0 ? `${days}일 전` : hours > 0 ? `${hours}시간 전` : `${minutes}분 전`;
      if (updated && Math.abs(updated.getTime() - created.getTime()) > 60_000) {
        base += ' (수정됨)';
      }
      return base;
    } catch {
      return '';
    }
  };

  useEffect(() => {
    loadComments();
    loadCurrentUser();
  }, [reviewId]);

  useEffect(() => {
    restoreScroll();
  }, []);

  const saveScroll = () => {
    if (typeof window !== 'undefined') {
      scrollYRef.current = window.scrollY;
    }
  };

  const restoreScroll = () => {
    if (typeof window !== 'undefined') {
      window.scrollTo({ top: scrollYRef.current, behavior: 'instant' as ScrollBehavior });
    }
  };

  const loadCurrentUser = async () => {
    try {
      const user = await getCurrentUser();
      setCurrentUser(user);
    } catch (error) {
      console.log('사용자 정보 로드 실패:', error);
    }
  };

  const loadComments = async () => {
    try {
      setIsLoading(true);
      saveScroll();
      const data = await getReviewComments(reviewId);
      console.log('📡 댓글 API 응답:', data);
      let commentsData: Comment[] = [];
      if (data && typeof data === 'object') {
        if ('items' in (data as any) && Array.isArray((data as any).items)) {
          console.log('✅ Comments: items 구조로 파싱');
          commentsData = (data as any).items as Comment[];
        } else if ('content' in (data as any) && Array.isArray((data as any).content)) {
          console.log('✅ Comments: content 구조로 파싱');
          commentsData = (data as any).content as Comment[];
        } else if (Array.isArray(data)) {
          console.log('✅ Comments: 배열 응답으로 파싱');
          commentsData = data as unknown as Comment[];
        } else {
          console.warn('⚠️ 예상치 못한 댓글 데이터 구조:', data);
          commentsData = [];
        }
      } else {
        console.warn('⚠️ 댓글 데이터가 null/undefined');
        commentsData = [];
      }
      setComments(commentsData);
    } catch (error) {
      console.error('댓글 로드 실패:', error);
    } finally {
      setIsLoading(false);
      setTimeout(() => restoreScroll(), 0);
    }
  };

  const handleCreateComment = async () => {
    if (!newComment.content.trim()) return;
    
    try {
      await createComment(reviewId, { content: newComment.content });
      setNewComment({ content: '' });
      setShowCreateForm(false);
      loadComments();
    } catch (error) {
      console.error('댓글 작성 실패:', error);
    }
  };

  const handleUpdateComment = async () => {
    if (!editingComment || !editingComment.content.trim()) return;
    
    const prev = comments;
    const targetId = editingComment.id;
    const newContent = editingComment.content;

    setComments(prevComments =>
      prevComments.map(c => (c.id === targetId ? { ...c, content: newContent } : c))
    );
    setEditingComment(null);

    try {
      await updateComment(reviewId, targetId, { content: newContent });
    } catch (error) {
      console.error('댓글 수정 실패:', error);
      setComments(prev);
      loadComments();
    }
  };

  const handleDeleteComment = async (commentId: number) => {
    if (!confirm('정말로 이 댓글을 삭제하시겠습니까?')) return;
    
    saveScroll();
    const prev = comments;
    setComments(prevComments => prevComments.filter(c => c.id !== commentId));

    try {
      await deleteComment(reviewId, commentId);
      restoreScroll();
    } catch (error) {
      console.error('댓글 삭제 실패:', error);
      setComments(prev);
      loadComments();
      setTimeout(() => restoreScroll(), 0);
    }
  };

  const handleToggleLike = async (commentId: number) => {
    if (!currentUser) {
      alert('로그인이 필요합니다.');
      return;
    }
    saveScroll();
    const prevCommentsSnapshot = comments;
    const prevRepliesSnapshot = replies;

    // 1) 최상위 댓글 낙관적 토글
    setComments(prevComments => prevComments.map(c => {
      if (c.id !== commentId) return c;
      const liked = !c.isLikedByCurrentUser;
      return { ...c, isLikedByCurrentUser: liked, likeCount: c.likeCount + (liked ? 1 : -1) };
    }));

    // 2) 대댓글 낙관적 토글 (모든 parentId 배열에서 해당 ID를 찾아 갱신)
    setReplies(prev => {
      const next: Record<number, Comment[]> = { ...prev };
      Object.keys(next).forEach(k => {
        const pid = Number(k);
        next[pid] = next[pid]?.map(r => {
          if (r.id !== commentId) return r;
          const liked = !r.isLikedByCurrentUser;
          return { ...r, isLikedByCurrentUser: liked, likeCount: r.likeCount + (liked ? 1 : -1) };
        }) || next[pid];
      });
      return next;
    });

    try {
      await toggleCommentLike(reviewId, commentId);
      restoreScroll();
    } catch (error) {
      console.error('좋아요 토글 실패:', error);
      setComments(prevCommentsSnapshot);
      setReplies(prevRepliesSnapshot);
      setTimeout(() => restoreScroll(), 0);
    }
  };

  // 대댓글 로드/작성
  const [replies, setReplies] = useState<Record<number, Comment[]>>({});
  const [replyDrafts, setReplyDrafts] = useState<Record<number, string>>({});
  const [expandedReplies, setExpandedReplies] = useState<Set<number>>(new Set());

  const loadReplies = async (parentId: number) => {
    try {
      const data = await getCommentReplies(reviewId, parentId);
      console.log('📡 대댓글 API 응답:', data);
      
      let repliesData: Comment[] = [];
      if (data && typeof data === 'object') {
        if ('items' in (data as any) && Array.isArray((data as any).items)) {
          console.log('✅ Replies: items 구조로 파싱');
          repliesData = (data as any).items as Comment[];
        } else if ('content' in (data as any) && Array.isArray((data as any).content)) {
          console.log('✅ Replies: content 구조로 파싱');
          repliesData = (data as any).content as Comment[];
        } else if (Array.isArray(data)) {
          console.log('✅ Replies: 배열 응답으로 파싱');
          repliesData = data as unknown as Comment[];
        } else {
          console.warn('⚠️ 예상치 못한 대댓글 데이터 구조:', data);
          repliesData = [];
        }
      } else {
        console.warn('⚠️ 대댓글 데이터가 null/undefined');
        repliesData = [];
      }
      
      setReplies(prev => ({ ...prev, [parentId]: repliesData }));
    } catch (e) {
      console.log('대댓글 로드 실패:', e);
      setReplies(prev => ({ ...prev, [parentId]: [] }));
    }
  };

  const submitReply = async (parentId: number) => {
    const content = replyDrafts[parentId]?.trim();
    if (!content) return;
    try {
      await createReply(reviewId, parentId, content);
      setReplyDrafts(prev => ({ ...prev, [parentId]: '' }));
      await loadReplies(parentId);
    } catch (e) {
      console.log('대댓글 작성 실패:', e);
    }
  };

  if (isLoading) {
    return <div className={styles.loadingContainer}>댓글을 불러오는 중...</div>;
  }

  return (
    <div className={styles.mainContainer}>
      {/* 댓글 작성 폼 */}
      {currentUser && !showCreateForm && (
        <button
          onClick={() => setShowCreateForm(true)}
          className={styles.createForm}
        >
          댓글을 작성해주세요
        </button>
      )}

      {showCreateForm && (
        <div className={styles.commentForm}>
          <textarea
            value={newComment.content}
            onChange={(e) => setNewComment(prev => ({ ...prev, content: e.target.value }))}
            placeholder="댓글을 작성해주세요..."
            className={styles.commentTextarea}
            rows={3}
          />
          <div className={styles.formButtons}>
            <button
              onClick={() => setShowCreateForm(false)}
              className={styles.cancelButton}
            >
              취소
            </button>
            <button
              onClick={handleCreateComment}
              className={styles.saveButton}
            >
              작성
            </button>
          </div>
        </div>
      )}

      {/* 댓글 목록 */}
      <div className={styles.commentsList}>
        {comments.map((comment) => (
          <div key={comment.id} className={styles.commentItem}>
            {editingComment?.id === comment.id ? (
              // 수정 모드
              <div className={styles.editForm}>
                <textarea
                  value={editingComment.content}
                  onChange={(e) => setEditingComment(prev => prev ? { ...prev, content: e.target.value } : null)}
                  className={styles.editTextarea}
                  rows={2}
                />
                <div className={styles.editButtons}>
                  <button
                    onClick={() => setEditingComment(null)}
                    className={styles.editCancelButton}
                  >
                    취소
                  </button>
                  <button
                    onClick={handleUpdateComment}
                    className={styles.editSaveButton}
                  >
                    수정
                  </button>
                </div>
              </div>
            ) : (
              // 표시 모드
              <div>
                <div className={styles.commentHeader}>
                  <div className={styles.commentMeta}>
                    <span className={styles.commentDate}>{formatRelativeTime(comment.createdAt, comment.updatedAt)}</span>
                    <div className={styles.userNameSection}>
                      <img 
                        src={comment.userProfileImage || ''} 
                        alt={comment.userName} 
                        className={styles.userNameAvatar}
                        onError={(e) => {
                          console.error('❌ 댓글 닉네임 프로필 이미지 로딩 실패:', comment.userProfileImage);
                          e.currentTarget.style.display = 'none';
                        }}
                      />
                      <span className={styles.userName}>{comment.userName}</span>
                    </div>
                    {currentUser && (
                      <div className={styles.commentActions}>
                        <button
                          onClick={() => setEditingComment(comment)}
                          className={styles.actionButton}
                        >
                          수정
                        </button>
                        <button
                          onClick={() => handleDeleteComment(comment.id)}
                          className={styles.deleteButton}
                        >
                          삭제
                        </button>
                      </div>
                    )}
                  </div>
                </div>
                <p className={styles.commentContent}>{comment.content}</p>
                <div className={styles.commentActionButtons}>
                  <button
                    onClick={() => handleToggleLike(comment.id)}
                    className={`${styles.likeButton} ${
                      comment.isLikedByCurrentUser ? styles.likeButtonActive : styles.likeButtonInactive
                    }`}
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className={styles.likeIcon}>
                      <path d="M2 10h4v12H2zM22 10c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L13 1 6.59 7.41C6.22 7.78 6 8.3 6 8.83V20c0 1.1.9 2 2 2h8c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73V10z"/>
                    </svg>
                    <span>{comment.likeCount}</span>
                  </button>
                </div>

                {/* 대댓글 영역 */}
                <div className={styles.repliesSection}>
                  <div className={styles.repliesHeader}>
                    <button
                      onClick={async () => {
                        setExpandedReplies(prev => {
                          const next = new Set(prev);
                          if (next.has(comment.id)) next.delete(comment.id); else next.add(comment.id);
                          return next;
                        });
                        if (!replies[comment.id]) {
                          await loadReplies(comment.id);
                        }
                      }}
                      className={styles.replyButton}
                    >
                      {expandedReplies.has(comment.id) ? '대댓글 숨기기' : '대댓글 보기'}
                    </button>
                  </div>
                  {expandedReplies.has(comment.id) && replies[comment.id]?.map((reply) => (
                    <div key={reply.id} className={styles.replyItem}>
                      {editingReply?.id === reply.id ? (
                        <div className={styles.replyEditForm}>
                          <textarea
                            value={editingReply.content}
                            onChange={(e) => setEditingReply(prev => prev ? { ...prev, content: e.target.value } : null)}
                            className={styles.replyEditTextarea}
                            rows={2}
                          />
                          <div className={styles.replyEditButtons}>
                            <button onClick={() => setEditingReply(null)} className={styles.replyEditCancelButton}>취소</button>
                            <button onClick={async () => {
                              if (!editingReply || !editingReply.content.trim()) return;
                              try {
                                await updateComment(reviewId, reply.id, { content: editingReply.content });
                                // 갱신 후 목록 새로고침
                                await loadReplies(comment.id);
                                setEditingReply(null);
                              } catch (e) { console.log('대댓글 수정 실패:', e); }
                            }} className={styles.replyEditSaveButton}>수정</button>
                          </div>
                        </div>
                      ) : (
                        <>
                          <div className={styles.replyHeader}>
                                                         <div className={styles.replyMeta}>
                               <span className={styles.replyDate}>{formatRelativeTime(reply.createdAt, reply.updatedAt)}</span>
                               <div className={styles.userNameSection}>
                                 <img 
                                   src={reply.userProfileImage || ''} 
                                   alt={reply.userName} 
                                   className={styles.userNameAvatar}
                                   onError={(e) => {
                                     console.error('❌ 대댓글 닉네임 프로필 이미지 로딩 실패:', reply.userProfileImage);
                                     e.currentTarget.style.display = 'none';
                                   }}
                                 />
                                 <span className={styles.replyUserName}>{reply.userName}</span>
                               </div>
                              <div className={styles.replyActions}>
                                <button
                                  onClick={() => handleToggleLike(reply.id)}
                                  className={`${styles.replyLikeButton} ${
                                    reply.isLikedByCurrentUser ? styles.replyLikeButtonActive : styles.replyLikeButtonInactive
                                  }`}
                                >
                                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className={styles.replyLikeIcon}>
                                    <path d="M2 10h4v12H2zM22 10c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L13 1 6.59 7.41C6.22 7.78 6 8.3 6 8.83V20c0 1.1.9 2 2 2h8c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73V10z"/>
                                  </svg>
                                  <span>{reply.likeCount}</span>
                                </button>
                                {currentUser && reply.userName === (currentUser as any).username && (
                                  <>
                                    <button onClick={() => setEditingReply(reply)} className={styles.replyEditButton}>수정</button>
                                    <button onClick={async () => {
                                      if (!confirm('정말로 이 대댓글을 삭제하시겠습니까?')) return;
                                      try {
                                        await deleteComment(reviewId, reply.id);
                                        await loadReplies(comment.id);
                                      } catch (e) { console.log('대댓글 삭제 실패:', e); }
                                    }} className={styles.replyDeleteButton}>삭제</button>
                                  </>
                                )}
                              </div>
                            </div>
                          </div>
                          <div className={styles.replyContent}>{reply.content}</div>
                        </>
                      )}


                    </div>
                  ))}
                  {/* 대댓글 입력 폼 */}
                  {expandedReplies.has(comment.id) && (
                    <div className={styles.replyForm}>
                      <input
                        value={replyDrafts[comment.id] || ''}
                        onChange={(e) => setReplyDrafts(prev => ({ ...prev, [comment.id]: e.target.value }))}
                        placeholder="대댓글을 입력하세요"
                        className={styles.replyInput}
                      />
                      <button
                        onClick={() => submitReply(comment.id)}
                        className={styles.replySubmitButton}
                      >
                        등록
                      </button>
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        ))}
      </div>

      {comments.length === 0 && !showCreateForm && (
        <div className={styles.emptyState}>
          아직 작성된 댓글이 없습니다.
        </div>
      )}
    </div>
  );
}
