# OTT Project Frontend

## 환경변수 설정

프로젝트 루트에 `.env.local` 파일을 생성하고 다음 내용을 추가하세요:

```bash
# 백엔드 API 기본 URL
NEXT_PUBLIC_BACKEND_ORIGIN=http://localhost:8090

# 프론트엔드 기본 URL  
NEXT_PUBLIC_FRONTEND_ORIGIN=http://localhost:3000
```

## 주요 기능

### 🔐 인증 시스템
- **소셜 로그인**: Google, Kakao, Naver OAuth2 지원
- **이메일 로그인**: 기존 계정으로 로그인
- **단계별 회원가입**: 이메일 인증을 통한 안전한 회원가입
  - 1단계: 이메일 입력 + 인증코드 발송
  - 2단계: 인증코드 입력 + 검증
  - 3단계: 비밀번호 + 닉네임 입력 + 회원가입

### 📱 UI 컴포넌트
- `EmailAuthForm`: 기본 로그인/회원가입 폼
- `StepByStepRegisterForm`: 단계별 회원가입 폼
- `SocialButton`: 소셜 로그인 버튼
- `Modal`: 모달 컴포넌트

## 개발 서버 실행

```bash
npm run dev
```

## 빌드

```bash
npm run build
```
