// 동일 오리진 경유 // 프론트엔드와 백엔드가 같은 도메인/포트에서 서비스된다는 뜻
// 1. 클라이언트가 http://example.com/ -> nginx (80/443 포트)
// http://exmaple.com/api/auth/login -> nginx (80/443 포트)
// 2. nginx가 내부로 분기: 
// /api/로 시작하면 백앤드(ott-app:8090)로 프록시
// 그 외는 프론트엔드(ott-frontend:3000)로 프록시
// 클라이언트 관점에서는 모든 요청이 같은 도메인(80/443)으로 보이고 실제로로는 nginx가 내부에서 분기함
// 같은 서버어세 프론트엔드와 백엔드를 함께 서비스하고,
// Next.js 프록시나 리버스 프록시를 통해 /api/.. 요청을 백엔드로 전달하는 구조

// 로그인 관련 API 함수들

// API 기본 설정: 항상 동일 오리진 프록시 사용
const API_BASE = ''; // 상대 경로로 요청하면 nginx가 자동으로 백엔드로 프록시
// featch('/api/auth/login') -> nginx (80/443 포트) -> ott-app:8090/api/auth/login으로 전달
// API_BASE = '' → /api/auth/login → nginx가 백엔드로 전달
// API_BASE = 'apple' → apple/api/auth/login → 404 에러
// API_BASE = 'http://localhost:8090' → http://localhost:8090/api/auth/login → 백엔드로 직접 요청 (CORS 문제 가능)
// 브라우저가 자동으로 처리함 만약 현재 페이지가 http://example.com이면
// 경로를 붙임 http://example.com/api/auth/login 이렇게 됨
// feach 함수는 브라우저에서 HTTP 요청을 보내는 함수임 서버에 데이터를 요청하거나 보낼 때 사용
// 백엔드 API를 호출할 때 사용하는 함수임

// 공통 fetch 함수

// 함수 선언식
// async function apiCall<T>(...) { } / 호이스팅됨(선언 전 호출 가능), 제네릭 사용 가능
// 화살표 함수
// const sendVerificationCode = async (email: string) => { } // 호이스팅 안 됨, 제네릭 사용 불가
// 호이스팅이란 변수/함수 선언이 스코프 상단으로 끌어올려지는 동작 즉 코드를 실행하기 전에 함수 선언식이 메모리에 먼저 등록됨
// 그래서 선언 전에도 호출가능
// let/const는 선언 전 접근 불가임
async function apiCall<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  // 제네릭, 호이스팅이 가능한 비동기함수 apiCall<T> 선언
  // endpoing에는 '/api/auth/login' 이렇게 문자열로 전달되고고 API 경로 문자열임 즉, 백엔드 API 경로임
  // options에는 HTTP 요청을 초기화할 때 필요한 설정 정보들이 객체 형태로 들어오는데
  // RequestInit는 fetch 함수에서 두 번째 인자로 사용되는 "옵션 객체"의 타입 이름임 이 객체 안에는
  // method: 요청 방식 (GET, POST, PUT, DELETE 등)
  // haders: 헤더 정보 (Content-Type, Authorization 등)
  // body: 요청 본문 데이터 (JSON 문자열 등)
  // credentials: 인증 정보 포함 여부 (include, same-origin, omit)
  // redirect: 리다이렉트 처리 (follow, error, manual)
  // optins 파라미터에는 HTTP 요청에 필요한 정보가 담긴 객체가 들어오고 이 정보는 RequestInit 타입이어야함
  // = {}이 파라미터의 기본값을을 "빈 객체"로 정해준것임
  // 이 함수의 options를 아예 안 넘기면, 지동으로 기본값이 빈 객체가 사용됨
  // apiCall 함수를 호출할때 options를 안넘기면 기본값인 GET으로 요청되서 POST, PUT, DELETE는 지정해줘야함
  // body,hedaers도 필요에 따라 추가
  // promise<T>는 앞으로(비동기 작업이 끝나면) T 타입의 데이터를 반환하겠다다는 "약속"임
  const url = `${API_BASE}${endpoint}`; // '' + '/api/...' => '/api/...'
  // 재할당 불가 변수고 백틱 + ${...}는 "템플릿 리터럴"이라고 해서, 문자열 안에 변수나 표현식을 쉽게 넣을수 있게 해줌
  // API_BASE 변수는 빈 문자열이고 endpoint에 'api/auth/login' 이렇게 들어오면 두 값을 붙여서
  // 최종 URL로 '/api/auth/login' 이렇게 만들어지고 이걸 url에 할당함
  
  const response = await fetch(url, { // HTTP 요청 옵션에 세션 쿠키, 헤더 기본 정보 포함해서 서버에 요청 전송하고
    // 응답이오면 재할당 불가 response 변수에 저장함
    // fetch 함수로 url에 요청을 보내고 응답을 받음
    // 재할당 불가 변수 response에 결과(응답)을 저장함 awit은 비동기 처리임임
    ...options, // ...은 스프레드 문법이고 options 객체 안의 모든 속성을 여기에 펼쳐서(복사해서) 추가해준다는 뜻
    credentials: 'include', // 세션 쿠키 포함
    // HTTP 요청에 "쿠키"와 같은 인증정보를 보내는 옵션, 'include'는 반드시 브라우저 쿠키(로그인 세션)를 포함해서 요청한다는뜻
    // 로그인/인증이 필요한 AJAX 요청에서 자주씀, 여긴 fetch를 사용했음음
    headers: { // 요청 헤더 정보 지정
      'Content-Type': 'application/json', // 요청 본문(body)이 JSON임을 서버에 알리는것임
      ...options.headers, // 전달받은 options에 추가/수정된 headers가 있으면 덮어씌움
      // options에서 기본으로 제공되는 헤더가 없기에 여기서 headers 정보를 지정하고 기본정보를 추가함
      // 기본 헤더는 함수 내부에서 자동으로 주입해주고 필요시 직접 추가할수있게하면 실수를 줄일수있음
    },
  });

  if (!response.ok) { // 만약 response.ok가 false(=응답이 성공하지 않음)이면 아래 코드 실행
    const errorText = await response.text(); // 응답 본문(body)을 문자열로 읽어와서 errorText에 저장함
    throw new Error(`API Error: ${response.status} ${errorText}`); // Http 상태 코드랑 응답 본문(body)를 문자열로 합쳐서
    // 에러 객체를 만들고 던짐
  }

  // response.ok가 true면 즉 성공한 응답이면면
  return response.json(); // 응답 본문(body)을 JSON 문자열에서 JavaSCript 객체로 변환해서 반환하는것
}

