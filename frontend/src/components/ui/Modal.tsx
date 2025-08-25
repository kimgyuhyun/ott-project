"use client";
import { ReactNode, useEffect } from "react";

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
      className="fixed inset-0 z-50 flex items-center justify-center"
    >
      <div className="absolute inset-0 bg-black/70" onClick={onClose} />
      <div className="relative w-full max-w-md rounded-2xl bg-[#1a1a1a] p-8 text-white shadow-2xl">
        {children}
      </div>
    </div>
  );
}


