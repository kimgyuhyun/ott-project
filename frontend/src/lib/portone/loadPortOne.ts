/** PortOne(구 아임포트) JS SDK — 페이지당 스크립트 1회만 주입 */
const PORTONE_SCRIPT_URL = "https://cdn.iamport.kr/js/iamport.payment-1.2.0.js";

const MAX_WAIT_MS = 30000;
const POLL_MS = 100;

/**
 * 데스크톱은 팝업 결제로 iframe 간 postMessage origin 불일치를 피함.
 * 모바일은 m_redirect_url 위주 동작을 위해 레이어(iframe) 유지.
 */
export function preferPortOnePopup(): boolean {
  if (typeof window === "undefined") return false;
  return window.matchMedia?.("(min-width: 769px)")?.matches ?? false;
}

export async function loadPortOne(): Promise<Window["IMP"] | null> {
  if (typeof window === "undefined") return null;
  if (window.IMP) return window.IMP;

  const start = Date.now();
  const existing = document.querySelector<HTMLScriptElement>(
    `script[src="${PORTONE_SCRIPT_URL}"]`
  );

  if (!existing) {
    const script = document.createElement("script");
    script.src = PORTONE_SCRIPT_URL;
    script.async = false;
    document.head.appendChild(script);
    try {
      await new Promise<void>((resolve, reject) => {
        script.onload = () => resolve();
        script.onerror = () => reject(new Error("Failed to load PortOne SDK"));
      });
    } catch {
      /* 네트워크 실패 시에도 아래 폴링으로 대기 */
    }
  }

  while (!window.IMP && Date.now() - start < MAX_WAIT_MS) {
    await new Promise((r) => setTimeout(r, POLL_MS));
  }

  return window.IMP || null;
}

export function getPortOneMerchantCode(): string {
  return (
    process.env.NEXT_PUBLIC_PORTONE_MERCHANT_CODE?.trim() || "imp45866522"
  );
}
