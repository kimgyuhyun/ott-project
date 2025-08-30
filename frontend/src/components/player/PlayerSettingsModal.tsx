"use client";
import { useState } from "react";
import styles from "./PlayerSettingsModal.module.css";

interface PlayerSettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  currentQuality: string;
  currentPlaybackRate: number;
  autoSkipIntro: boolean;
  autoSkipOutro: boolean;
  onQualityChange: (quality: string) => void;
  onPlaybackRateChange: (rate: number) => void;
  onAutoSkipIntroChange: (skip: boolean) => void;
  onAutoSkipOutroChange: (skip: boolean) => void;
}

export default function PlayerSettingsModal({
  isOpen,
  onClose,
  currentQuality,
  currentPlaybackRate,
  autoSkipIntro,
  autoSkipOutro,
  onQualityChange,
  onPlaybackRateChange,
  onAutoSkipIntroChange,
  onAutoSkipOutroChange
}: PlayerSettingsModalProps) {
  const [localQuality, setLocalQuality] = useState(currentQuality);
  const [localPlaybackRate, setLocalPlaybackRate] = useState(currentPlaybackRate);
  const [localAutoSkipIntro, setLocalAutoSkipIntro] = useState(autoSkipIntro);
  const [localAutoSkipOutro, setLocalAutoSkipOutro] = useState(autoSkipOutro);
  const [showQualityOptions, setShowQualityOptions] = useState(false);
  const [showPlaybackRateOptions, setShowPlaybackRateOptions] = useState(false);

  const qualityOptions = [
    { value: "auto", label: "자동" },
    { value: "1080p", label: "1080p" },
    { value: "720p", label: "720p" },
    { value: "480p", label: "480p" }
  ];

  const playbackRateOptions = [
    { value: 0.5, label: "0.5x" },
    { value: 0.75, label: "0.75x" },
    { value: 1, label: "1x (정상)" },
    { value: 1.25, label: "1.25x" },
    { value: 1.5, label: "1.5x" },
    { value: 2, label: "2x" }
  ];

  const handleSave = () => {
    onQualityChange(localQuality);
    onPlaybackRateChange(localPlaybackRate);
    onAutoSkipIntroChange(localAutoSkipIntro);
    onAutoSkipOutroChange(localAutoSkipOutro);
    onClose();
  };

  const handleCancel = () => {
    setLocalQuality(currentQuality);
    setLocalPlaybackRate(currentPlaybackRate);
    setLocalAutoSkipIntro(autoSkipIntro);
    setLocalAutoSkipOutro(autoSkipOutro);
    onClose();
  };

  if (!isOpen) return null;

  return (
    <div className={styles.modalOverlay} onClick={onClose}>
      <div className={styles.modalContent} onClick={(e) => e.stopPropagation()}>
        <div className={styles.modalHeader}>
          <h3 className={styles.modalTitle}>플레이어 설정</h3>
          <button className={styles.closeButton} onClick={onClose}>
            <svg className={styles.closeIcon} fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
            </svg>
          </button>
        </div>

        <div className={styles.modalBody}>
          {/* 화질 설정 */}
          <div className={styles.settingSection}>
            <div className={styles.settingItem} onClick={() => setShowQualityOptions(!showQualityOptions)}>
              <span className={styles.settingLabel}>화질 설정</span>
              <div className={styles.settingValue}>
                <span>{qualityOptions.find(opt => opt.value === localQuality)?.label || '자동'}</span>
                <svg className={styles.arrowIcon} fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clipRule="evenodd" />
                </svg>
              </div>
            </div>
            {showQualityOptions && (
              <div className={styles.settingOptions}>
                {qualityOptions.map((option) => (
                  <button
                    key={option.value}
                    className={`${styles.optionButton} ${localQuality === option.value ? styles.selected : ''}`}
                    onClick={() => {
                      setLocalQuality(option.value);
                      setShowQualityOptions(false);
                    }}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* 재생 속도 설정 */}
          <div className={styles.settingSection}>
            <div className={styles.settingItem} onClick={() => setShowPlaybackRateOptions(!showPlaybackRateOptions)}>
              <span className={styles.settingLabel}>재생 속도</span>
              <div className={styles.settingValue}>
                <span>{playbackRateOptions.find(opt => opt.value === localPlaybackRate)?.label || '1x (정상)'}</span>
                <svg className={styles.arrowIcon} fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clipRule="evenodd" />
                </svg>
              </div>
            </div>
            {showPlaybackRateOptions && (
              <div className={styles.settingOptions}>
                {playbackRateOptions.map((option) => (
                  <button
                    key={option.value}
                    className={`${styles.optionButton} ${localPlaybackRate === option.value ? styles.selected : ''}`}
                    onClick={() => {
                      setLocalPlaybackRate(option.value);
                      setShowPlaybackRateOptions(false);
                    }}
                  >
                    {option.label}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* 자동 스킵 설정 */}
          <div className={styles.settingSection}>
            <div className={styles.settingItem}>
              <span className={styles.settingLabel}>오프닝/엔딩 자동스킵</span>
              <label className={styles.toggleSwitch}>
                <input
                  type="checkbox"
                  checked={localAutoSkipIntro && localAutoSkipOutro}
                  onChange={(e) => {
                    setLocalAutoSkipIntro(e.target.checked);
                    setLocalAutoSkipOutro(e.target.checked);
                  }}
                />
                <span className={styles.toggleSlider}></span>
              </label>
            </div>
          </div>
        </div>

        <div className={styles.modalFooter}>
          <button className={styles.cancelButton} onClick={handleCancel}>
            취소
          </button>
          <button className={styles.saveButton} onClick={handleSave}>
            저장
          </button>
        </div>
      </div>
    </div>
  );
}
