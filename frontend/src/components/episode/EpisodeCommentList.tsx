"use client";
import { useState, useEffect, useRef } from "react";
import { getEpisodeComments, createEpisodeComment, updateEpisodeComment, deleteEpisodeComment, toggleEpisodeCommentLike, getEpisodeCommentReplies, createEpisodeReply } from "@/lib/api/episodeComments";
import { getCurrentUser } from "@/lib/api/auth";
import DropdownMenu from "@/components/ui/DropdownMenu";
import LoginRequiredModal from "@/components/auth/LoginRequiredModal";
import styles from "./EpisodeCommentList.module.css";
import { EpisodeComment } from "@/types/episodeComments";

interface EpisodeCommentListProps {
  episodeId: number;
}

export default function EpisodeCommentList({ episodeId }: EpisodeCommentListProps) {
  const [comments, setComments] = useState<EpisodeComment[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [currentUser, setCurrentUser] = useState<any>(null);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [showReplyForm, setShowReplyForm] = useState<number | null>(null);
  const [editingComment, setEditingComment] = useState<EpisodeComment | null>(null);
  const [editingReply, setEditingReply] = useState<EpisodeComment | null>(null);
  const [newComment, setNewComment] = useState({ content: '' });
  const [showLoginRequired, setShowLoginRequired] = useState(false);
  const [isCommentFocused, setIsCommentFocused] = useState(false);
  const [focusedReplyId, setFocusedReplyId] = useState<number | null>(null);
  const scrollYRef = useRef<number>(0);

  const formatRelativeTime = (iso?: string, updatedIso?: string) => {
    if (!iso) return '';
    try {
      // UTC 시간을 로컬 시간으로 변환
      const created = new Date(iso + 'Z'); // Z를 추가해서 UTC로 명시
      const updated = updatedIso ? new Date(updatedIso + 'Z') : null;
      const diff = Date.now() - created.getTime();
      const minutes = Math.floor(diff / 60000);
      const hours = Math.floor(minutes / 60);
      const days = Math.floor(hours / 24);
      const months = Math.floor(days / 30);
      const years = Math.floor(days / 365);
      
      let base = '';
      if (years > 0) {
        base = `${years}년 전`;
      } else if (months > 0) {
        base = `${months}개월 전`;
      } else if (days > 0) {
        base = `${days}일 전`;
      } else if (hours > 0) {
        base = `${hours}시간 전`;
      } else if (minutes > 0) {
        base = `${minutes}분 전`;
      } else {
        base = '방금 전';
      }
      
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
  }, [episodeId]);

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
      const data = await getEpisodeComments(episodeId);
      console.log('📡 댓글 API 응답:', data);
      let commentsData: EpisodeComment[] = [];
      if (data && typeof data === 'object') {
        if ('items' in (data as any) && Array.isArray((data as any).items)) {
          console.log('✅ Comments: items 구조로 파싱');
          commentsData = (data as any).items as EpisodeComment[];
        } else if ('content' in (data as any) && Array.isArray((data as any).content)) {
          console.log('✅ Comments: content 구조로 파싱');
          commentsData = (data as any).content as EpisodeComment[];
        } else if (Array.isArray(data)) {
          console.log('✅ Comments: 배열 응답으로 파싱');
          commentsData = data as unknown as EpisodeComment[];
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
      saveScroll();
      await createEpisodeComment(episodeId, { content: newComment.content });
      setNewComment({ content: '' });
      loadComments();
      setTimeout(() => restoreScroll(), 0);
    } catch (error) {
      console.error('댓글 작성 실패:', error);
      setTimeout(() => restoreScroll(), 0);
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
      await updateEpisodeComment(episodeId, targetId, { content: newContent });
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
      await deleteEpisodeComment(episodeId, commentId);
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
      const next: Record<number, EpisodeComment[]> = { ...prev };
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
      await toggleEpisodeCommentLike(episodeId, commentId);
      restoreScroll();
    } catch (error) {
      console.error('좋아요 토글 실패:', error);
      setComments(prevCommentsSnapshot);
      setReplies(prevRepliesSnapshot);
      setTimeout(() => restoreScroll(), 0);
    }
  };

  // 대댓글 로드/작성
  const [replies, setReplies] = useState<Record<number, EpisodeComment[]>>({});
  const [expandedReplies, setExpandedReplies] = useState<Set<number>>(new Set());

  const loadReplies = async (parentId: number) => {
    try {
      const data = await getEpisodeCommentReplies(episodeId, parentId);
      console.log('📡 대댓글 API 응답:', data);
      
      let repliesData: EpisodeComment[] = [];
      if (data && typeof data === 'object') {
        if ('items' in (data as any) && Array.isArray((data as any).items)) {
          console.log('✅ Replies: items 구조로 파싱');
          repliesData = (data as any).items as EpisodeComment[];
        } else if ('content' in (data as any) && Array.isArray((data as any).content)) {
          console.log('✅ Replies: content 구조로 파싱');
          repliesData = (data as any).content as EpisodeComment[];
        } else if (Array.isArray(data)) {
          console.log('✅ Replies: 배열 응답으로 파싱');
          repliesData = data as unknown as EpisodeComment[];
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

  if (isLoading) {
    return <div className={styles.loadingContainer}>댓글을 불러오는 중...</div>;
  }

  return (
    <div className={styles.mainContainer}>
      {(
        <div className={styles.commentForm}>
          <textarea
            value={newComment.content}
            onChange={(e) => setNewComment(prev => ({ ...prev, content: e.target.value }))}
            onFocus={() => setIsCommentFocused(true)}
            onBlur={() => {
              // 포커스를 잃을 때 약간의 지연을 두어 버튼 클릭이 가능하도록 함
              setTimeout(() => setIsCommentFocused(false), 200);
            }}
            placeholder="댓글을 작성해주세요..."
            className={styles.commentTextarea}
            rows={3}
          />
          {isCommentFocused && (
            <div className={styles.formButtons}>
              <button
                onClick={() => {
                  setNewComment({ content: '' });
                  setIsCommentFocused(false);
                }}
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
          )}
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
                    {currentUser && currentUser.id === comment.userId && (
                      <DropdownMenu
                        items={[
                          {
                            label: "수정",
                            onClick: () => setEditingComment(comment),
                            className: "edit"
                          },
                          {
                            label: "삭제",
                            onClick: () => handleDeleteComment(comment.id),
                            className: "delete"
                          }
                        ]}
                      >
                        <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z" />
                        </svg>
                      </DropdownMenu>
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
                  
                  {currentUser && (
                    <button
                      onClick={() => setShowReplyForm(showReplyForm === comment.id ? null : comment.id)}
                      className={styles.replyButton}
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className={styles.replyIcon}>
                        <path d="M20 2H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h4l4 4 4-4h4c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zm-2 12H6v-2h12v2zm0-3H6V9h12v2zm0-3H6V6h12v2z"/>
                      </svg>
                      답글
                    </button>
                  )}
                </div>

                {/* 답글 입력 폼 */}
                {showReplyForm === comment.id && (
                  <div className={styles.replyForm}>
                    <textarea
                      value={newComment.content}
                      onChange={(e) => setNewComment(prev => ({ ...prev, content: e.target.value }))}
                      onFocus={() => setFocusedReplyId(comment.id)}
                      onBlur={() => {
                        // 포커스를 잃을 때 약간의 지연을 두어 버튼 클릭이 가능하도록 함
                        setTimeout(() => setFocusedReplyId(null), 200);
                      }}
                      placeholder="답글을 작성해주세요..."
                      className={styles.replyTextarea}
                      rows={3}
                    />
                    {focusedReplyId === comment.id && (
                      <div className={styles.replyFormButtons}>
                        <button
                          onClick={() => {
                            setShowReplyForm(null);
                            setNewComment({ content: '' });
                            setFocusedReplyId(null);
                          }}
                          className={styles.cancelButton}
                        >
                          취소
                        </button>
                        <button
                          onClick={async () => {
                            if (!newComment.content.trim()) return;
                            try {
                              saveScroll();
                              await createEpisodeReply(episodeId, comment.id, newComment.content);
                              setNewComment({ content: '' });
                              setShowReplyForm(null);
                              setFocusedReplyId(null);
                              // 해당 댓글의 대댓글만 다시 로드
                              await loadReplies(comment.id);
                              // 대댓글 영역 자동으로 펼치기
                              setExpandedReplies(prev => new Set([...prev, comment.id]));
                              setTimeout(() => restoreScroll(), 0);
                            } catch (error) {
                              console.error('답글 작성 실패:', error);
                              setTimeout(() => restoreScroll(), 0);
                            }
                          }}
                          disabled={!newComment.content.trim()}
                          className={styles.saveButton}
                        >
                          작성
                        </button>
                      </div>
                    )}
                  </div>
                )}

                {/* 대댓글 영역 */}
                <div className={styles.repliesSection}>
                  <div className={styles.repliesHeader}>
                    {((Boolean(replies[comment.id]?.length) || (typeof comment.replacesCount === 'number' && comment.replacesCount > 0))) && (
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
                                              {expandedReplies.has(comment.id) 
                                                ? `답글 ${replies[comment.id]?.length || 0}개 숨기기` 
                                                : `답글 ${(comment.replacesCount ?? (replies[comment.id]?.length || 0))}개 보기`}
                      </button>
                    )}
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
                                await updateEpisodeComment(episodeId, reply.id, { content: editingReply.content });
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
                                {currentUser && currentUser.id === reply.userId && (
                                  <DropdownMenu
                                    items={[
                                      {
                                        label: "수정",
                                        onClick: () => setEditingReply(reply),
                                        className: "edit"
                                      },
                                      {
                                        label: "삭제",
                                        onClick: async () => {
                                          if (!confirm('정말로 이 대댓글을 삭제하시겠습니까?')) return;
                                          try {
                                            await deleteEpisodeComment(episodeId, reply.id);
                                            await loadReplies(comment.id);
                                          } catch (e) { console.log('대댓글 삭제 실패:', e); }
                                        },
                                        className: "delete"
                                      }
                                    ]}
                                  >
                                    <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z" />
                                    </svg>
                                  </DropdownMenu>
                                )}
                              </div>
                            </div>
                          </div>
                          <div className={styles.replyContent}>{reply.content}</div>
                          <div className={styles.replyActionButtons}>
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
                          </div>
                        </>
                      )}
                    </div>
                  ))}

                </div>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}