// 로그인 API
export async function login(email: string, password: string) {
  // 외부에서 import해서 사용가능한 비동기 함수 function이 붙으면 호이스팅,제네릭이 가능하지만 export여서 호이스팅 불가함
  // email 파라미터는 String 형식으로 받아야함, password 파라미터도 string 형식으로 받아야함
  // 참고로 자바스크립트 함수는 리턴 타입이 지정 불가하고 타입스크립트는 리턴타입을 타입추론해서 생략 가능함
  return apiCall('/api/auth/login', { // apiCall 함수를 호출해서 return 값을 바로 반환하는 형태임
    // 파라미터로 백엔드 api 경로, 요청 본문을 apiCall 함수에 태워보냄
    method: 'POST', // 요청형식 POST로 지정
    body: JSON.stringify({ email, password }), // JSON.stringify는 Javascript 내장 함수임
    // {email, password}는 객체 리터럴 문법이고 축약식임 이걸로 JavaScript 객체를 만들고 그걸 함수에 넘겨주면
    // JSON 문자열이 반환되고 그게 요청 본문으로 저장됨
    // 이 login 함수는 그냥 로그인 폼폼에서 string이랑 password보내면 그걸 Javascript 객체로 만들고 문자열로 변환한뒤
    // apiCall에 작성해놓은 백엔드 API경로랑 요청정보를 함수에 태워보내서 리턴값으로 Javascript 객체를 받고 그대로 반환해줌
  });
}

// 회원가입 API
export async function register(email: string, password: string, name: string) {
  // import 해서 사용 가능한 비동기 함수 호이스팅은 불가
  // 파라미터로 email, password, name을 받고 이건 모두 String 타입이어야함 함수명은 register
  return apiCall('/api/auth/register', { // apiCall 함수를 호출해서 return 값을 바로 반환하는 형태임
    method: 'POST', // 요청형식 POST로 
    body: JSON.stringify({ email, password, name }),
    // 함수 호출할때 파라미터로 받은 email, password, name을 Javascript 객체로 만들고 이걸 문자열로 변환해서 요청본문(Body)에 저장
    // 이 register 함수는 회원가입 폼에서 email, password, name을 보내면 그걸 Javascript 객체로 만들고 문자열로 변환한뒤
    // apiCall에 작성해놓은 백엔드 API경로랑 요청정보를 함수에 태워보내서 리턴값으로 Javascript 객체를 받고 그대로 반환해줌
  });
}

