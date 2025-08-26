"use client";
import { useState, useEffect, useRef } from "react";
import { getReviewComments, createComment, updateComment, deleteComment, toggleCommentLike, getCommentReplies, createReply } from "@/lib/api/comments";
import { getCurrentUser } from "@/lib/api/auth";

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
      const items = await getCommentReplies(reviewId, parentId);
      setReplies(prev => ({ ...prev, [parentId]: Array.isArray(items) ? items as any : [] }));
    } catch (e) {
      console.log('대댓글 로드 실패:', e);
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
    return <div className="text-center py-4">댓글을 불러오는 중...</div>;
  }

  return (
    <div className="space-y-4">
      {/* 댓글 작성 폼 */}
      {currentUser && !showCreateForm && (
        <button
          onClick={() => setShowCreateForm(true)}
          className="w-full p-3 border border-gray-300 rounded-lg text-gray-500 hover:border-purple-500 hover:text-purple-500 transition-colors text-left"
        >
          댓글을 작성해주세요
        </button>
      )}

      {showCreateForm && (
        <div className="p-3 border border-gray-200 rounded-lg">
          <textarea
            value={newComment.content}
            onChange={(e) => setNewComment(prev => ({ ...prev, content: e.target.value }))}
            placeholder="댓글을 작성해주세요..."
            className="w-full p-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
            rows={3}
          />
          <div className="flex justify-end space-x-2 mt-2">
            <button
              onClick={() => setShowCreateForm(false)}
              className="px-3 py-1 text-gray-600 hover:text-gray-800 transition-colors"
            >
              취소
            </button>
            <button
              onClick={handleCreateComment}
              className="px-3 py-1 bg-purple-600 text-white rounded hover:bg-purple-700 transition-colors"
            >
              작성
            </button>
          </div>
        </div>
      )}

      {/* 댓글 목록 */}
      <div className="space-y-3">
        {comments.map((comment) => (
          <div key={comment.id} className="p-3 border border-gray-200 rounded-lg">
            {editingComment?.id === comment.id ? (
              // 수정 모드
              <div>
                <textarea
                  value={editingComment.content}
                  onChange={(e) => setEditingComment(prev => prev ? { ...prev, content: e.target.value } : null)}
                  className="w-full p-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                  rows={2}
                />
                <div className="flex justify-end space-x-2 mt-2">
                  <button
                    onClick={() => setEditingComment(null)}
                    className="px-2 py-1 text-sm text-gray-600 hover:text-gray-800 transition-colors"
                  >
                    취소
                  </button>
                  <button
                    onClick={handleUpdateComment}
                    className="px-2 py-1 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors"
                  >
                    수정
                  </button>
                </div>
              </div>
            ) : (
              // 표시 모드
              <div>
                <div className="flex justify-between items-start mb-2">
                  <div className="flex items-center space-x-2">
                    {/* 아바타 (이미지 우선, 없으면 이니셜) */}
                    {comment.userProfileImage ? (
                      <img src={comment.userProfileImage} alt={comment.userName} className="w-6 h-6 rounded-full object-cover" />
                    ) : (
                      <div className="w-6 h-6 rounded-full bg-gray-300 flex items-center justify-center text-[10px] text-white">
                        {comment.userName?.[0] || '?'}
                      </div>
                    )}
                    <span className="font-medium text-gray-800 text-sm">{comment.userName}</span>
                  </div>
                  {currentUser && (
                    <div className="flex space-x-2">
                      <button
                        onClick={() => setEditingComment(comment)}
                        className="text-xs text-blue-600 hover:text-blue-800 transition-colors"
                      >
                        수정
                      </button>
                      <button
                        onClick={() => handleDeleteComment(comment.id)}
                        className="text-xs text-red-600 hover:text-red-800 transition-colors"
                      >
                        삭제
                      </button>
                    </div>
                  )}
                </div>
                <p className="text-gray-700 text-sm mb-2">{comment.content}</p>
                <div className="flex items-center justify-between">
                  <div className="text-xs text-gray-500">{formatRelativeTime(comment.createdAt, comment.updatedAt)}</div>
                  <button
                    onClick={() => handleToggleLike(comment.id)}
                    className={`flex items-center space-x-1 text-xs transition-colors ${
                      comment.isLikedByCurrentUser ? 'text-blue-600' : 'text-gray-500 hover:text-blue-600'
                    }`}
                  >
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="w-4 h-4">
                      <path d="M2 10h4v12H2zM22 10c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L13 1 6.59 7.41C6.22 7.78 6 8.3 6 8.83V20c0 1.1.9 2 2 2h8c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73V10z"/>
                    </svg>
                    <span>{comment.likeCount}</span>
                  </button>
                </div>

                {/* 대댓글 영역 */}
                <div className="mt-2 pl-6 space-y-2">
                  <div className="flex items-center justify-between">
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
                      className="text-xs text-purple-600 hover:text-purple-800"
                    >
                      {expandedReplies.has(comment.id) ? '대댓글 숨기기' : '대댓글 보기'}
                    </button>
                  </div>
                  {expandedReplies.has(comment.id) && replies[comment.id]?.map((reply) => (
                    <div key={reply.id} className="p-2 border border-gray-100 rounded">
                      {editingReply?.id === reply.id ? (
                        <div>
                          <textarea
                            value={editingReply.content}
                            onChange={(e) => setEditingReply(prev => prev ? { ...prev, content: e.target.value } : null)}
                            className="w-full p-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                            rows={2}
                          />
                          <div className="flex justify-end space-x-2 mt-2">
                            <button onClick={() => setEditingReply(null)} className="px-2 py-1 text-xs text-gray-600 hover:text-gray-800">취소</button>
                            <button onClick={async () => {
                              if (!editingReply || !editingReply.content.trim()) return;
                              try {
                                await updateComment(reviewId, reply.id, { content: editingReply.content });
                                // 갱신 후 목록 새로고침
                                await loadReplies(comment.id);
                                setEditingReply(null);
                              } catch (e) { console.log('대댓글 수정 실패:', e); }
                            }} className="px-2 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700">수정</button>
                          </div>
                        </div>
                      ) : (
                        <>
                          <div className="flex items-center justify_between mb-1">
                            <div className="flex items-center space-x-2">
                              {reply.userProfileImage ? (
                                <img src={reply.userProfileImage} alt={reply.userName} className="w-5 h-5 rounded-full object-cover" />
                              ) : (
                                <div className="w-5 h-5 rounded-full bg-gray-300 flex items-center justify-center text-[9px] text-white">
                                  {reply.userName?.[0] || '?'}
                                </div>
                              )}
                              <span className="text-xs font-medium text-gray-800">{reply.userName}</span>
                              <span className="text-[10px] text-gray-500">{formatRelativeTime(reply.createdAt, reply.updatedAt)}</span>
                            </div>
                            <div className="flex items-center space-x-2">
                              <button
                                onClick={() => handleToggleLike(reply.id)}
                                className={`flex items-center space-x-1 text-xs transition-colors ${
                                  reply.isLikedByCurrentUser ? 'text-blue-600' : 'text-gray-500 hover:text-blue-600'
                                }`}
                              >
                                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" className="w-4 h-4">
                                  <path d="M2 10h4v12H2zM22 10c0-1.1-.9-2-2-2h-6.31l.95-4.57.03-.32c0-.41-.17-.79-.44-1.06L13 1 6.59 7.41C6.22 7.78 6 8.3 6 8.83V20c0 1.1.9 2 2 2h8c.83 0 1.54-.5 1.84-1.22l3.02-7.05c.09-.23.14-.47.14-.73V10z"/>
                                </svg>
                                <span>{reply.likeCount}</span>
                              </button>
                              {currentUser && reply.userName === (currentUser as any).username && (
                                <>
                                  <button onClick={() => setEditingReply(reply)} className="text-xs text-blue-600 hover:text-blue-800">수정</button>
                                  <button onClick={async () => {
                                    if (!confirm('정말로 이 대댓글을 삭제하시겠습니까?')) return;
                                    try {
                                      await deleteComment(reviewId, reply.id);
                                      await loadReplies(comment.id);
                                    } catch (e) { console.log('대댓글 삭제 실패:', e); }
                                  }} className="text-xs text-red-600 hover:text-red-800">삭제</button>
                                </>
                              )}
                            </div>
                          </div>
                          <div className="text-sm text-gray-700 mb-2">{reply.content}</div>
                        </>
                      )}

                      {/* 모든 댓글에 대댓글 입력 표시 */}
                      <div className="flex items-center space-x-2">
                        <input
                          value={replyDrafts[reply.id] || ''}
                          onChange={(e) => setReplyDrafts(prev => ({ ...prev, [reply.id]: e.target.value }))}
                          placeholder="대댓글을 입력하세요"
                          className="flex-1 p-2 border border-gray-300 rounded"
                        />
                        <button
                          onClick={() => submitReply(reply.id)}
                          className="px-2 py-1 bg-purple-600 text-white rounded text-xs hover:bg-purple-700"
                        >
                          등록
                        </button>
                      </div>
                    </div>
                  ))}
                  {/* 대댓글 입력 */}
                  <div className="flex items-center space-x-2">
                    <input
                      value={replyDrafts[comment.id] || ''}
                      onChange={(e) => setReplyDrafts(prev => ({ ...prev, [comment.id]: e.target.value }))}
                      placeholder="대댓글을 입력하세요"
                      className="flex-1 p-2 border border-gray-300 rounded"
                    />
                    <button
                      onClick={() => submitReply(comment.id)}
                      className="px-2 py-1 bg-purple-600 text-white rounded text-xs hover:bg-purple-700"
                    >
                      등록
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        ))}
      </div>

      {comments.length === 0 && !showCreateForm && (
        <div className="text-center py-4 text-gray-500 text-sm">
          아직 작성된 댓글이 없습니다.
        </div>
      )}
    </div>
  );
}
