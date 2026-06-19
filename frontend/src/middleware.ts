import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';

// anonId 생성 함수 (Base62 26자)
function generateAnonId(): string {
  const uuid = crypto.randomUUID();
  const bytes = new Uint8Array(16);
  
  // UUID를 바이트 배열로 변환
  const hex = uuid.replace(/-/g, '');
  for (let i = 0; i < 16; i++) {
    bytes[i] = parseInt(hex.substr(i * 2, 2), 16);
  }
  
  // Base64 URL 인코딩 후 26자로 제한
  const base64 = btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, '');
  
  return base64.substring(0, 26);
}

export function middleware(request: NextRequest) {
  const response = NextResponse.next();
  
  // anonId 쿠키 확인
  const anonId = request.cookies.get('anonId');
  
  if (!anonId) {
    // anonId가 없으면 새로 생성하고 쿠키 설정
    const newAnonId = generateAnonId();
    response.cookies.set('anonId', newAnonId, {
      path: '/',
      maxAge: 365 * 24 * 60 * 60, // 365일
      httpOnly: false, // 프론트에서 접근 가능
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'lax'
    });
  }
  
  return response;
}

export const config = {
  matcher: [
    /*
     * Match all request paths except for the ones starting with:
     * - api (API routes)
     * - _next/static (static files)
     * - _next/image (image optimization files)
     * - favicon.ico (favicon file)
     * - public 정적 자산 폴더(banners/icons/images/videos)
     *   → 배너/아이콘 등 자산 요청마다 미들웨어가 도는 불필요한 오버헤드 제거
     */
    '/((?!api|_next/static|_next/image|favicon.ico|banners|icons|images|videos).*)',
  ],
};
