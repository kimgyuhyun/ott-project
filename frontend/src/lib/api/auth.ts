// 동일 오리진 경유

// 로그인 관련 API 함수들

// API 기본 설정: 항상 동일 오리진 프록시 사용
const API_BASE = '';

// 공통 fetch 함수
async function apiCall<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const url = `${API_BASE}${endpoint}`; // '' + '/api/...' => '/api/...'
  
  const response = await fetch(url, {
    ...options,
    credentials: 'include', // 세션 쿠키 포함
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`API Error: ${response.status} ${errorText}`);
  }

  return response.json();
}

// 로그인 API
export async function login(email: string, password: string) {
  return apiCall('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
}

// 회원가입 API
export async function register(email: string, password: string, name: string) {
  return apiCall('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify({ email, password, name }),
  });
}

// 이메일 중복 확인 API
export async function checkEmailDuplicate(email: string) {
  return apiCall<boolean>(`/api/auth/check-email?email=${encodeURIComponent(email)}`);
}

// 로그아웃 API
export async function logout() {
  const response = await fetch('/api/auth/logout', {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`API Error: ${response.status} ${errorText}`);
  }

  // 로그아웃 API는 JSON이 아닌 텍스트를 반환할 수 있으므로 text()로 처리
  const text = await response.text();
  return text;
}

// 현재 사용자 정보 가져오기
export async function getCurrentUser() {
  try {
    return await apiCall('/api/oauth2/user-info');
  } catch (error) {
    // 401 에러는 로그인하지 않은 상태
    if (error instanceof Error && error.message.includes('401')) {
      return null;
    }
    throw error;
  }
}

// 이메일 인증 코드 발송
export const sendVerificationCode = async (email: string): Promise<void> => {
  const response = await fetch(`${API_BASE}/api/auth/send-verification-code?email=${encodeURIComponent(email)}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`인증코드 발송 실패: ${errorText}`);
  }
};

// 이메일 인증 코드 검증
export const verifyCode = async (email: string, code: string): Promise<boolean> => {
  const response = await fetch(`${API_BASE}/api/auth/verify-code?email=${encodeURIComponent(email)}&code=${encodeURIComponent(code)}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`인증코드 검증 실패: ${errorText}`);
  }

  return true;
};
