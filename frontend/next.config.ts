import type { NextConfig } from "next";

/** PortOne/PG 결제 iframe — frame-src 없으면 default-src 'self'만 적용되어 차단됨 */
const CONTENT_SECURITY_POLICY =
  "default-src 'self'; img-src 'self' data: https:; script-src 'self' 'unsafe-inline' https://cdn.iamport.kr; style-src 'self' 'unsafe-inline'; connect-src 'self' https:; frame-src 'self' https:;";

const nextConfig: NextConfig = {
  output: 'standalone', // Docker 빌드를 위한 standalone 모드 활성화
  experimental: {
    forceSwcTransforms: true,
    // 보안 하드닝으로 컨테이너 루트FS가 read-only(.next/server/app 쓰기 불가)이므로
    // ISR 재생성 결과를 디스크에 flush하지 않고 메모리 캐시만 사용한다.
    // (미설정 시 매 revalidate마다 EROFS: read-only file system 에러가 반복 발생)
    isrFlushToDisk: false,
  },
  // 개발 모드에서 콘솔 로그 활성화
  logging: {
    fetches: {
      fullUrl: true,
    },
  },
  compiler: {
    styledComponents: true,
  },
  webpack: (config) => {
    config.resolve.fallback = {
      ...config.resolve.fallback,
      fs: false,
    };
    return config;
  },
  images: {
    // 보안 하드닝으로 frontend 컨테이너 egress(아웃바운드 인터넷)가 완전 차단돼 있어,
    // next/image 서버 최적화기가 외부 포스터(cdn.myanimelist.net 등)를 가져오지 못해 500 → 액박이 났다.
    // unoptimized=true 로 서버 프록시를 끄면 브라우저가 원본 이미지를 직접 로드(CSP img-src https: 허용)하므로
    // egress 차단(채굴기 방어)을 그대로 유지하면서 포스터가 정상 표시된다.
    unoptimized: true,
    dangerouslyAllowSVG: true,
    contentSecurityPolicy: "default-src 'self'; script-src 'none'; sandbox;",
    remotePatterns: [
      {
        protocol: 'https',
        hostname: 'placehold.co',
        port: '',
        pathname: '/**',
      },
      {
        protocol: 'https',
        hostname: 'cdn.myanimelist.net',
        port: '',
        pathname: '/**',
      },
      {
        protocol: 'https',
        hostname: 'cdn.example.com',
        port: '',
        pathname: '/**',
      },
    ],
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          {
            key: "Content-Security-Policy",
            value: CONTENT_SECURITY_POLICY,
          },
        ],
      },
    ];
  },
  async rewrites() {
    // 개발 서버에서만 필요. 기본은 Nginx 리버스 프록시가 처리하므로 상대경로 유지
    const origin = process.env.NEXT_PUBLIC_BACKEND_ORIGIN || process.env.BACKEND_ORIGIN;
    if (!origin) return [];
    const base = origin.replace(/\/$/, '');
    return [
      { source: "/api/:path*", destination: `${base}/api/:path*` },
      // OAuth2 인가/콜백 경로를 개발 환경에서 백엔드로 프록시
      { source: "/login/oauth2/:path*", destination: `${base}/login/oauth2/:path*` },
      // 주의: /oauth2/success, /oauth2/failure 는 프론트 라우팅 페이지
      { source: "/oauth2/(success|failure)", destination: "/oauth2/$1" },
      // 그 외 /oauth2/api 하위만 백엔드로
      { source: "/oauth2/api/:path*", destination: `${base}/oauth2/api/:path*` }
    ];
  },
  eslint: {
    ignoreDuringBuilds: true,
  },
  // typescript: {
  //   // 빌드 차단 방지: 타입 오류가 있어도 빌드 진행
  //   ignoreBuildErrors: true,
  // },
};

export default nextConfig;
