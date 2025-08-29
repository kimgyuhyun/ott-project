"use client";
import { ReactNode, useEffect } from "react";
import styles from "./Modal.module.css";

type ModalProps = {
  open?: boolean; // 기본 prop
  isOpen?: boolean; // 호환 prop
  onClose: () => void;
  title?: string; // 호환용(사용하지 않음)
  children?: ReactNode;
};

export default function Modal({ open, isOpen, onClose, children }: ModalProps) {
  const visible = typeof open === 'boolean' ? open : !!isOpen;
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    if (visible) {
      document.addEventListener("keydown", onKey);
      document.body.style.overflow = "hidden";
    }
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
    };
  }, [visible, onClose]);

  if (!visible) return null;

  return (
    <div
      role="dialog"
      aria-modal="true"
      className={styles.modalOverlay}
    >
      <div className={styles.modalBackdrop} onClick={onClose} />
      <div className={styles.modalContainer}>
        {children}
      </div>
    </div>
  );
}