// 이메일 중복 확인 API
export async function checkEmailDuplicate(email: string) {
  // import 해서 사용 가능한 비동기 함수 호이스팅은 불가
  // 파라미터로 email을 받고 이건 String 타입이어야함 함수명은 checkEmailDuplicate
  return apiCall<boolean>(`/api/auth/check-email?email=${encodeURIComponent(email)}`);
  // URL 쿼리 파라미터로 데이터를 포함해서 보내는 URL 쿼리 파라미터 형식임
  // URL 쿼리 파라미터는 경로 뒤에 ?로 시작하는것 key=value 형태임
  // 형식은 ?key=value 이거고 예시는 ?email=test@example.com 이거고 여기서 email이 key고 test@example.com이 value임
  // ?email=test@example.com&page=1&limit=10 / &는 파라미터 구분자임임
  // ↑key1    ↑value1      ↑key2 ↑V2 ↑key3 ↑value3
  // apiCall 함수를 호출해서 return 값을 바로 반환하는 형태임
  // <boolean>으로 제네릭 타입을 부여했으니 apiCall 함수의 리턴값은 true, false 중 하나를 반환하게 됨
  // 백틱으로 문자열을 합치고 ${...}로 변수를 인식하게해줌
  // encodeURIComponent는 URL 인코딩을 해주는 함수임 URL 쿼리 파라미터에 직접 넣으면 특수문자 떄문에 문제가 생길 수 있음
  // 그래서 이메일에 특수문자나 공백 등이 있으면 인코딩해줘야함
  // 그리고 HTTP 요청 정보를 안보내서 기본값으로 세팅될텐데 요청방식은 GET 으로 기본값세팅되고 요청본문(Body)는 빈값임임
  // 회원가입 폼에서 이메일 입력하고 중복확인 버튼 누르면 유저가 입력한 email값이 여기로 전달되고 그 값을
  // 백엔드 API 경로에 인코딩해서 문자열로 합치고 apiCall 함수에 태워보내면 true 혹은 false가 반환되고
  // 그 결과로 중복여부를 알 수 있음 true면 중복 false면 중복이 아님

}

// 로그아웃 API
export async function logout() {
  // import 해서 사용 가능한 비동기 함수 호이스팅은 불가
  const response = await fetch('/api/auth/logout', { // fetch함수에 백엔드경로랑 HTTP 요청 방식과 헤더 정보를 태워보내면
    // 응답값을 재할당 불가 변수 response에 저장함
    method: 'POST', // 요청형식 POST로 지정정
    credentials: 'include',// 세션 쿠키 포함
    headers: {
      'Content-Type': 'application/json', // 요청 본문(body)이 JSON임을 서버에 알리는것임
      // 로그아웃은 사실 요청본문이없어서 헤더 정보가 불필요 세션 쿠키만 보내면됨됨
    },
  });

  if (!response.ok) { // 만약 response.ok가 false(=응답이 성공하지 않음)이면 아래 코드 실행
    const errorText = await response.text(); // 응답 본문(body)을 문자열로 읽어와서 errorText에 저장함
    throw new Error(`API Error: ${response.status} ${errorText}`); // Http 상태 코드랑 응답 본문(body)를 문자열로 합쳐서
    // 에러 객체를 만들고 던짐
  }

  // 로그아웃 API는 JSON이 아닌 텍스트를 반환할 수 있으므로 text()로 처리
  const text = await response.text(); // 응답 본문(body)을 문자열로 읽어와서 text에 저장함
  return text; // 문자열을 반환
  // 로그인/회원가입은 JSON 응답 -> response.json()을 사용함
  // 로그아웃은 텍스트 응답(예: 로그아웃 성공") -> response text() 사용
  // 서버 응답 형식에 따라 다름
  // 보통 요청할때 JSON 문자열로 요청하고 반환도 JSON 문자열로 받고 이걸 JavaSCript 객체로 바꿔서 사용하는듯
  // JSON 상하차가 여기서 일어나는듯
  // 요청은 JavasSCript 객체를 만들고 JSON.stringify()로 JSON 문자열로 변환한뒤 요청본문으로 전달하고 이걸 서버로 전송송
  // 응답은 서버가 JSON 문자열로 보내고 그걸 response.json()으로 JavaScript 객체로 변환함
}

