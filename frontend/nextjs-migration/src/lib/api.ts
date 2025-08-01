import axios from 'axios';

// 환경별 API 설정
const API_CONFIG = {
  // 로컬 개발 환경
  development: {
    baseURL: 'http://localhost:8090',
    apiPath: '/api',
    frontendURL: 'http://localhost:3000'
  },
  // Docker 환경
  production: {
    baseURL: '',  // 같은 도메인에서 서빙
    apiPath: '/api',
    frontendURL: 'http://localhost'
  }
};

// 현재 환경 감지
const isDevelopment = process.env.NODE_ENV === 'development';
const config = isDevelopment ? API_CONFIG.development : API_CONFIG.production;

// API 기본 URL
export const API_BASE_URL = config.baseURL;
export const API_PATH = config.apiPath;
export const FRONTEND_URL = config.frontendURL;

// 전체 API URL
export const getApiUrl = (endpoint: string) => {
  return `${API_BASE_URL}${API_PATH}${endpoint}`;
};

// API 엔드포인트들
export const API_ENDPOINTS = {
  // 인증 관련
  LOGIN: '/auth/login',
  SIGNUP: '/auth/signup',
  LOGOUT: '/auth/logout',
  WITHDRAW: '/auth/withdraw',
  ME: '/auth/me',
  CHECK_EMAIL: '/auth/check-email',
  CHECK_NICKNAME: '/auth/check-nickname',
  
  // 이메일 인증
  SEND_EMAIL_VERIFICATION: '/auth/email/send-verification',
  VERIFY_EMAIL: '/auth/email/verify',
  
  // 헬스 체크
  HEALTH: '/health'
} as const;

const apiClient = axios.create({
  baseURL: `${API_BASE_URL}${API_PATH}`,
  withCredentials: true,  // 쿠키 포함
  timeout: 10000,  // 10초 타임아웃
});

// 요청 인터셉터
apiClient.interceptors.request.use(
  (config) => {
    console.log('API Request:', config.method?.toUpperCase(), config.url);
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// 응답 인터셉터
apiClient.interceptors.response.use(
  (response) => {
    console.log('API Response:', response.status, response.config.url);
    return response;
  },
  (error) => {
    console.error('API Error:', error.response?.status, error.response?.data);
    return Promise.reject(error);
  }
);

export default apiClient; 