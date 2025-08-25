// 환경변수 설정 파일 - 기본값 사용
// Next.js 15 경고를 피하기 위해 객체 export 대신 상수 export 사용
// nginx 리버스 프록시를 사용하므로 동일 오리진 상대 경로로 호출

// 새로운 명명 규칙 (camelCase)
export const backendOrigin = process.env.NEXT_PUBLIC_BACKEND_ORIGIN || 'http://localhost';
export const frontendOrigin = process.env.NEXT_PUBLIC_FRONTEND_ORIGIN || 'http://localhost:3000';

// 기존 호환성을 위한 export (deprecated)
export const BACKEND_ORIGIN = backendOrigin;
export const FRONTEND_ORIGIN = frontendOrigin;
