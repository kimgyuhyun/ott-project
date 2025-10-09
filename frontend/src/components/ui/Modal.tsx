"use client";
import { ReactNode, useEffect } from "react";
import styles from "./Modal.module.css";

type ModalProps = {
  open?: boolean; // 기본 prop
  isOpen?: boolean; // 호환 prop
  onClose: () => void;
  title?: string; // 호환용(사용하지 않음)
  children?: ReactNode;
  closeOnBackdropClick?: boolean; // backdrop 클릭 시 닫기 (기본값: true)
  closeOnEscape?: boolean; // ESC 키로 닫기 (기본값: true)
};

export default function Modal({ 
  open, 
  isOpen, 
  onClose, 
  children, 
  closeOnBackdropClick = true,
  closeOnEscape = true 
}: ModalProps) {
  const visible = typeof open === 'boolean' ? open : !!isOpen;
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape" && closeOnEscape) onClose();
    }
    if (visible) {
      document.addEventListener("keydown", onKey);
      document.body.style.overflow = "hidden";
    }
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
    };
  }, [visible, onClose, closeOnEscape]);

  if (!visible) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      className={styles.modalOverlay}
    >
      <div 
        className={styles.modalBackdrop} 
        onClick={closeOnBackdropClick ? onClose : undefined}
      />
      <div className={styles.modalContainer}>
        {children}
      </div>
    </div>
  );
}