// 현재 사용자 정보 가져오기
export async function getCurrentUser() { // import 해서 사용 가능한 비동기 함수 호이스팅은 불가
  try { // 에외처리할려고 try catch문 사용
    return await apiCall('/api/oauth2/user-info'); // apiCall 함수를 호출해서 return 값을 바로 반환하는 형태임
    // apiCall이 성공하면 JavaSCript 객체를 그대로 반환함
    // credentials: 'include', // 세션 쿠키 포함 이 옵션이 있으면 브라우저가 자동으로 세션 쿠키를 포함해서 요청을 보내서
    // 서버는 이 쿠키로 현재 로그인한 사용자를 식별 가능. 그래서 여기서는 따로 HTTP 요청 정보를 안보내줘도됨.
  } catch (error) { // apiCall 함수에서 응답오류가나면 error 객체를 던지는데 이걸 catch문에서 처리함
    // 401 에러는 로그인하지 않은 상태
    if (error instanceof Error && error.message.includes('401')) { // error 객체가 Error 타입이고
    //  message 속성에 '401'이 포함되어있으면 아래 코드 실행
      return null; // 401 에러는 로그인하지 않은 상태이므로 null을 반환
    }
    throw error; // 그 외 에러는 그대로 던짐
  }
}
// GET 요청은 요청 본문이없고 POST는 요청 본문이 있음

// 이메일 인증 코드 발송
export const sendVerificationCode = async (email: string): Promise<void> => {
  // 파라미터로 email을 받고 이건 string 타입이어야하고 
  // Promise<void>는 리턴값이 없다는 뜻이고 이 함수는 리턴값이 void 타입이어야한다고 약속한것
  // () => {} 는 익명 함수이자 화살표 함수고 함수 정의할 때 사용
  // 아래 동작 정의를 import 해서 사용 가능한 재할당 불가 변수 sendVerificationCode에 할당함
  // sendVerificationCOde는 import 가능 재할당 불가 비동기 함수가됨됨
  const response = await fetch(`${API_BASE}/api/auth/send-verification-code?email=${encodeURIComponent(email)}`, {
    //  유저가 회원가입 폼에서 입력한 email값이 여기에 전달되고 그걸 URL 쿼리 파라미터에 추가함
    // fetch 함수에 상대경로랑 쿼리 파라미터에 추가한값을 합쳐서 전달
    method: 'POST', // 요청형식 POST로 지정
    headers: {
      'Content-Type': 'application/json', // 요청 본문(body)이 JSON임을 서버에 알리는것임
    },
  });

  if (!response.ok) { // 만약 response.ok가 false(=응답이 성공하지 않음)이면 아래 코드 실행
    const errorText = await response.text(); // 응답 본문(body)을 문자열로 읽어와서 errorText에 저장함
    throw new Error(`인증코드 발송 실패: ${errorText}`); // Http 상태 코드랑 응답 본문(body)를 문자열로 합쳐서
    // 에러 객체를 만들고 던짐
  }
};

// 이메일 인증 코드 검증 // response.ok가 true면 즉 성공한 응답이면 여기가 실행
export const verifyCode = async (email: string, code: string): Promise<boolean> => {
  // 파라미터로 email, code를 받는데 이건 string 타입이어야함
  // Promise<boolean>는 리턴값이 boolean 타입이어야한다고 약속한것 true, false 둘 중 하나를 반환하게됨
  // 화살표 함수로 함수 정의하고 이걸 verifyCode에 할당함
  // verifyCode는 import 해서 사용 가능한 재할당 불가 비동기 함수가됨
  const response = await fetch(`${API_BASE}/api/auth/verify-code?email=${encodeURIComponent(email)}&code=${encodeURIComponent(code)}`, {
    // fetch 함수에 상대경로랑 쿼리파라미터에 유저가 입력한 이메일이랑 인증코드를 추가해서 전달
    method: 'POST', // 요청형식 POST로 지정
    headers: {
      'Content-Type': 'application/json', // 요청 본문(body)이 JSON임을 서버에 알리는것임
    },
  });

  if (!response.ok) { // 만약 response.ok가 false(=응답이 성공하지 않음)이면 아래 코드 실행
    const errorText = await response.text(); // 응답 본문(body)을 문자열로 읽어와서 errorText에 저장함
    throw new Error(`인증코드 검증 실패: ${errorText}`); // Http 상태 코드랑 응답 본문(body)를 문자열로 합쳐서'
    // 에러 객체를 만들고 던짐
  }

  return true; // 인증 코드 검증 성공하면 true를 반환
};

// URL는 HTTP 요청에 제일 첫줄이고 백엔드 API 경로 + 쿼리 파라미터(있을 수도, 없을 수도)
// hedaers는 첫 줄 아래에 있는 별도 섹션임