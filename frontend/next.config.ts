import type { NextConfig } from "next";

/** PortOne/PG 결제 iframe — frame-src 없으면 default-src 'self'만 적용되어 차단됨 */
const CONTENT_SECURITY_POLICY =
  "default-src 'self'; img-src 'self' data: https:; script-src 'self' 'unsafe-inline' https://cdn.iamport.kr; style-src 'self' 'unsafe-inline'; connect-src 'self' https:; frame-src 'self' https:;";

const nextConfig: NextConfig = {
  output: 'standalone', // Docker 빌드를 위한 standalone 모드 활성화
  experimental: {
    forceSwcTransforms: true,
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
