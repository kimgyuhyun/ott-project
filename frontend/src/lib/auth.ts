export function oauthUrl(provider: "google" | "naver" | "kakao") {
  const base = process.env.NEXT_PUBLIC_BACKEND_ORIGIN || "";
  const redirectParam = encodeURIComponent(
    typeof window === "undefined" ? "" : window.location.origin + "/auth/callback"
  );
  if (base) {
    return `${base}/api/oauth2/authorization/${provider}?redirect_uri=${redirectParam}`;
  }
  return `/api/oauth2/authorization/${provider}?redirect_uri=${redirectParam}`;
}


