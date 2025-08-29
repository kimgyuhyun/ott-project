"use client";
import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import ReviewList from "@/components/reviews/ReviewList";
import { getAnimeDetail } from "@/lib/api/anime";

interface AnimeDetailModalProps {
  anime: any;
  isOpen: boolean;
  onClose: () => void;
}

/**
 * 애니메이션 상세 정보 모달
 * 평점, 제목, 장르, 액션 버튼, 시놉시스, 탭 메뉴, 에피소드 목록 포함
 */
export default function AnimeDetailModal({ anime, isOpen, onClose }: AnimeDetailModalProps) {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<'episodes' | 'reviews' | 'shop' | 'similar'>('episodes');
  const [detail, setDetail] = useState<any>(anime);

  useEffect(() => {
    setDetail(anime);
  }, [anime]);

  useEffect(() => {
    if (!isOpen) return;
    const id = anime?.aniId ?? anime?.id;
    const needsFetch = !Array.isArray(anime?.genres) || anime.genres.length === 0 || !Array.isArray(anime?.episodes);
    if (id && needsFetch) {
      getAnimeDetail(Number(id))
        .then((d) => setDetail((prev: any) => ({ ...prev, ...d })))
        .catch(() => {});
    }
  }, [isOpen, anime]);

  // 라프텔 방식: 모달 열 때 CSS 동적 주입
  useEffect(() => {
    if (isOpen) {
      // html 태그에 data-theme="light" 추가
      document.documentElement.setAttribute('data-theme', 'light');
      
      // body에 overflow: hidden !important 적용
      document.body.style.overflow = 'hidden';
      document.body.style.setProperty('overflow', 'hidden', 'important');
    } else {
      // 모달 닫을 때 원래 상태로 복원
      document.documentElement.removeAttribute('data-theme');
      document.body.style.overflow = 'auto';
      document.body.style.removeProperty('overflow');
    }

    // 컴포넌트 언마운트 시 정리
    return () => {
      document.documentElement.removeAttribute('data-theme');
      document.body.style.overflow = 'auto';
      document.body.style.removeProperty('overflow');
    };
  }, [isOpen]);

  // 디버깅: anime 객체 확인
  console.log('🔍 AnimeDetailModal - anime 객체:', detail);
  console.log('🔍 AnimeDetailModal - anime.aniId:', detail?.aniId);
  console.log('🔍 AnimeDetailModal - anime 타입:', typeof detail);

  if (!isOpen) return null;

  const tabs: { id: 'episodes' | 'reviews' | 'shop' | 'similar'; label: string; count: number | null }[] = [
    { id: 'episodes', label: '에피소드', count: null },
    { id: 'reviews', label: '사용자 평', count: null },
    { id: 'shop', label: '상점', count: null },
    { id: 'similar', label: '비슷한 작품', count: null }
  ];

  const episodes = Array.isArray(detail?.episodes) ? detail.episodes : [];

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      zIndex: 50,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      overflow: 'hidden'
    }}>
      {/* 배경 오버레이 */}
      <div 
        style={{
          position: 'absolute',
          inset: 0,
          backdropFilter: 'blur(4px)',
          backgroundColor: 'var(--background-dim-1, rgba(0, 0, 0, 0.5))'
        }}
        onClick={onClose}
      />
      
      {/* 모달 컨테이너 */}
      <div style={{
        position: 'relative',
        borderRadius: '16px',
        maxWidth: '72rem',
        width: '100%',
        margin: '0 1rem',
        maxHeight: '90vh',
        overflowY: 'auto',
        boxShadow: 'var(--shadow-basic, rgba(0, 0, 0, 0.25))',
        backgroundColor: 'var(--background-1, #FFFFFF)'
      }}>
        {/* 닫기 버튼 - 상단 오른쪽 */}
        <button
          onClick={onClose}
          style={{
            position: 'absolute',
            top: '1rem',
            right: '1rem',
            transition: 'color 0.2s',
            zIndex: 20,
            color: 'var(--foreground-3, #8A8A8A)',
            background: 'none',
            border: 'none',
            cursor: 'pointer',
            padding: '0.5rem'
          }}
          aria-label="닫기"
        >
          <svg style={{ width: '24px', height: '24px' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>

        {/* 상단 정보 섹션 */}
        <div style={{
          position: 'relative',
          padding: '1.5rem',
          paddingBottom: 0
        }}>
          {/* 배경 이미지 */}
          <div style={{
            position: 'absolute',
            inset: 0,
            zIndex: 0
          }}>
            <div style={{
              width: '100%',
              height: '100%',
              borderTopLeftRadius: '16px',
              borderTopRightRadius: '16px',
              backgroundColor: 'var(--background-highlight, #F0EDFF)'
            }}>
              {/* 애니 캐릭터 이미지 (플레이스홀더) */}
              <div style={{
                position: 'absolute',
                inset: 0,
                backgroundSize: 'cover',
                backgroundPosition: 'center',
                backgroundRepeat: 'no-repeat',
                opacity: 0.3,
                backgroundImage: 'url("https://placehold.co/800x400/ff69b4/ffffff?text=Anime+Character")'
              }}>
              </div>
            </div>
          </div>

          {/* 작은 포스터 - 오른쪽 중간에 위치 */}
          <div style={{
            position: 'absolute',
            top: '50%',
            right: '1.5rem',
            transform: 'translateY(-50%)',
            zIndex: 10
          }}>
            <div style={{
              width: '96px',
              height: '128px',
              borderRadius: '8px',
              overflow: 'hidden',
              boxShadow: 'var(--shadow-basic, rgba(0, 0, 0, 0.25))',
              border: '2px solid',
              borderColor: 'var(--border-1, #EEEEEE)',
              backgroundColor: 'var(--background-1, #FFFFFF)'
            }}>
              <img 
                src={detail?.posterUrl || "https://placehold.co/96x128/ff69b4/ffffff?text=LAFTEL+ONLY"} 
                alt={`${detail?.title || '애니메이션'} 포스터`}
                style={{
                  width: '100%',
                  height: '100%',
                  objectFit: 'cover'
                }}
              />
            </div>
          </div>

          {/* 상단 정보 오버레이 */}
          <div style={{
            position: 'relative',
            zIndex: 10,
            color: 'var(--foreground-1, #121212)'
          }}>
            {/* 평점 및 배지 - 왼쪽 상단 */}
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.75rem',
              marginBottom: '1rem'
            }}>
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: '0.25rem'
              }}>
                <span style={{
                  fontSize: '1.125rem',
                  color: 'var(--foreground-slight, #816BFF)'
                }}>★</span>
                <span style={{
                  fontWeight: 600,
                  fontSize: '1.125rem',
                  color: 'var(--foreground-1, #121212)'
                }}>
                  {typeof detail?.rating === 'number' ? detail.rating.toFixed(1) : 'N/A'}
                </span>
              </div>
              <span style={{
                padding: '0.25rem 0.5rem',
                fontSize: '0.75rem',
                fontWeight: 700,
                borderRadius: '4px',
                backgroundColor: 'var(--foreground-slight, #816BFF)',
                color: 'var(--background-1, #FFFFFF)'
              }}>
                {detail?.badges?.[0] || 'ONLY'}
              </span>
            </div>

            {/* 애니메이션 제목 */}
            <h1 style={{
              fontSize: '1.875rem',
              fontWeight: 700,
              marginBottom: '1rem',
              color: 'var(--foreground-1, #121212)'
            }}>
              {detail?.title || '제목 없음'}
            </h1>

            {/* 장르 및 정보 */}
            <div style={{
              display: 'flex',
              flexWrap: 'wrap',
              alignItems: 'center',
              gap: '0.75rem',
              marginBottom: '1.5rem'
            }}>
              {Array.isArray(detail?.genres) && detail.genres.length > 0 ? (
                detail.genres.slice(0, 6).map((g: any, idx: number) => (
                  <span key={idx} style={{
                    padding: '0.25rem 0.75rem',
                    borderRadius: '9999px',
                    fontSize: '0.875rem',
                    backgroundColor: 'var(--background-3, #EEEEEE)',
                    color: 'var(--foreground-2, #505050)'
                  }}>
                    {g?.name || g}
                  </span>
                ))
              ) : (
                <span style={{
                  padding: '0.25rem 0.75rem',
                  borderRadius: '9999px',
                  fontSize: '0.875rem',
                  backgroundColor: 'var(--background-3, #EEEEEE)',
                  color: 'var(--foreground-2, #505050)'
                }}>장르 정보 없음</span>
              )}
              <span style={{
                fontSize: '0.875rem',
                color: 'var(--foreground-3, #8A8A8A)'
              }}>
                {(detail?.totalEpisodes ?? detail?.episodeCount ?? '정보 없음')}화
              </span>
            </div>

            {/* 액션 버튼들 */}
            <div style={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: '0.75rem',
              marginBottom: '1.5rem'
            }}>
              <button 
                onClick={() => {
                  // 플레이어 페이지로 이동 (현재 탭에서)
                  router.push(`/player?episodeId=1&animeId=${detail?.aniId}`);
                  onClose(); // 모달 닫기
                }}
                style={{
                  padding: '0.75rem 1.5rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  transition: 'background-color 0.2s',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.5rem',
                  backgroundColor: 'var(--foreground-slight, #816BFF)',
                  color: 'var(--background-1, #FFFFFF)',
                  border: 'none',
                  cursor: 'pointer'
                }}
              >
                <svg style={{ width: '20px', height: '20px' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.828 14.828a4 4 0 01-5.656 0M9 10h1m4 0h1m-6 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
                <span>보러가기</span>
              </button>
              <button style={{
                padding: '0.75rem 1.5rem',
                borderRadius: '8px',
                fontWeight: 600,
                transition: 'background-color 0.2s',
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem',
                backgroundColor: 'var(--background-3, #EEEEEE)',
                color: 'var(--foreground-2, #505050)',
                border: 'none',
                cursor: 'pointer'
              }}>
                <svg style={{ width: '20px', height: '20px' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z" />
                </svg>
                     <span>보고싶다</span>
              </button>
              <button style={{
                padding: '0.75rem 1.5rem',
                borderRadius: '8px',
                fontWeight: 600,
                transition: 'background-color 0.2s',
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem',
                backgroundColor: 'var(--background-3, #EEEEEE)',
                color: 'var(--foreground-2, #505050)',
                border: 'none',
                cursor: 'pointer'
              }}>
                <svg style={{ width: '20px', height: '20px' }} fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.367 2.684 3 3 0 00-5.367-2.684z" />
                </svg>
                <span>공유</span>
              </button>
            </div>

            {/* 시놉시스 */}
            <div style={{ marginBottom: '1.5rem' }}>
              <h3 style={{
                fontSize: '1.125rem',
                fontWeight: 600,
                marginBottom: '0.75rem',
                color: 'var(--foreground-1, #121212)'
              }}>시놉시스</h3>
              <p style={{
                lineHeight: 1.6,
                color: 'var(--foreground-2, #505050)'
              }}>
                {detail?.synopsis || detail?.fullSynopsis || "시놉시스 정보가 없습니다."}
              </p>
            </div>
          </div>
        </div>

        {/* 탭 메뉴 */}
        <div style={{
          borderBottom: '1px solid',
          borderColor: 'var(--border-1, #EEEEEE)'
        }}>
          <div style={{
            display: 'flex',
            gap: '2rem',
            padding: '0 1.5rem'
          }}>
            {tabs.map((tab) => (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.5rem',
                  padding: '1rem 0.25rem',
                  borderBottom: '2px solid',
                  borderBottomColor: activeTab === tab.id ? 'var(--foreground-slight, #816BFF)' : 'transparent',
                  transition: 'color 0.2s',
                  fontWeight: activeTab === tab.id ? 600 : 400,
                  color: activeTab === tab.id ? 'var(--foreground-slight, #816BFF)' : 'var(--foreground-3, #8A8A8A)',
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer'
                }}
              >
                <span style={{ fontSize: '0.875rem' }}>{tab.label}</span>
                {tab.count !== null && (
                  <span style={{
                    fontSize: '0.875rem',
                    color: 'var(--foreground-4, #D0D0D0)'
                  }}>({tab.count})</span>
                )}
              </button>
            ))}
          </div>
        </div>

        {/* 탭 콘텐츠 */}
        <div style={{ padding: '1.5rem' }}>
          {activeTab === 'episodes' && (
            <div>
              <h3 style={{
                fontSize: '1.125rem',
                fontWeight: 600,
                marginBottom: '1rem',
                color: 'var(--foreground-1, #121212)'
              }}>에피소드 목록</h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                {episodes.length > 0 ? (
                  episodes.map((episode: any) => (
                  <div key={episode.id} style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: '1rem',
                    padding: '1rem',
                    borderRadius: '8px',
                    backgroundColor: 'var(--background-2, #F7F7F7)'
                  }}>
                    <div style={{ flexShrink: 0 }}>
                      <img 
                        src={episode.thumbnailUrl || "https://placehold.co/120x80/999/ffffff?text=Episode"} 
                        alt={episode.title}
                        style={{
                          width: '80px',
                          height: '56px',
                          objectFit: 'cover',
                          borderRadius: '4px'
                        }}
                      />
                    </div>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        marginBottom: '0.5rem'
                      }}>
                        <h4 style={{
                          fontSize: '0.875rem',
                          fontWeight: 500,
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                          color: 'var(--foreground-1, #121212)'
                        }}>
                          {episode.title}
                        </h4>
                        <div style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: '0.75rem',
                          fontSize: '0.75rem',
                          color: 'var(--foreground-3, #8A8A8A)'
                        }}>
                          <span>{episode.duration ? `${episode.duration}분` : ''}</span>
                          <span>{episode.createdAt ? String(episode.createdAt).slice(0,10) : ''}</span>
                        </div>
                      </div>
                      <p style={{
                        fontSize: '0.875rem',
                        lineHeight: 1.4,
                        color: 'var(--foreground-2, #505050)',
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden'
                      }}>
                        {episode.description || ''}
                      </p>
                    </div>
                    <button 
                      onClick={() => {
                        // 플레이어 페이지로 이동 (현재 탭에서)
                        router.push(`/player?episodeId=${episode.id}&animeId=${detail?.aniId ?? detail?.id}`);
                        onClose(); // 모달 닫기
                      }}
                      style={{
                        flexShrink: 0,
                        padding: '0.25rem 0.75rem',
                        fontSize: '0.75rem',
                        borderRadius: '4px',
                        transition: 'background-color 0.2s',
                        backgroundColor: 'var(--foreground-slight, #816BFF)',
                        color: 'var(--background-1, #FFFFFF)',
                        border: 'none',
                        cursor: 'pointer'
                      }}
                    >
                      재생
                    </button>
                  </div>
                ))
                ) : (
                  <div style={{
                    textAlign: 'center',
                    padding: '1.5rem 0',
                    color: 'var(--foreground-3, #8A8A8A)'
                  }}>에피소드 정보가 없습니다.</div>
                )}
              </div>
            </div>
          )}

          {/* 리뷰 탭: ReviewList 항상 마운트되도록 렌더링, 탭 아닐 때는 hidden 처리 */}
          <div style={{ display: activeTab === 'reviews' ? 'block' : 'none' }}>
            {detail?.aniId ? (
              <ReviewList key={detail?.aniId ?? detail?.id} animeId={(detail?.aniId ?? detail?.id) as number} />
            ) : (
              <div style={{ textAlign: 'center', padding: '3rem 0' }}>
                <p style={{ color: 'var(--foreground-slight, #816BFF)' }}>⚠️ 애니메이션 ID를 찾을 수 없습니다.</p>
                <p style={{
                  fontSize: '0.875rem',
                  marginTop: '0.5rem',
                  color: 'var(--foreground-3, #8A8A8A)'
                }}>
                  anime 객체: {JSON.stringify(detail, null, 2)}
                </p>
              </div>
            )}
          </div>

          {activeTab === 'shop' && (
            <div style={{
              textAlign: 'center',
              padding: '3rem 0',
              color: 'var(--foreground-3, #8A8A8A)'
            }}>
              상점 기능은 준비 중입니다
            </div>
          )}

          {activeTab === 'similar' && (
            <div style={{
              textAlign: 'center',
              padding: '3rem 0',
              color: 'var(--foreground-3, #8A8A8A)'
            }}>
              비슷한 작품 기능은 준비 중입니다
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
