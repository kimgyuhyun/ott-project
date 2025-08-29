"use client";
import { useState, useEffect, useRef } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Header from "@/components/layout/Header";
import { getEpisodeStreamUrl, saveEpisodeProgress, getEpisodeProgress, getNextEpisode } from "@/lib/api/player";
import { getAnimeDetail } from "@/lib/api/anime";
import styles from "./player.module.css";

/**
 * 에피소드 재생 페이지
 * 비디오 플레이어, 컨트롤, 에피소드 정보, 다음 에피소드 이동 기능 포함
 */
export default function PlayerPage() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const episodeId = searchParams.get('episodeId');
  const animeId = searchParams.get('animeId');
  
  const videoRef = useRef<HTMLVideoElement>(null);
  const [streamUrl, setStreamUrl] = useState<string>("");
  const [animeInfo, setAnimeInfo] = useState<any>(null);
  const [episodeInfo, setEpisodeInfo] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [volume, setVolume] = useState(1);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [showControls, setShowControls] = useState(true);
  const [nextEpisode, setNextEpisode] = useState<any>(null);

  useEffect(() => {
    if (episodeId && animeId) {
      loadPlayerData();
      loadAnimeInfo();
      loadNextEpisode();
    } else {
      setError('에피소드 정보가 없습니다.');
      setIsLoading(false);
    }
  }, [episodeId, animeId]);

  // 자동 진행률 저장 (30초마다)
  useEffect(() => {
    if (currentTime > 0 && duration > 0) {
      const interval = setInterval(() => {
        saveProgress();
      }, 30000);
      return () => clearInterval(interval);
    }
  }, [currentTime, duration]);

  const loadPlayerData = async () => {
    if (!episodeId) return;
    
    try {
      setIsLoading(true);
      const data = await getEpisodeStreamUrl(parseInt(episodeId));
      setStreamUrl((data as any).url);
      
      // 기존 진행률 로드
      const progress = await getEpisodeProgress(parseInt(episodeId));
      if (progress) {
        setCurrentTime((progress as any).positionSec || 0);
      }
    } catch (error) {
      console.error('스트림 URL 로드 실패:', error);
      // 임시로 더미 비디오 URL 설정 (테스트용)
      setStreamUrl('https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4');
      console.log('더미 비디오 URL로 대체하여 테스트 진행');
    } finally {
      setIsLoading(false);
    }
  };

  const loadAnimeInfo = async () => {
    if (!animeId) return;
    
    try {
      const data = await getAnimeDetail(parseInt(animeId));
      setAnimeInfo(data);
      
      // 현재 에피소드 정보 찾기
      if ((data as any)?.episodes && episodeId) {
        const episode = (data as any).episodes.find((ep: any) => ep.id == episodeId);
        setEpisodeInfo(episode);
      }
    } catch (error) {
      console.error('애니메이션 정보 로드 실패:', error);
    }
  };

  const loadNextEpisode = async () => {
    if (!episodeId) return;
    
    try {
      const data = await getNextEpisode(parseInt(episodeId));
      setNextEpisode(data);
    } catch (error) {
      console.error('다음 에피소드 로드 실패:', error);
    }
  };

  const handleTimeUpdate = (event: any) => {
    const video = event.target;
    setCurrentTime(video.currentTime);
    setDuration(video.duration);
  };

  const handlePlayPause = () => {
    const video = videoRef.current;
    if (video) {
      if (isPlaying) {
        video.pause();
      } else {
        video.play();
      }
      setIsPlaying(!isPlaying);
    }
  };

  const handleSeek = (event: React.ChangeEvent<HTMLInputElement>) => {
    const video = videoRef.current;
    if (video) {
      const newTime = parseFloat(event.target.value);
      video.currentTime = newTime;
      setCurrentTime(newTime);
    }
  };

  const handleVolumeChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const newVolume = parseFloat(event.target.value);
    setVolume(newVolume);
    if (videoRef.current) {
      videoRef.current.volume = newVolume;
    }
  };

  const handleFullscreen = () => {
    if (!document.fullscreenElement) {
      videoRef.current?.requestFullscreen();
      setIsFullscreen(true);
    } else {
      document.exitFullscreen();
      setIsFullscreen(false);
    }
  };

  const saveProgress = async () => {
    if (!episodeId) return;
    
    try {
      await saveEpisodeProgress(parseInt(episodeId), {
        positionSec: Math.floor(currentTime),
        durationSec: Math.floor(duration)
      });
    } catch (error) {
      console.error('진행률 저장 실패:', error);
    }
  };

  const goToNextEpisode = () => {
    if (nextEpisode) {
      router.push(`/player?episodeId=${nextEpisode.id}&animeId=${animeId}`);
    }
  };

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  if (isLoading) {
    return (
      <div className={styles.loadingContainer}>
        <div className={styles.loadingText}>로딩 중...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className={styles.errorContainer}>
        <div className={styles.errorContent}>
          <div className={styles.errorTitle}>오류가 발생했습니다</div>
          <div className={styles.errorMessage}>{error}</div>
          <button 
            onClick={() => router.back()}
            className={styles.backButton}
          >
            뒤로 가기
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <Header />
      
      <div className={styles.mainContent}>
        <div className={styles.playerLayout}>
          {/* 왼쪽: 메인 비디오 플레이어 */}
          <div className={styles.videoSection}>
            {/* 애니메이션 정보 */}
            {animeInfo && (
              <div className={styles.animeInfo}>
                <h1 className={styles.animeTitle}>{animeInfo.title}</h1>
                {episodeInfo && (
                  <p className={styles.episodeInfo}>
                    {episodeInfo.title} - {episodeInfo.episodeNumber || '에피소드'}
                  </p>
                )}
              </div>
            )}

            {/* 비디오 플레이어 */}
            <div className={styles.videoContainer}>
              <video
                ref={videoRef}
                src={streamUrl}
                className={styles.video}
                onTimeUpdate={handleTimeUpdate}
                onPlay={() => setIsPlaying(true)}
                onPause={() => setIsPlaying(false)}
                onEnded={saveProgress}
                onLoadedMetadata={() => {
                  if (videoRef.current && currentTime > 0) {
                    videoRef.current.currentTime = currentTime;
                  }
                }}
                controls={false}
                autoPlay
              />
              
              {/* 커스텀 컨트롤 */}
              <div className={`${styles.controls} ${!showControls ? styles.hidden : ''}`}>
                {/* 진행률 바 */}
                <div className={styles.progressContainer}>
                  <input
                    type="range"
                    min="0"
                    max={duration || 0}
                    value={currentTime}
                    onChange={handleSeek}
                    className={styles.progressBar}
                    style={{
                      background: `linear-gradient(to right, #3b82f6 0%, #3b82f6 ${(currentTime / duration) * 100}%, #4b5563 ${(currentTime / duration) * 100}%, #4b5563 100%)`
                    }}
                  />
                </div>
                
                {/* 컨트롤 버튼들 */}
                <div className={styles.controlsRow}>
                  <div className={styles.leftControls}>
                    <button
                      onClick={handlePlayPause}
                      className={styles.playPauseButton}
                    >
                      {isPlaying ? (
                        <svg className={styles.playPauseIcon} fill="currentColor" viewBox="0 0 20 20">
                          <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zM7 8a1 1 0 012 0v4a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v4a1 1 0 102 0V8a1 1 0 00-1-1z" clipRule="evenodd" />
                        </svg>
                      ) : (
                        <svg className={styles.playPauseIcon} fill="currentColor" viewBox="0 0 20 20">
                          <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM9.555 7.168A1 1 0 008 8v4a1 1 0 001.555.832l3-2a1 1 0 000-1.664l-3-2z" clipRule="evenodd" />
                        </svg>
                      )}
                    </button>
                    
                    <div className={styles.timeDisplay}>
                      {formatTime(currentTime)} / {formatTime(duration)}
                    </div>
                  </div>
                  
                  <div className={styles.rightControls}>
                    {/* 볼륨 컨트롤 */}
                    <div className={styles.volumeControl}>
                      <svg className={styles.volumeIcon} fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M9.383 3.076A1 1 0 0110 4v12a1 1 0 01-1.617.793L4.5 14H2a1 1 0 01-1-1V7a1 1 0 011-1h2.5l3.883-3.793A1 1 0 0110 4zM12.657 2.929a1 1 0 011.414 0A9.972 9.972 0 0119 10a9.972 9.972 0 01-2.929 7.071 1 1 0 01-1.414-1.414A7.971 7.971 0 0017 10c0-2.21-.894-4.208-2.343-5.657a1 1 0 010-1.414zm-2.829 2.828a1 1 0 011.415 0A5.983 5.983 0 0115 10a5.984 5.984 0 01-1.757 4.243 1 1 0 01-1.415-1.415A3.984 3.984 0 0013 10a3.983 3.983 0 00-1.172-2.828a1 1 0 010-1.415z" clipRule="evenodd" />
                      </svg>
                      <input
                        type="range"
                        min="0"
                        max="1"
                        step="0.1"
                        value={volume}
                        onChange={handleVolumeChange}
                        className={styles.volumeSlider}
                      />
                    </div>
                    
                    {/* 전체화면 버튼 */}
                    <button
                      onClick={handleFullscreen}
                      className={styles.fullscreenButton}
                    >
                      <svg className={styles.fullscreenIcon} fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M3 4a1 1 0 011-1h4a1 1 0 010 2H6.414l2.293 2.293a1 1 0 11-1.414 1.414L5 6.414V8a1 1 0 01-2 0V4zm9 1a1 1 0 010-2h4a1 1 0 011 1v4a1 1 0 01-2 0V6.414l-2.293 2.293a1 1 0 11-1.414-1.414L13.586 5H12z" clipRule="evenodd" />
                      </svg>
                    </button>
                  </div>
                </div>
              </div>
            </div>

            {/* 댓글 섹션 */}
            <div className={styles.commentSection}>
              <div className={styles.commentHeader}>
                <span className={styles.commentCount}>댓글 8</span>
                <div className={styles.commentSort}>
                  <span className={styles.sortLabel}>인기순</span>
                </div>
              </div>
              <div className={styles.commentInput}>
                <input 
                  type="text" 
                  placeholder="댓글을 남겨보세요" 
                  className={styles.commentField}
                />
              </div>
            </div>
          </div>

          {/* 오른쪽: 에피소드 목록 사이드바 */}
          <div className={styles.episodeSidebar}>
            <h3 className={styles.sidebarTitle}>{animeInfo?.title || '애니메이션'}</h3>
            <div className={styles.episodeList}>
              {animeInfo?.episodes ? (
                animeInfo.episodes.map((episode: any) => (
                  <div 
                    key={episode.id} 
                    className={`${styles.episodeItem} ${episode.id == episodeId ? styles.activeEpisode : ''}`}
                    onClick={() => router.push(`/player?episodeId=${episode.id}&animeId=${animeId}`)}
                  >
                    <div className={styles.episodeThumbnail}>
                      <img 
                        src={episode.thumbnailUrl || "https://placehold.co/120x80/999/ffffff?text=Episode"} 
                        alt={episode.title}
                        className={styles.thumbnail}
                      />
                      <div className={styles.membershipBadge}>
                        <span className={styles.crownIcon}>👑</span>
                        <span className={styles.membershipText}>멤버십</span>
                      </div>
                    </div>
                    <div className={styles.episodeInfo}>
                      <h4 className={styles.episodeTitle}>{episode.title}</h4>
                      <span className={styles.episodeDuration}>
                        {episode.duration ? `${episode.duration}분` : '24분'}
                      </span>
                    </div>
                  </div>
                ))
              ) : (
                <div className={styles.noEpisodes}>에피소드 정보가 없습니다.</div>
              )}
            </div>
          </div>
        </div>
      </div>

    </div>
  );
}
