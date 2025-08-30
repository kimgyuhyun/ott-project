"use client";
import { useState, useEffect, useRef } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Header from "@/components/layout/Header";
import { getEpisodeStreamUrl, saveEpisodeProgress, getEpisodeProgress, getNextEpisode } from "@/lib/api/player";
import { getAnimeDetail } from "@/lib/api/anime";
import PlayerSettingsModal from "@/components/player/PlayerSettingsModal";
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
  
  // 플레이어 설정 상태
  const [showSettingsModal, setShowSettingsModal] = useState(false);
  const [videoQuality, setVideoQuality] = useState("auto");
  const [playbackRate, setPlaybackRate] = useState(1);
  const [autoSkipIntro, setAutoSkipIntro] = useState(false);
  const [autoSkipOutro, setAutoSkipOutro] = useState(false);
  
  // PIP 모드 및 와이드 모드 상태
  const [isPipMode, setIsPipMode] = useState(false);
  const [isWideMode, setIsWideMode] = useState(false);
  
  // 키보드 단축키 도움말 상태
  const [showKeyboardHelp, setShowKeyboardHelp] = useState(false);

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

  // PIP 모드 이벤트 리스너
  useEffect(() => {
    const handlePipChange = () => {
      setIsPipMode(!!document.pictureInPictureElement);
    };

    document.addEventListener('enterpictureinpicture', handlePipChange);
    document.addEventListener('leavepictureinpicture', handlePipChange);

    return () => {
      document.removeEventListener('enterpictureinpicture', handlePipChange);
      document.removeEventListener('leavepictureinpicture', handlePipChange);
    };
  }, []);

  // 키보드 단축키 이벤트 리스너
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      // 입력 필드에 포커스가 있으면 단축키 무시
      if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) {
        return;
      }

      switch (event.code) {
        case 'Space':
          event.preventDefault();
          handlePlayPause();
          break;
        case 'ArrowLeft':
          event.preventDefault();
          handleRewind10();
          break;
        case 'ArrowRight':
          event.preventDefault();
          handleForward10();
          break;
        case 'KeyM':
          event.preventDefault();
          setVolume(volume > 0 ? 0 : 1);
          if (videoRef.current) {
            videoRef.current.volume = volume > 0 ? 0 : 1;
          }
          break;
        case 'KeyF':
          event.preventDefault();
          handleFullscreen();
          break;
        case 'KeyP':
          event.preventDefault();
          handlePipMode();
          break;
        case 'KeyW':
          event.preventDefault();
          handleWideMode();
          break;
        case 'Escape':
          if (isFullscreen) {
            event.preventDefault();
            handleFullscreen();
          }
          break;
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [volume, isFullscreen]);

  // 마우스 움직임 감지로 컨트롤 자동 숨김/표시
  useEffect(() => {
    let timeoutId: NodeJS.Timeout;

    const handleMouseMove = () => {
      setShowControls(true);
      
      // 3초 후 자동으로 컨트롤 숨김
      clearTimeout(timeoutId);
      timeoutId = setTimeout(() => {
        setShowControls(false);
      }, 3000);
    };

    const handleMouseLeave = () => {
      setShowControls(false);
    };

    const videoContainer = document.querySelector(`.${styles.videoContainer}`);
    if (videoContainer) {
      videoContainer.addEventListener('mousemove', handleMouseMove);
      videoContainer.addEventListener('mouseleave', handleMouseLeave);
    }

    return () => {
      clearTimeout(timeoutId);
      if (videoContainer) {
        videoContainer.removeEventListener('mousemove', handleMouseMove);
        videoContainer.removeEventListener('mouseleave', handleMouseLeave);
      }
    };
  }, []);

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
      // API로 다음 에피소드 로드 시도
      const data = await getNextEpisode(parseInt(episodeId));
      if (data) {
        setNextEpisode(data);
        return;
      }
    } catch (error) {
      console.error('다음 에피소드 API 로드 실패:', error);
    }
    
    // API 실패 시 사이드바 에피소드 목록에서 다음 에피소드 찾기
    if (animeInfo?.episodes) {
      const currentEpisodeIndex = animeInfo.episodes.findIndex((ep: any) => ep.id == episodeId);
      if (currentEpisodeIndex !== -1 && currentEpisodeIndex < animeInfo.episodes.length - 1) {
        const nextEp = animeInfo.episodes[currentEpisodeIndex + 1];
        setNextEpisode(nextEp);
        console.log('사이드바 목록에서 다음 에피소드 찾음:', nextEp);
      }
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

  // 10초 뒤로 감기
  const handleRewind10 = () => {
    const video = videoRef.current;
    if (video) {
      const newTime = Math.max(0, currentTime - 10);
      video.currentTime = newTime;
      setCurrentTime(newTime);
    }
  };

  // 10초 앞으로 감기
  const handleForward10 = () => {
    const video = videoRef.current;
    if (video) {
      const newTime = Math.min(duration, currentTime + 10);
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

  // 재생 속도 변경
  const handlePlaybackRateChange = (rate: number) => {
    setPlaybackRate(rate);
    if (videoRef.current) {
      videoRef.current.playbackRate = rate;
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

  // PIP 모드 토글
  const handlePipMode = async () => {
    const video = videoRef.current;
    if (!video) return;

    try {
      if (document.pictureInPictureElement) {
        await document.exitPictureInPicture();
        setIsPipMode(false);
      } else {
        await video.requestPictureInPicture();
        setIsPipMode(true);
      }
    } catch (error) {
      console.error('PIP 모드 전환 실패:', error);
    }
  };

  // 와이드 모드 토글
  const handleWideMode = () => {
    setIsWideMode(!isWideMode);
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
             <div className={`${styles.videoContainer} ${isWideMode ? styles.wideMode : ''}`}>
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

                                         {/* 10초 뒤로 감기 버튼 */}
                     <button
                       onClick={handleRewind10}
                       className={styles.rewindButton}
                       title="10초 뒤로"
                     >
                       <svg className={styles.rewindIcon} fill="currentColor" viewBox="0 0 24 24">
                         <path d="M11 6v12L2.5 12 11 6z"/>
                       </svg>
                     </button>

                                         {/* 10초 앞으로 감기 버튼 */}
                     <button
                       onClick={handleForward10}
                       className={styles.forwardButton}
                       title="10초 앞으로"
                     >
                       <svg className={styles.forwardIcon} fill="currentColor" viewBox="0 0 24 24">
                         <path d="M13 6v12l8.5-6L13 6z"/>
                       </svg>
                     </button>
                    
                    <div className={styles.timeDisplay}>
                      {formatTime(currentTime)} / {formatTime(duration)}
                    </div>

                    {/* 다음 에피소드 버튼 */}
                    {nextEpisode && (
                      <button
                        onClick={goToNextEpisode}
                        className={styles.nextEpisodeButton}
                        title="다음 에피소드"
                      >
                        <svg className={styles.nextEpisodeIcon} fill="currentColor" viewBox="0 0 24 24">
                          <path d="M6 18l8.5-6L6 6v12z"/>
                          <path d="M16 6h2v12h-2z"/>
                        </svg>
                      </button>
                    )}
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

                    {/* 환경설정 버튼 */}
                    <button
                      onClick={() => setShowSettingsModal(true)}
                      className={styles.settingsButton}
                      title="플레이어 설정"
                    >
                      <svg className={styles.settingsIcon} fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M11.49 3.17c-.38-1.56-2.6-1.56-2.98 0a1.532 1.532 0 01-2.286.948c-1.372-.836-2.942.734-2.106 2.106.54.886.061 2.042-.947 2.287-1.561.379-1.561 2.6 0 2.978a1.532 1.532 0 01.947 2.287c-.836 1.372.734 2.942 2.106 2.106a1.532 1.532 0 012.287.947c.379 1.561 2.6 1.561 2.978 0a1.533 1.533 0 012.287-.947c1.372.836 2.942-.734 2.106-2.106a1.533 1.533 0 01.947-2.287c1.561-.379 1.561-2.6 0-2.978a1.532 1.532 0 01-.947-2.287c.836-1.372-.734-2.942-2.106-2.106a1.532 1.532 0 01-2.287-.947zM10 13a3 3 0 100-6 3 3 0 000 6z" clipRule="evenodd" />
                      </svg>
                    </button>

                    {/* 키보드 단축키 도움말 버튼 */}
                    <button
                      onClick={() => setShowKeyboardHelp(!showKeyboardHelp)}
                      className={`${styles.helpButton} ${showKeyboardHelp ? styles.active : ''}`}
                      title="키보드 단축키 도움말"
                    >
                      <svg className={styles.helpIcon} fill="currentColor" viewBox="0 0 24 24">
                        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17h-2v-2h2v2zm2.07-7.75l-.9.92C13.45 12.9 13 13.5 13 15h-2v-.5c0-1.1.45-2.1 1.17-2.83l1.24-1.26c.37-.36.59-.86.59-1.41 0-1.1-.9-2-2-2s-2 .9-2 2H8c0-2.21 1.79-4 4-4s4 1.79 4 4c0 .88-.36 1.68-.93 2.25z"/>
                      </svg>
                    </button>

                    {/* PIP 모드 버튼 */}
                    <button
                      onClick={handlePipMode}
                      className={`${styles.pipButton} ${isPipMode ? styles.active : ''}`}
                      title={isPipMode ? "PIP 모드 종료" : "PIP 모드"}
                    >
                      <svg className={styles.pipIcon} fill="currentColor" viewBox="0 0 24 24">
                        <path d="M19 7h-8v7H3V5H1v15h2v-3h18v3h2v-9c0-2.21-1.79-4-4-4zm2 8h-8V9h6c1.1 0 2 .9 2 2v4z"/>
                      </svg>
                    </button>

                    {/* 와이드 모드 버튼 */}
                    <button
                      onClick={handleWideMode}
                      className={`${styles.wideButton} ${isWideMode ? styles.active : ''}`}
                      title={isWideMode ? "와이드 모드 종료" : "와이드 모드"}
                    >
                      <svg className={styles.wideIcon} fill="currentColor" viewBox="0 0 24 24">
                        <path d="M21 3H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H3V5h18v14z"/>
                        <path d="M9 7H7v10h2V7zm4 0h-2v10h2V7zm4 0h-2v10h2V7z"/>
                      </svg>
                    </button>
                    
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

            {/* 다음 에피소드 버튼 (비디오 아래) */}
            {nextEpisode && (
              <div className={styles.nextEpisodeContainer}>
                <button
                  onClick={goToNextEpisode}
                  className={styles.nextEpisodeButton}
                >
                  다음 에피소드 보기
                </button>
              </div>
            )}

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

               {/* 환경설정 모달 */}
        <PlayerSettingsModal
          isOpen={showSettingsModal}
          onClose={() => setShowSettingsModal(false)}
          currentQuality={videoQuality}
          currentPlaybackRate={playbackRate}
          autoSkipIntro={autoSkipIntro}
          autoSkipOutro={autoSkipOutro}
          onQualityChange={setVideoQuality}
          onPlaybackRateChange={handlePlaybackRateChange}
          onAutoSkipIntroChange={(skip) => {
            setAutoSkipIntro(skip);
            setAutoSkipOutro(skip);
          }}
          onAutoSkipOutroChange={(skip) => {
            setAutoSkipIntro(skip);
            setAutoSkipOutro(skip);
          }}
        />

        {/* 키보드 단축키 도움말 모달 */}
        {showKeyboardHelp && (
          <div className={styles.keyboardHelpModal}>
            <div className={styles.keyboardHelpContent}>
              <div className={styles.keyboardHelpHeader}>
                <h3>키보드 단축키</h3>
                <button 
                  onClick={() => setShowKeyboardHelp(false)}
                  className={styles.keyboardHelpClose}
                >
                  ×
                </button>
              </div>
              <div className={styles.keyboardHelpBody}>
                <div className={styles.shortcutItem}>
                  <span className={styles.shortcutKey}>스페이스바</span>
                  <span className={styles.shortcutDesc}>재생/일시정지</span>
                </div>
                <div className={styles.shortcutItem}>
                  <span className={styles.shortcutKey}>← →</span>
                  <span className={styles.shortcutDesc}>10초 뒤로/앞으로</span>
                </div>
                <div className={styles.shortcutItem}>
                  <span className={styles.shortcutKey}>M</span>
                  <span className={styles.shortcutDesc}>음소거/음소거 해제</span>
                </div>
                <div className={styles.shortcutItem}>
                  <span className={styles.shortcutKey}>F</span>
                  <span className={styles.shortcutDesc}>전체화면</span>
                </div>
                <div className={styles.shortcutItem}>
                  <span className={styles.shortcutKey}>P</span>
                  <span className={styles.shortcutDesc}>PIP 모드</span>
                </div>
                <div className={styles.shortcutItem}>
                  <span className={styles.shortcutKey}>W</span>
                  <span className={styles.shortcutDesc}>와이드 모드</span>
                </div>
                <div className={styles.shortcutItem}>
                  <span className={styles.shortcutKey}>ESC</span>
                  <span className={styles.shortcutDesc}>전체화면 종료</span>
                </div>
              </div>
            </div>
          </div>
        )}

      </div>
    );
  }
