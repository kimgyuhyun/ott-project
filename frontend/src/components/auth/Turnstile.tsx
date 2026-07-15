"use client";
// Cloudflare Turnstile 위젯 컴포넌트
// - 스크립트를 로드하고 위젯을 렌더링한 뒤, 검증 성공 시 토큰을 상위로 전달한다.
// - 위젯을 새로 띄우고 싶으면(예: 로그인 재시도) 이 컴포넌트에 key 를 바꿔 remount 하면 된다.
import { useEffect, useRef } from "react";

// Turnstile site key 는 공개값(브라우저 HTML 에 그대로 노출되는 값)이라 소스에 상수로 둬도 안전하다.
// (secret key 와 다름 — secret 은 백엔드에만 존재) 값 변경/로테이션 시 여기만 교체하면 됨.
// 빌드 시 NEXT_PUBLIC_TURNSTILE_SITE_KEY 로 override 도 가능(없으면 아래 상수 사용).
const SITE_KEY = process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY || "0x4AAAAAAD2seOGHnAGRZna_";
// render=explicit: 스크립트 자동 렌더를 끄고 우리가 명시적으로 render() 를 호출한다.
const SCRIPT_SRC = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";

// window.turnstile 전역 타입 선언(스크립트가 주입하는 객체)
declare global {
  interface Window {
    turnstile?: {
      render: (el: HTMLElement, opts: Record<string, unknown>) => string;
      remove: (id: string) => void;
      reset: (id?: string) => void;
    };
  }
}

interface TurnstileProps {
  onVerify: (token: string) => void; // 검증 성공 시 토큰 전달(상위에서 상태로 저장)
  onExpire?: () => void;             // 토큰 만료 시(선택) — 보통 토큰 초기화 용도
}

export default function Turnstile({ onVerify, onExpire }: TurnstileProps) {
  const containerRef = useRef<HTMLDivElement>(null); // 위젯이 그려질 div
  const widgetIdRef = useRef<string | null>(null);   // render 가 돌려주는 위젯 id(정리 시 필요)

  useEffect(() => {
    let cancelled = false; // 언마운트 후 콜백 실행 방지 플래그

    const renderWidget = () => {
      if (cancelled || !containerRef.current || !window.turnstile) return;
      if (widgetIdRef.current) return; // 이미 렌더됐으면 중복 방지
      widgetIdRef.current = window.turnstile.render(containerRef.current, {
        sitekey: SITE_KEY,
        callback: (token: string) => onVerify(token),       // 통과 시 토큰 전달
        "expired-callback": () => onExpire && onExpire(),   // 만료 시 콜백
      });
    };

    if (window.turnstile) {
      // 스크립트가 이미 로드돼 있으면 바로 렌더
      renderWidget();
    } else {
      // 아직 없으면 스크립트를 로드하고 load 후 렌더(이미 추가 중인 스크립트면 그 load 를 기다림)
      const existing = document.querySelector<HTMLScriptElement>(`script[src="${SCRIPT_SRC}"]`);
      if (existing) {
        existing.addEventListener("load", renderWidget);
      } else {
        const script = document.createElement("script");
        script.src = SCRIPT_SRC;
        script.async = true;
        script.defer = true;
        script.addEventListener("load", renderWidget);
        document.head.appendChild(script);
      }
    }

    // 정리: 언마운트 시 위젯 제거(메모리/중복 방지)
    return () => {
      cancelled = true;
      if (widgetIdRef.current && window.turnstile) {
        try {
          window.turnstile.remove(widgetIdRef.current);
        } catch {
          /* 이미 제거된 경우 무시 */
        }
        widgetIdRef.current = null;
      }
    };
  }, [onVerify, onExpire]);

  return <div ref={containerRef} />;
}
