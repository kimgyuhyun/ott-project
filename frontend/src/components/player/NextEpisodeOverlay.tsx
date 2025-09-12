"use client";
import { useState, useEffect } from "react";
import styles from "./NextEpisodeOverlay.module.css";

interface NextEpisodeOverlayProps {
  nextEpisode: any;
  isVisible: boolean;
  onPlay: () => void;
  onCancel: () => void;
  countdown: number;
}

export default function NextEpisodeOverlay({
  nextEpisode,
  isVisible,
  onPlay,
  onCancel,
  countdown
}: NextEpisodeOverlayProps) {
  if (!isVisible || !nextEpisode) return null;

  return (
    <div className={styles.overlay}>
      <div className={styles.modal}>
        <div className={styles.header}>다음 화</div>
        
        <div className={styles.content}>
          <img
            src={nextEpisode.thumbnailUrl || "/api/placeholder/180/102"}
            alt={nextEpisode.title}
            className={styles.thumbnail}
          />
          
          <div className={styles.episodeInfo}>
            <div className={styles.episodeTitle}>
              {nextEpisode.episodeNumber}화
            </div>
          </div>
        </div>
        
        <div className={styles.countdownSection}>
          <div className={styles.countdownText}>
            <span className={styles.countdownNumber}>{countdown}</span>초 후 자동 재생됩니다.
          </div>
        </div>
        
        <div className={styles.actions}>
          <button className={styles.cancelButton} onClick={onCancel}>
            취소
          </button>
          
          <button className={styles.playButton} onClick={onPlay}>
            <svg className={styles.playIcon} fill="currentColor" viewBox="0 0 24 24">
              <path d="M8 5v14l11-7z"/>
            </svg>
            바로 재생
          </button>
        </div>
      </div>
    </div>
  );
}
