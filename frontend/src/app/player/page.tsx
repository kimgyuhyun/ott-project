"use client";
import { useState, useEffect, useRef, useCallback, Suspense } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import Header from "@/components/layout/Header";
import { getEpisodeStreamUrl, saveEpisodeProgress, getEpisodeProgress, getNextEpisode, getSkips } from "@/lib/api/player";
import { getAnimeDetail } from "@/lib/api/anime";
import { getUserMembership } from "@/lib/api/membership";
import PlayerSettingsModal from "@/components/player/PlayerSettingsModal";
import EpisodeCommentList from "@/components/episode/EpisodeCommentList";
import { useAuth } from "@/hooks/useAuth";
import LoginRequiredModal from "@/components/auth/LoginRequiredModal";
import { AnimeDetail, Episode } from "@/types/anime";
import { SkipMeta } from "@/types/player";
import styles from "./player.module.css";

/**
 * 에피소드 재생 페이지
 * 비디오 플레이어, 컨트롤, 에피소드 정보, 다음 에피소드 이동 기능 포함
 */
function PlayerContent() {
  const getFallbackEpisodeThumb = (episodeNumber?: number) => {
    const n = Number(episodeNumber);
    if (n === 1) return 'https://placehold.co/120x80/111827/ffffff?text=EP1+Thumbnail';
    if (n === 2) return 'https://placehold.co/120x80/1f2937/ffffff?text=EP2+Thumbnail';
    return 'https://placehold.co/120x80/374151/ffffff?text=Episode';
  };
  const searchParams = useSearchParams();
  const router = useRouter();
  const episodeId = searchParams.get('episodeId');
  const animeId = searchParams.get('animeId');
  
  const videoRef = useRef<HTMLVideoElement>(null);
  const [streamUrl, setStreamUrl] = useState<string>("");
  const [animeInfo, setAnimeInfo] = useState<AnimeDetail | null>(null);
  const [episodeInfo, setEpisodeInfo] = useState<Episode | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [volume, setVolume] = useState(1);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [showControls, setShowControls] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const hideTimerRef = useRef<number | null>(null);
  const [nextEpisode, setNextEpisode] = useState<Episode | null>(null);
  const [hasMembership, setHasMembership] = useState<boolean>(false);
  
  // 스킵 메타 데이터
  const [skipMeta, setSkipMeta] = useState<SkipMeta | null>(null);
  const [hasSkippedIntro, setHasSkippedIntro] = useState<boolean>(false);
  const [hasSkippedOutro, setHasSkippedOutro] = useState<boolean>(false);
  
  // 다음화 자동재생
  const [showNextEpisodeOverlay, setShowNextEpisodeOverlay] = useState<boolean>(false);
  const [countdown, setCountdown] = useState<number>(10);
  
  // 플레이어 설정 상태 (localStorage에서 불러오기)
  const [showSettingsModal, setShowSettingsModal] = useState(false);
  const [videoQuality, setVideoQuality] = useState(() => {
    if (typeof window !== 'undefined') {
      return localStorage.getItem('player_videoQuality') || "auto";
    }
    return "auto";
  });
  const [playbackRate, setPlaybackRate] = useState(() => {
    if (typeof window !== 'undefined') {
      return parseFloat(localStorage.getItem('player_playbackRate') || "1");
    }
    return 1;
  });
  const [autoSkipIntro, setAutoSkipIntro] = useState(() => {
    if (typeof window !== 'undefined') {
      return localStorage.getItem('player_autoSkipIntro') === 'true';
    }
    return false;
  });
  const [autoSkipOutro, setAutoSkipOutro] = useState(() => {
    if (typeof window !== 'undefined') {
      return localStorage.getItem('player_autoSkipOutro') === 'true';
    }
    return false;
  });
  
  // PIP 모드 및 와이드 모드 상태
  const [isPipMode, setIsPipMode] = useState(false);
  const [isWideMode, setIsWideMode] = useState(false);
  
  // 키보드 단축키 도움말 상태
  const [showKeyboardHelp, setShowKeyboardHelp] = useState(false);
  
  // 로그인 상태 체크 추가
  const { isLoggedIn, isLoading: authLoading } = useAuth();
  const [showLoginModal, setShowLoginModal] = useState(false);

  useEffect(() => {
    if (episodeId && animeId) {
      loadPlayerData();
      loadAnimeInfo();
      loadNextEpisode();
      loadSkipMeta();
    } else {
      setError('에피소드 정보가 없습니다.');
      setIsLoading(false);
    }
  }, [episodeId, animeId]);

  // 멤버십 상태 로드: 로그인 시에만 조회. 비로그인/오류는 비구독 처리
  useEffect(() => {
    const loadMembership = async () => {
      try {
        if (!isLoggedIn) {
          setHasMembership(false);
          return;
        }
        const membership = await getUserMembership();
        const status: string | undefined = membership?.status;
        const endAt: string | undefined = membership?.endAt;
        const now = new Date();
        const endDate = endAt ? new Date(endAt) : null;
        const active = status === 'ACTIVE' && (!!endDate ? endDate.getTime() > now.getTime() : true);
        setHasMembership(!!active);
      } catch {
        setHasMembership(false);
      }
    };
    if (!authLoading) {
      loadMembership();
    }
  }, [isLoggedIn, authLoading]);

  // 자동 진행률 저장 (5초마다) - 한 번만 시작
  useEffect(() => {
    let interval: number | null = null;
    
    if (duration > 0 && isLoggedIn && episodeId) {
      interval = window.setInterval(() => {
        saveProgress();
      }, 5000);
    }

    return () => {
      if (interval) {
        window.clearInterval(interval);
        interval = null;
      }
    };
  }, [duration, isLoggedIn, episodeId]); // currentTime 제거

  const saveProgress = useCallback(async () => {
    // 엣지 케이스 검증
    if (!episodeId || !isLoggedIn || !videoRef.current) {
      return;
    }

    // 비디오에서 직접 현재 시간을 가져와서 더 정확한 값 사용
    const videoCurrentTime = videoRef.current.currentTime;
    const videoDuration = videoRef.current.duration;

    // NaN, Infinity, 음수 값 검증
    if (!isFinite(videoCurrentTime) || !isFinite(videoDuration) ||
        videoCurrentTime < 0 || videoDuration <= 0) {
      return;
    }

    const positionSec = Math.floor(videoCurrentTime);
    const durationSec = Math.floor(videoDuration);

    // 비정상적인 값 검증
    if (positionSec > durationSec) {
      return;
    }

    // 너무 짧은 재생 시간은 저장하지 않음 (광고 스킵 등)
    if (positionSec < 5) {
      return;
    }

    // 네트워크 연결 확인
    if (!navigator.onLine) {
      return;
    }

    try {
      await saveEpisodeProgress(parseInt(episodeId), {
        positionSec,
        durationSec
      });
    } catch (error) {
      console.error('진행률 저장 실패:', error);
    }
  }, [episodeId, isLoggedIn]);

  // 페이지를 나갈 때 시청 진행률 저장
  useEffect(() => {
    const handleBeforeUnload = () => {
      if (currentTime > 0 && duration > 0 && episodeId && isLoggedIn) {
        // 동기적으로 저장 (navigator.sendBeacon 사용)
        // Blob 으로 Content-Type 을 application/json 으로 지정해야 백엔드 @RequestBody 가 파싱함
        // (문자열로 넘기면 text/plain 으로 전송되어 415 로 실패)
        const data = new Blob([JSON.stringify({
          positionSec: Math.floor(currentTime),
          durationSec: Math.floor(duration)
        })], { type: 'application/json' });

        navigator.sendBeacon(`/api/episodes/${episodeId}/progress`, data);
      }
    };

    // 탭 전환/최소화 감지
    const handleVisibilityChange = () => {
      if (document.hidden) {
        saveProgress();
      }
    };

    // 페이지 포커스 변경 감지
    const handlePageHide = () => {
      saveProgress();
    };

    window.addEventListener('beforeunload', handleBeforeUnload);
    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('pagehide', handlePageHide);
    
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('pagehide', handlePageHide);
    };
  }, [currentTime, duration, episodeId, isLoggedIn, saveProgress]);

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

  // 컨테이너에 직접 핸들러를 달아 호버 시에만 표시
  const handleControlsMouseEnter = () => {
    setShowControls(true);
    if (hideTimerRef.current) window.clearTimeout(hideTimerRef.current);
  };
  const handleControlsMouseMove = () => {
    setShowControls(true);
    if (hideTimerRef.current) window.clearTimeout(hideTimerRef.current);
    hideTimerRef.current = window.setTimeout(() => setShowControls(false), 2000);
  };
  const handleControlsMouseLeave = () => {
    if (hideTimerRef.current) window.clearTimeout(hideTimerRef.current);
    setShowControls(false);
  };

  // HLS 소스 연결: m3u8 은 hls.js 로 재생(Safari 는 네이티브 지원), 그 외(더미 mp4 등)는 직접 src 설정
  // hls.js 를 붙이지 않으면 Chrome/Firefox/Edge 에서 m3u8 이 재생되지 않는다.
  useEffect(() => {
    const video = videoRef.current;
    if (!video || !streamUrl) return undefined;

    // m3u8 이 아니면(더미 mp4 등) 그대로 재생
    if (!streamUrl.includes('.m3u8')) {
      video.src = streamUrl;
      return undefined;
    }

    // Safari/iOS 는 HLS 를 네이티브로 지원
    if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = streamUrl;
      return undefined;
    }

    // 그 외 브라우저는 hls.js 사용 (동적 import 로 초기 번들 크기/SSR 이슈 회피)
    let hls: import('hls.js').default | null = null;
    let destroyed = false;
    void import('hls.js').then(({ default: Hls }) => {
      const el = videoRef.current;
      if (destroyed || !el) return;
      if (!Hls.isSupported()) {
        el.src = streamUrl; // 최후 폴백
        return;
      }
      hls = new Hls();
      hls.loadSource(streamUrl);
      hls.attachMedia(el);
      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (!data.fatal) return;
        // hls.js 표준 복구 패턴: 네트워크/미디어 오류는 복구 시도, 그 외는 폐기
        if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
          hls?.startLoad();
        } else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) {
          hls?.recoverMediaError();
        } else {
          hls?.destroy();
        }
      });
    });

    return () => {
      destroyed = true;
      if (hls) hls.destroy();
    };
  }, [streamUrl]);

  // 비디오 자동 재생 시도 - 에피소드 변경 시에만 실행
  useEffect(() => {
    if (videoRef.current && streamUrl && isLoggedIn) {
      const video = videoRef.current;

      // 에피소드 변경 시 진행률 초기화
      setCurrentTime(0);

      // 비디오 로드 완료 후 자동 재생 시도
      const handleCanPlay = async () => {
        try {
          await video.play();
          setIsPlaying(true);
        } catch {
          // 브라우저 자동재생 정책상 실패하면 사용자가 직접 재생해야 함
          setIsPlaying(false);
        }
      };

      video.addEventListener('canplay', handleCanPlay);

      return () => {
        video.removeEventListener('canplay', handleCanPlay);
      };
    }
    return undefined;
  }, [streamUrl, isLoggedIn, episodeId]); // episodeId 추가하여 에피소드 변경 시에만 실행

  const loadPlayerData = async () => {
    if (!episodeId) return;
    
    try {
      setIsLoading(true);
      const data = await getEpisodeStreamUrl(parseInt(episodeId));
      setStreamUrl(data.url);

      // 기존 진행률 로드 (에피소드별 독립적)
      const progress = await getEpisodeProgress(parseInt(episodeId));
      if (progress && progress.positionSec > 0) {
        const savedPosition = progress.positionSec;
        const savedDuration = progress.durationSec || 0;

        // 이어보기 정책(일반 OTT 표준): 저장 위치가 영상 마지막 30초 이내면
        // 사실상 시청 완료로 보고 처음부터, 그 외에는 저장 위치에서 이어보기.
        const RESUME_END_THRESHOLD_SEC = 30;
        const isEffectivelyFinished =
          savedDuration > 0 && savedPosition >= savedDuration - RESUME_END_THRESHOLD_SEC;
        setCurrentTime(isEffectivelyFinished ? 0 : savedPosition);
      } else {
        setCurrentTime(0);
      }
    } catch (error) {
      console.error('스트림 URL 로드 실패:', error);
      
      // 403 에러 시 스트림 URL을 빈 문자열로 설정하여 로그인 필요 메시지 표시
      if (error instanceof Error && (error.message.includes('403') || error.message.includes('재생 권한이 없습니다'))) {
        setStreamUrl('');
        setError(null); // 에러 상태 초기화
        return;
      }
      
      // 기타 에러는 기존 로직 유지
      setStreamUrl('https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4');
    } finally {
      setIsLoading(false);
    }
  };

  const loadAnimeInfo = async () => {
    if (!animeId) return;
    
    try {
      // getAnimeDetail 은 아직 unknown 을 반환하므로 API 경계에서 한 번만 단언한다(2단계에서 소스 타입화 예정).
      const data = await getAnimeDetail(parseInt(animeId)) as AnimeDetail;
      setAnimeInfo(data);

      // 현재 에피소드 정보 찾기
      if (data?.episodes && episodeId) {
        const episode = data.episodes.find((ep) => ep.id === Number(episodeId));
        setEpisodeInfo(episode || null);
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
      if (data && data.id) {
        setNextEpisode(data);
        return;
      }
    } catch (error) {
      console.error('다음 에피소드 API 로드 실패:', error);
    }
    
    // API 실패 시 사이드바 에피소드 목록에서 다음 에피소드 찾기
    if (animeInfo?.episodes) {
      const currentEpisodeIndex = animeInfo.episodes.findIndex((ep) => ep.id === Number(episodeId));
      if (currentEpisodeIndex !== -1 && currentEpisodeIndex < animeInfo.episodes.length - 1) {
        const nextEp = animeInfo.episodes[currentEpisodeIndex + 1];
        setNextEpisode(nextEp);
      }
    }
  };

  // 스킵 메타 데이터 로드
  const loadSkipMeta = async () => {
    if (!episodeId) return;
    
    try {
      const data = await getSkips(parseInt(episodeId));
      setSkipMeta(data);
      // 에피소드 변경 시 스킵 상태 초기화
      setHasSkippedIntro(false);
      setHasSkippedOutro(false);
    } catch (error) {
      console.error('스킵 메타 로드 실패:', error);
      setSkipMeta(null);
    }
  };

  const handleTimeUpdate = useCallback((event: React.SyntheticEvent<HTMLVideoElement, Event>) => {
    const video = event.currentTarget;
    const newCurrentTime = video.currentTime;
    const newDuration = video.duration;
    
    // 상태 업데이트 (불필요한 리렌더링 방지)
    // currentTime은 0.5초마다 업데이트하여 저장 정확도 향상
    setCurrentTime(prev => Math.abs(prev - newCurrentTime) > 0.5 ? newCurrentTime : prev);
    setDuration(prev => Math.abs(prev - newDuration) > 0.1 ? newDuration : prev);
    
    // 자동 스킵 로직
    if (skipMeta && (autoSkipIntro || autoSkipOutro)) {
      const currentTimeSec = Math.floor(newCurrentTime);
      
      // 오프닝 자동 스킵 (한 번만)
      if (autoSkipIntro && skipMeta.introStart !== null && skipMeta.introEnd !== null && 
          !hasSkippedIntro && currentTimeSec >= skipMeta.introStart && currentTimeSec <= skipMeta.introEnd) {
        video.currentTime = skipMeta.introEnd;
        setHasSkippedIntro(true);
      }
      
      // 엔딩 자동 스킵 (한 번만)
      if (autoSkipOutro && skipMeta.outroStart !== null && skipMeta.outroEnd !== null && 
          !hasSkippedOutro && currentTimeSec >= skipMeta.outroStart && currentTimeSec <= skipMeta.outroEnd) {
        video.currentTime = skipMeta.outroEnd;
        setHasSkippedOutro(true);
      }
    }
  }, [skipMeta, autoSkipIntro, autoSkipOutro, hasSkippedIntro, hasSkippedOutro]);

  const handlePlayPause = useCallback(() => {
    const video = videoRef.current;
    if (video) {
      if (isPlaying) {
        video.pause();
        // pause 이벤트에서 setIsPlaying(false)가 호출되므로 여기서는 호출하지 않음
      } else {
        video.play();
        // play 이벤트에서 setIsPlaying(true)가 호출되므로 여기서는 호출하지 않음
      }
    }
  }, [isPlaying]);

  const handleSeek = useCallback((event: React.ChangeEvent<HTMLInputElement>) => {
    const video = videoRef.current;
    if (video) {
      const newTime = parseFloat(event.target.value);
      video.currentTime = newTime;
      // onSeeked 이벤트에서 setCurrentTime이 호출되므로 여기서는 호출하지 않음
      // onSeeked 이벤트에서 saveProgress도 호출됨
    }
  }, []);

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
    localStorage.setItem('player_playbackRate', rate.toString());
    if (videoRef.current) {
      videoRef.current.playbackRate = rate;
    }
  };

  // 비디오 품질 변경
  const handleVideoQualityChange = (quality: string) => {
    setVideoQuality(quality);
    localStorage.setItem('player_videoQuality', quality);
  };

  // 자동 스킵 설정 변경
  const handleAutoSkipIntroChange = (intro: boolean) => {
    setAutoSkipIntro(intro);
    localStorage.setItem('player_autoSkipIntro', intro.toString());
  };

  const handleAutoSkipOutroChange = (outro: boolean) => {
    setAutoSkipOutro(outro);
    localStorage.setItem('player_autoSkipOutro', outro.toString());
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


  const goToNextEpisode = () => {
    if (nextEpisode) {
      router.push(`/player?episodeId=${nextEpisode.id}&animeId=${animeId}`);
    }
  };

  // 다음화 자동재생 관련 함수들
  const handleVideoEnded = () => {
    saveProgress();

    // 다음화가 있고 로그인한 사용자라면 자동재생 오버레이 표시
    if (nextEpisode && isLoggedIn) {
      setShowNextEpisodeOverlay(true);
      setCountdown(10);
    }
  };

  const handlePlayNextEpisode = () => {
    setShowNextEpisodeOverlay(false);
    goToNextEpisode();
  };

  const handleCancelNextEpisode = () => {
    setShowNextEpisodeOverlay(false);
  };

  // 카운트다운 타이머
  useEffect(() => {
    let timer: number | undefined;
    
    if (showNextEpisodeOverlay && countdown > 0) {
      timer = window.setTimeout(() => {
        setCountdown(countdown - 1);
      }, 1000);
    } else if (showNextEpisodeOverlay && countdown === 0) {
      // 카운트다운이 끝나면 자동으로 다음화 재생
      handlePlayNextEpisode();
    }
    
    return () => {
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [showNextEpisodeOverlay, countdown]);

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  // 로그인 모달 닫기 핸들러 추가
  const handleCloseLoginModal = () => {
    setShowLoginModal(false);
  };

  // 로딩 중이거나 로그인 체크 중일 때
  if (authLoading) {
    return (
      <div className={styles.loadingContainer}>
        <div className={styles.loadingText}>로그인 상태 확인 중...</div>
      </div>
    );
  }

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
        <div className={`${styles.playerLayout} ${isWideMode ? styles.wideLayout : ''}`}>
          {/* 왼쪽: 메인 비디오 플레이어 */}
          <div className={styles.videoSection}>

            {/* 비디오 플레이어 - 로그인 상태 및 스트림 URL 유무에 따라 조건부 렌더링 */}
            {isLoggedIn && streamUrl ? (
              <div
                ref={containerRef}
                className={`${styles.videoContainer} ${isWideMode ? styles.wideMode : ''}`}
                onMouseEnter={handleControlsMouseEnter}
                onMouseMove={handleControlsMouseMove}
                onMouseLeave={handleControlsMouseLeave}
              >
                {/* 다음화 자동재생 오버레이 */}
                {showNextEpisodeOverlay && nextEpisode && (
                  <div className={styles.nextEpisodeOverlay}>
                    <div className={styles.nextEpisodeModal}>
                      <div className={styles.nextEpisodeHeader}>다음 화</div>
                      
                      <div className={styles.nextEpisodeContent}>
                        <img
                          src={nextEpisode.thumbnailUrl || getFallbackEpisodeThumb(nextEpisode.episodeNumber)}
                          alt={nextEpisode.title}
                          className={styles.nextEpisodeThumbnail}
                          onError={(e) => { (e.currentTarget as HTMLImageElement).src = '/icons/default-avatar.png'; }}
                        />
                        
                        <div className={styles.nextEpisodeInfo}>
                          <div className={styles.nextEpisodeTitle}>
                            {nextEpisode.episodeNumber}화
                          </div>
                        </div>
                      </div>
                      
                      <div className={styles.nextEpisodeCountdown}>
                        <span className={styles.countdownNumber}>{countdown}</span>초 후 자동 재생됩니다.
                      </div>
                      
                      <div className={styles.nextEpisodeActions}>
                        <button 
                          className={styles.nextEpisodeCancelButton} 
                          onClick={handleCancelNextEpisode}
                        >
                          취소
                        </button>
                        
                        <button 
                          className={styles.nextEpisodePlayButton} 
                          onClick={handlePlayNextEpisode}
                        >
                          <svg className={styles.nextEpisodePlayIcon} fill="currentColor" viewBox="0 0 24 24">
                            <path d="M8 5v14l11-7z"/>
                          </svg>
                          바로 재생
                        </button>
                      </div>
                    </div>
                  </div>
                )}
                
                <video
                  ref={videoRef}
                  className={styles.video}
                  onTimeUpdate={handleTimeUpdate}
                  onPlay={() => setIsPlaying(true)}
                  onPause={() => {
                    setIsPlaying(false);
                    saveProgress(); // 일시정지 시에도 진행률 저장
                  }}
                  onEnded={handleVideoEnded}
                  onSeeked={(event: React.SyntheticEvent<HTMLVideoElement>) => {
                    const video = event.currentTarget;
                    setCurrentTime(video.currentTime);
                    saveProgress(); // 구간 이동 시에도 진행률 저장
                  }}
                  onLoadedMetadata={() => {
                    // 저장된 이어보기 위치로 이동
                    if (videoRef.current && currentTime > 0 && videoRef.current.duration > 0) {
                      videoRef.current.currentTime = currentTime;
                      // handleTimeUpdate가 자동으로 상태를 업데이트하므로 여기서는 setCurrentTime 호출하지 않음
                    }
                  }}
                  controls={false}
                  autoPlay
                />
               
               {/* 커스텀 컨트롤 - 호버/활성시에만 렌더 */}
               {showControls && (
                 <div className={styles.controls}>
                   {/* 진행률 바 */}
                   <div className={styles.progressContainer}>
                     <div className={styles.progressRow}>
                       <span className={styles.progressTimeLeft}>{formatTime(currentTime)}</span>
                       <input
                         type="range"
                         min="0"
                         max={duration || 0}
                         value={currentTime}
                         onChange={handleSeek}
                         className={styles.progressBar}
                         style={{ '--seek-fill': `${duration > 0 ? (currentTime / duration) * 100 : 0}%` } as React.CSSProperties}
                       />
                       <span className={styles.progressTimeRight}>{formatTime(duration)}</span>
                     </div>
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

                      {/* 볼륨 컨트롤 (앞으로 버튼과 다음 화 사이, 아이콘 호버 시 세로 슬라이더 표시) */}
                      <div className={styles.volumeControl}>
                        {volume > 0 ? (
                          <svg 
                            className={styles.volumeIcon} 
                            fill="currentColor" 
                            viewBox="0 0 20 20"
                            onClick={() => {
                              const newVolume = volume > 0 ? 0 : 1;
                              setVolume(newVolume);
                              if (videoRef.current) {
                                videoRef.current.volume = newVolume;
                              }
                            }}
                            style={{ cursor: 'pointer' }}
                          >
                            <path fillRule="evenodd" d="M9.383 3.076A1 1 0 0110 4v12a1 1 0 01-1.617.793L4.5 14H2a1 1 0 01-1-1V7a1 1 0 011-1h2.5l3.883-3.793A1 1 0 0110 4zM12.657 2.929a1 1 0 011.414 0A9.972 9.972 0 0119 10a9.972 9.972 0 01-2.929 7.071 1 1 0 01-1.414-1.414A7.971 7.971 0 0017 10c0-2.21-.894-4.208-2.343-5.657a1 1 0 010-1.414zm-2.829 2.828a1 1 0 011.415 0A5.983 5.983 0 0115 10a5.984 5.984 0 01-1.757 4.243 1 1 0 01-1.415-1.415A3.984 3.983 0 0013 10a3.983 3.983 0 00-1.172-2.828a1 1 0 010-1.415z" clipRule="evenodd" />
                          </svg>
                        ) : (
                          <svg 
                            className={styles.volumeIcon} 
                            fill="currentColor" 
                            viewBox="0 0 20 20"
                            onClick={() => {
                              const newVolume = volume > 0 ? 0 : 1;
                              setVolume(newVolume);
                              if (videoRef.current) {
                                videoRef.current.volume = newVolume;
                              }
                            }}
                            style={{ cursor: 'pointer' }}
                          >
                            <path fillRule="evenodd" d="M9.383 3.076A1 1 0 0110 4v12a1 1 0 01-1.617.793L4.5 14H2a1 1 0 01-1-1V7a1 1 0 011-1h2.5l3.883-3.793A1 1 0 0110 4zM14.657 2.929a1 1 0 011.414 0A9.972 9.972 0 0119 10a9.972 9.972 0 01-2.929 7.071 1 1 0 01-1.414-1.414A7.971 7.971 0 0017 10c0-2.21-.894-4.208-2.343-5.657a1 1 0 010-1.414zm-2.829 2.828a1 1 0 011.415 0A5.983 5.983 0 0115 10a5.984 5.984 0 01-1.757 4.243 1 1 0 01-1.415-1.415A3.984 3.983 0 0013 10a3.983 3.983 0 00-1.172-2.828a1 1 0 010-1.415z" clipRule="evenodd" />
                            <path fillRule="evenodd" d="M3.28 2.22a.75.75 0 00-1.06 1.06l14.5 14.5a.75.75 0 101.06-1.06L3.28 2.22z" clipRule="evenodd" />
                          </svg>
                        )}
                        <div className={styles.volumePopup}>
                          <input
                            type="range"
                            min="0"
                            max="1"
                            step="0.01"
                            value={1 - volume}
                            onChange={(e) => {
                              const raw = parseFloat(e.target.value);
                              const newVolume = 1 - raw; // 슬라이더는 0=아래, 1=위로 보이게, 실제 볼륨은 역으로 매핑
                              setVolume(newVolume);
                              if (videoRef.current) {
                                videoRef.current.volume = newVolume;
                              }
                            }}
                            className={styles.volumeSliderVertical}
                            style={{
                              // CSS 변수로 채움 비율 전달 (아래→위)
                              '--vol-fill': `${volume * 100}%`
                            } as React.CSSProperties}
                          />
                        </div>
                      </div>

                      {/* 다음 에피소드 버튼 - 로그인한 사용자만 표시 */}
                      {nextEpisode && isLoggedIn && (
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

                      {/* 키보드 단축키 도움말 버튼 제거 */}

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
               )}
              </div>
            ) : (
              /* 로그인하지 않았거나 스트림 URL이 없을 때 메시지 */
              <div className={styles.authRequiredVideo}>
                <div className={styles.authRequiredContent}>
                  {!isLoggedIn ? (
                                         // 로그인하지 않은 경우
                     <>
                       <h2 className={styles.authRequiredTitle}>로그인이 필요합니다</h2>
                      <p className={styles.authRequiredMessage}>
                        이 콘텐츠를 시청하려면 로그인이 필요합니다.
                      </p>
                      <button 
                        onClick={() => setShowLoginModal(true)}
                        className={styles.authRequiredButton}
                      >
                        로그인하기
                      </button>
                    </>
                  ) : (
                                         // 로그인했지만 멤버십이 없는 경우
                     <>
                       <h2 className={styles.authRequiredTitle}>멤버십 가입이 필요합니다</h2>
                       <p className={styles.authRequiredMessage}>
                         이 콘텐츠를 시청하려면 멤버십 가입이 필요합니다.
                       </p>
                       <button 
                         onClick={() => router.push('/membership')}
                         className={styles.authRequiredButton}
                       >
                         멤버십 가입하기
                       </button>
                     </>
                  )}
                </div>
              </div>
            )}

            {/* 에피소드 정보 - 비디오 플레이어 바로 밑 */}
            {episodeInfo && (
              <div className={styles.episodeDetails}>
                {animeInfo && (
                  <div className={styles.animeTitle}>
                    {(() => {
                      // 더빙과 자막 여부 확인
                      const isDub = animeInfo.isDub === true;
                      const isSubtitle = animeInfo.isSubtitle === true;

                      let prefix = '';
                      if (isDub && isSubtitle) {
                        // 둘 다 true인 경우 자막으로 표시
                        prefix = '(자막) ';
                      } else if (isDub) {
                        prefix = '(더빙) ';
                      } else if (isSubtitle) {
                        prefix = '(자막) ';
                      }

                      return `${prefix}${animeInfo.title}`;
                    })()}
                  </div>
                )}
                <div className={styles.episodeTitle}>
                  {`${episodeInfo.episodeNumber || '에피소드'}화`}
                </div>
              </div>
            )}

            {/* 와이드모드일 때 댓글과 에피소드 목록을 나란히 배치 */}
            {isWideMode ? (
              <div className={styles.wideModeContent}>
                <div className={styles.commentsSection}>
                  {episodeId && (
                    <EpisodeCommentList episodeId={parseInt(episodeId)} />
                  )}
                </div>
                {/* 와이드 모드 에피소드 컨테이너 */}
                <div className={styles.episodeSidebarContainer}>
                  {/* 와이드 모드 에피소드 헤더 */}
                  <header className={styles.episodeHeader}>
                  <div className={styles.episodeHeaderContent}>
                    <span className={styles.episodeHeaderTitle}>
                      {animeInfo ? (() => {
                        // 더빙과 자막 여부 확인
                        const isDub = animeInfo.isDub === true;
                        const isSubtitle = animeInfo.isSubtitle === true;

                        let prefix = '';
                        if (isDub && isSubtitle) {
                          // 둘 다 true인 경우 자막으로 표시
                          prefix = '(자막) ';
                        } else if (isDub) {
                          prefix = '(더빙) ';
                        } else if (isSubtitle) {
                          prefix = '(자막) ';
                        }

                        return `${prefix}${animeInfo.title}`;
                      })() : '애니메이션'}
                    </span>
                  </div>
                </header>

                {/* 와이드 모드 에피소드 목록 */}
                <div className={styles.episodeScrollContainer}>
                  {animeInfo?.episodes ? (
                    animeInfo.episodes.map((episode: Episode, idx: number) => (
                      <div 
                        key={episode.id} 
                        className={`${styles.episodeItem} ${episode.id === Number(episodeId) ? styles.activeEpisode : ''}`}
                        onClick={() => router.push(`/player?episodeId=${episode.id}&animeId=${animeId}`)}
                      >
                        <div className={styles.episodeThumbnail}>
                          <img 
                            src={episode.thumbnailUrl || getFallbackEpisodeThumb(episode.episodeNumber)} 
                            alt={episode.title}
                            className={styles.thumbnail}
                            onError={(e) => { (e.currentTarget as HTMLImageElement).src = '/icons/default-avatar.png'; }}
                          />
                          {(() => {
                            const epNum = Number(episode?.episodeNumber ?? (idx + 1));
                            return (!isLoggedIn || (isLoggedIn && !hasMembership)) && epNum > 3 ? (
                              <div className={styles.membershipBadge}>
                                <span className={styles.membershipText}>멤버십</span>
                              </div>
                            ) : null;
                          })()}
                        </div>
                        <div className={styles.episodeInfo}>
                          <h4 className={styles.episodeTitle}>{episode.episodeNumber}화</h4>
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
            ) : (
              <>
                {/* 에피소드 댓글 섹션 */}
                {episodeId && (
                  <EpisodeCommentList episodeId={parseInt(episodeId)} />
                )}
              </>
            )}
          </div>

          {/* 오른쪽: 에피소드 목록 사이드바 (일반 모드일 때만) */}
          {!isWideMode && (
            <div className={styles.episodeSidebarContainer}>
              {/* 에피소드 헤더 */}
              <header className={styles.episodeHeader}>
                <div className={styles.episodeHeaderContent}>
                  <span className={styles.episodeHeaderTitle}>
                  {animeInfo ? (() => {
                      // 더빙과 자막 여부 확인
                    const isDub = animeInfo.isDub === true;
                    const isSubtitle = animeInfo.isSubtitle === true;

                      let prefix = '';
                      if (isDub && isSubtitle) {
                        // 둘 다 true인 경우 자막으로 표시
                        prefix = '(자막) ';
                      } else if (isDub) {
                        prefix = '(더빙) ';
                      } else if (isSubtitle) {
                        prefix = '(자막) ';
                      }

                    return `${prefix}${animeInfo.title}`;
                    })() : '애니메이션'}
                  </span>
                </div>
              </header>

              {/* 에피소드 목록 */}
              <div className={styles.episodeListContainer}>
                {animeInfo?.episodes ? (
                  animeInfo.episodes.map((episode: Episode, idx: number) => (
                    <div 
                      key={episode.id} 
                        className={`${styles.episodeItem} ${episode.id === Number(episodeId) ? styles.activeEpisode : ''}`}
                      onClick={() => router.push(`/player?episodeId=${episode.id}&animeId=${animeId}`)}
                    >
                      <div className={styles.episodeThumbnail}>
                        <img 
                          src={episode.thumbnailUrl || getFallbackEpisodeThumb(episode.episodeNumber)} 
                          alt={episode.title}
                          className={styles.thumbnail}
                          onError={(e) => { (e.currentTarget as HTMLImageElement).src = '/icons/default-avatar.png'; }}
                        />
                        {(() => {
                          const epNum = Number(episode?.episodeNumber ?? (idx + 1));
                          return (!isLoggedIn || (isLoggedIn && !hasMembership)) && epNum > 3 ? (
                            <div className={styles.membershipBadge}>
                              <span className={styles.membershipText}>멤버십</span>
                            </div>
                          ) : null;
                        })()}
                      </div>
                      <div className={styles.episodeInfo}>
                        <h4 className={styles.episodeTitle}>{episode.episodeNumber}화</h4>
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
          )}
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
          onQualityChange={handleVideoQualityChange}
          onPlaybackRateChange={handlePlaybackRateChange}
          onAutoSkipIntroChange={handleAutoSkipIntroChange}
          onAutoSkipOutroChange={handleAutoSkipOutroChange}
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

        {/* 로그인 필요 모달 */}
        <LoginRequiredModal 
          isOpen={showLoginModal} 
          onClose={handleCloseLoginModal} 
        />


      </div>
    );
  }

export default function PlayerPage() {
  return (
    <Suspense
      fallback={
        <div className={styles.loadingContainer}>
          <div className={styles.loadingText}>로딩 중...</div>
        </div>
      }
    >
      <PlayerContent />
    </Suspense>
  );
}
