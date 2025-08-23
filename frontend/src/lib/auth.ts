export function oauthUrl(provider: "google" | "naver" | "kakao") {
  const base = process.env.NEXT_PUBLIC_BACKEND_ORIGIN || "";
  // 서버사이드에서는 기본값 사용, 클라이언트에서는 동적으로 생성
  const redirectParam = encodeURIComponent(
    typeof window === "undefined" 
      ? "http://localhost/auth/callback" 
      : window.location.origin + "/auth/callback"
  );
  if (base) {
    return `${base}/api/oauth2/authorization/${provider}?redirect_uri=${redirectParam}`;
  }
  return `/api/oauth2/authorization/${provider}?redirect_uri=${redirectParam}`;
}


