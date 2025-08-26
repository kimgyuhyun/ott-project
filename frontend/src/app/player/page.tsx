"use client";
import { useState, useEffect } from "react";
import { useSearchParams } from "next/navigation";
import Header from "@/components/layout/Header";
import { getEpisodeStreamUrl, saveEpisodeProgress, getEpisodeProgress } from "@/lib/api/player";
import { getAnimeDetail } from "@/lib/api/anime";

export default function PlayerPage() {
  const searchParams = useSearchParams();
  const episodeId = searchParams.get('episodeId');
  const animeId = searchParams.get('animeId');
  
  const [streamUrl, setStreamUrl] = useState<string>("");
  const [animeInfo, setAnimeInfo] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);

  useEffect(() => {
    if (episodeId && animeId) {
      loadPlayerData();
      loadAnimeInfo();
    }
  }, [episodeId, animeId]);

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
      setError('재생할 수 없습니다. 멤버십이 필요할 수 있습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  const loadAnimeInfo = async () => {
    if (!animeId) return;
    
    try {
      const data = await getAnimeDetail(parseInt(animeId));
      setAnimeInfo(data);
    } catch (error) {
      console.error('애니메이션 정보 로드 실패:', error);
    }
  };

  const handleTimeUpdate = (event: any) => {
    const video = event.target;
    setCurrentTime(video.currentTime);
    setDuration(video.duration);
  };

  const handlePlayPause = () => {
    const video = document.querySelector('video') as HTMLVideoElement;
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
    const video = document.querySelector('video') as HTMLVideoElement;
    if (video) {
      const newTime = parseFloat(event.target.value);
      video.currentTime = newTime;
      setCurrentTime(newTime);
    }
  };

  const handleProgressSave = async () => {
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

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-black">
        <Header />
        <div className="flex items-center justify-center h-screen">
          <div className="text-white text-xl">로딩 중...</div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-black">
        <Header />
        <div className="flex items-center justify-center h-screen">
          <div className="text-white text-xl">{error}</div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-black">
      <Header />
      
      <main className="pt-16">
        <div className="max-w-6xl mx-auto px-4">
          {/* 애니메이션 정보 */}
          {animeInfo && (
            <div className="mb-6 text-white">
              <h1 className="text-2xl font-bold mb-2">{animeInfo.title}</h1>
              <p className="text-gray-300">에피소드 {episodeId}</p>
            </div>
          )}

          {/* 비디오 플레이어 */}
          <div className="relative bg-black rounded-lg overflow-hidden">
            {streamUrl ? (
              <video
                className="w-full h-auto max-h-[70vh]"
                controls
                onTimeUpdate={handleTimeUpdate}
                onPlay={() => setIsPlaying(true)}
                onPause={() => setIsPlaying(false)}
                onEnded={handleProgressSave}
              >
                <source src={streamUrl} type="application/x-mpegURL" />
                브라우저가 HLS를 지원하지 않습니다.
              </video>
            ) : (
              <div className="w-full h-96 bg-gray-800 flex items-center justify-center">
                <div className="text-white text-xl">비디오를 불러올 수 없습니다.</div>
              </div>
            )}
          </div>

          {/* 커스텀 컨트롤 */}
          <div className="mt-4 bg-gray-900 rounded-lg p-4">
            <div className="flex items-center space-x-4">
              <button
                onClick={handlePlayPause}
                className="p-2 bg-purple-600 text-white rounded hover:bg-purple-700 transition-colors"
              >
                {isPlaying ? (
                  <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10 9v6m4-6v6m7-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                ) : (
                  <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14.828 14.828a4 4 0 01-5.656 0M9 10h1m4 0h1m-6 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                  </svg>
                )}
              </button>

              <div className="flex-1">
                <input
                  type="range"
                  min="0"
                  max={duration || 100}
                  value={currentTime}
                  onChange={handleSeek}
                  className="w-full h-2 bg-gray-700 rounded-lg appearance-none cursor-pointer slider"
                />
                <div className="flex justify-between text-sm text-gray-400 mt-1">
                  <span>{formatTime(currentTime)}</span>
                  <span>{formatTime(duration)}</span>
                </div>
              </div>

              <button
                onClick={handleProgressSave}
                className="p-2 bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors"
              >
                진행률 저장
              </button>
            </div>
          </div>

          {/* 스타일링을 위한 CSS */}
          <style jsx>{`
            .slider::-webkit-slider-thumb {
              appearance: none;
              height: 20px;
              width: 20px;
              border-radius: 50%;
              background: #9333ea;
              cursor: pointer;
            }
            
            .slider::-moz-range-thumb {
              height: 20px;
              width: 20px;
              border-radius: 50%;
              background: #9333ea;
              cursor: pointer;
              border: none;
            }
          `}</style>
        </div>
      </main>
    </div>
  );
}
