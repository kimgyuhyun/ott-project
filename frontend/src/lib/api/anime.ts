// 동일 오리진 경유

// 애니메이션 관련 API 함수들

// API 기본 설정: 항상 동일 오리진 프록시 사용 (Nginx/Next rewrites 경유)
const API_BASE = '/api'; // 상대 경로로 요청하면 nginx가 자동으로 백엔드로 프록시
// featch('/api/anime?page=0&size=20&sort=id') -> nginx (80/443 포트) -> ott-app:8090/api/anime?page=0&size=20&sort=id으로 전달
// API_BASE = '/api' → /api/anime?page=0&size=20&sort=id → nginx가 백엔드로 전달
// API_BASE = 'apple' → apple/api/anime?page=0&size=20&sort=id → 404 에러
// API_BASE = 'http://localhost:8090' → http://localhost:8090/api/anime?page=0&size=20&sort=id → 백엔드로 직접 요청 (CORS 문제 가능)
// 브라우저가 자동으로 처리함 만약 현재 페이지가 http://example.com이면
// 경로를 붙임 http://example.com/api/anime?page=0&size=20&sort=id 이렇게 됨
// feach 함수는 브라우저에서 HTTP 요청을 보내는 함수임 서버에 데이터를 요청하거나 보낼 때 사용
// 현재 auth.ts는 빈 문자열을 AP_BASE로 사용하고 있어 매번 /api/를 반복해서 써야해서 중복 /api/가 여러 곳에 반복됨
// 그래서 여기 anime.ts처럼 API_BASE = '/api' 이렇게 설정해서 중복을 제거해서 apiCAll(/auth/login')이렇게 호출하는게 더나음음

// 공통 fetch 함수
async function apiCall<T>(endpoint: string, options: RequestInit = {}): Promise<T | null> {
  // 제네릭, 호이스팅이 가능한 비동기 함수 apiCall<T> 선언
  // 참고로 화살표 함수 + 함수 표현식 + 변수 할당 형태로 선언하면 호이스팅 안됨
  // 메모리에 변수만 올라가고 함수 표현식은 안올라와서 사용을 못함.
  // 호이스팅은 같은 파일(같은 스코프)안에서만 의미가 있음
  // 함수 선언이 위로 끌어올려지기 때문에(호이스팅) 코드상으로는 아래에 있어도, 엔진 입장에선 이미 알고 있음
  // 그냥 이 파일 안에서, 선언보다 위에서 쓸 수 있냐 없냐의 문제
  // export 키워드는 외부 파일에서 import해서 사용하는데 이 때 함수가 메모리에 올라감 호이스팅은 전혀 상관없음
  // 호이스팅은 필수는 아니고 코드 위쪽에 메인 흐름 / 무슨 일을 하는지를 작성하고
  // 아래쪽에는 위에서 쓰인 함수들을 함수 선언식으로만 정의하는것 호이스팅 덕분에 아래에 있어도 위에서 호출 가능
  // 이러면 위에서 흐름 파악 / 아래에서 세부로직 파악하니까 가독성/유지보수가 좋아짐
  // 하지만 요즘 화살표 함수가 많이 쓰이는데 this 바인딩을 신경 안써도되고 변수에 담아서 다루기 편함
  // 짧은 함수/ 콜백 표현하기 좋고 함수 선언식은 여전히 top-level 유틸에 많이씀
  // 화살표 함수를 쓰기 좋은곳은 익명 콜백 위치, 상태/props에 합수 자체를 값으로 들고 다니고 싶을 때나 this 신경 쓰기 싫을떄임
  // 화살표 함수는 자기만의 this를 안만들고, 바깥(상위 스코프)의 this를 그대로 씀
  // 그래서 요즘 프론트에서 this를 거의 안쓰고, 써야 할 떄는 보통 클래스 문법을 쓸 떄뿐임임
  // endpoint에는 '/anime?page=0&size=20&sort=id' 이렇게 문자열로 전달되고 API 경로 문자열임 즉, 백엔드 API 경로임
  // options에는 HTTP 요청을 초기화할 때 필요한 설정 정보들이 객체 형태로 들어오는데
  // RequestInit는 fetch 함수에서 두 번째 인자로 사용되는 "옵션 객체"의 타입 이름임 이 객체 안에는
  // method: 요청 방식 (GET, POST, PUT, DELETE 등)
  // haders: 헤더 정보 (Content-Type, Authorization 등)
  // body: 요청 본문 데이터 (JSON 문자열 등)
  // credentials: 인증 정보 포함 여부 (include, same-origin, omit)
  // Promise<T | null>은 이 함수는 Promise를 반환하고, 그 Promise가 완료(resolve)되면 t 타입또는 null 타입 중 하나의 값을 반환한다는 뜻
  // async 키워드는 비동기 함수를 만들고 async function은 자동으로 Promise를 반환함
  // 자바는 제네릭 타입을 반드시 명시해서 호출해야하는데
  // TypeScript는 타입 추론으로 생략가능함 파라미터로부터 타입 추론함
  const url = `${API_BASE}${endpoint}`; // '/api' + '/anime?page=0&size=20&sort=id' => '/api/anime?page=0&size=20&sort=id'
  
  try { // 예외처리를 위해 try catch문 사용
    // 네트워크 연결 상태 확인
    if (!navigator.onLine) { // 만약 브라우저가 네트워크에 연결이 되어 있지 않으면 fasle를 반환하고 이걸 부정 연산자로 true로 바꿔서 아래라인 실행
      // 즉 네트워크에 연결이 안되어있으면 실행함함
      // navigate는 브라우저의 전역 객체 (Web API)임 import 없이 바로 사용 가능하고 브라우저/네트워크에서 정보를 제공해줌
      // navigator.onLine은 브라우저가 네트워크에 연결되었는지 여부를 확인하고 ture / false를 리턴해줌
      throw new Error('네트워크 연결을 확인해주세요.'); // 에러 객체 생성해서 던짐짐
    }

    const response = await fetch(url, {
      // 비동기 함수 feach()를 호출해서 파라미터로 위에서 만든 url와 옵션 객체를 전달해서 서버에 요청을 보내고 응답받은걸
      // 재할당 불가 변수 response에 할당함 참고로 await이라 일정시간동안 대기하고 아래라인 실행하지않음 다른 함수는 호출가능
      ...options,
      // ...은 스프레드 문법이고 options 객체 안의 모든 속성을 여기에 펼쳐서(복사해서) 추가해준다는 뜻
      credentials: 'include', // 세션 쿠키 포함
      headers: { // 요청 헤더 정보 지정
        'Content-Type': 'application/json', // 요청 본문(body)이 JSON임을 서버에 알리는것
        ...options.headers, // 전달받은 options에 추가/수정된 hedaers가 있으면 덮어씌움
        // options에서 기본으로 제공되는 헤더가 없기에 여기서 headers 정보를 지정하고 기본정보를 추가함
        // 기본 헤더는 함수 내부에서 자동으로 주입해주고 필요시 직접 추가할수있게하면 실수를 줄일수있음
        // 그러니까 기본 헤더는 여기서 지정해주고 요청 방식이나 인증 정보같은거 필요하면 호출할떄 추가로 넘기면 추가(덮어씌우기)해줌
        // 참고로 headers 요청 방식에 기본값은 GET이고 요청 본문은 없음
      },
    });

    if (!response.ok) { // 만약 응답이 성공하지 않으면 false를 반환하고 이걸 부정 연산자로 true로 바꿔서 실행
      // 즉 올바르지 않은 응답일때 실행
      const errorText = await response.text();// 응답 본문(body)을 문자열로 읽어와서 errorText에 저장함
      throw new Error(`API Error: ${response.status} ${errorText}`); // Http 상태 코드랑 응답 본문(body)를 문자열로 합쳐서
      // 에러 객체를 만들고 던짐
    }

    
    const contentType = response.headers.get('content-type');
    // 응답 헤더에서 content-type 값을 가져와서 재할당 불가 변수 contentType에 저장함
    if (!contentType || !contentType.includes('application/json')) { // 논리합 사용용
      // 조건1: 만약 contentType이 없거나 null / undeFiend인 경우 즉 falsy값인 경우
      // falusy 값: null, undefiend, "", 0, false, NaN
      // 즉 falsy 값들은 fasle로 평가됨 그래서 값이 없거나 null / undeFiend인 경우 즉, falsy 값인 경우
      // false로 평가되고 여기에 부정 연산자로 붙여서 조건식을 성립함 즉 contentType 값을 알수없을때 성립
      // 조건2: contentType 값이 application/json 값이 아닌 경우 성립
      // .incluides() 함수는 문자열에 특정 값이 포함되어 있는지 확인함
      // 즉 응답 헤더에서 뽑아온 contentType 값을을 알수없거나 또는  application/json 값이 아닌 경우 조건식이 성립되서 아래라인이 실행됨됨
      // 404나 204 같은 경우는 정상적인 HTTP 응답이고 본문이 없거나 JSON이 아닐 수 있어서 에러로 처리하지 않고 null로 반환함
      if (response.status === 404 || response.status === 204) {
        // 만약 응답 상태가 404 또는 204인 경우 즉, 리소스를 찾을 수 없을때 실행
        console.log('리소스를 찾을 수 없습니다 (404/204):', endpoint); // 리소스를 찾을 수 없을때 콘솔에 출력
        return null; // null을 반환하고 함수 종료
      }
      // 애니메이션 ID가 0인 경우는 유효하지 않은 요청
      if (endpoint.includes('/anime/0')) {
        // apiCall 함수 호출시 전달된 endpoint 경로 문자열에서 .incldues 함수를 체인호출해서
        // /anime/0이 포함되어있는지 확인 포함되면 true 포함되지 않으면 false를 반환함
        // true면 유효하지 않은 요청이고 여기가 실행
        console.log('유효하지 않은 애니메이션 ID (0):', endpoint); // 유효하지 않은 요청이라고 콘솔에 출력
        return null; // null을 반환하고 함수 종료
      }
      // contetType이 falsy거나 JSON이 아니고, 404나 204도 아니고, /anime/0도 아닌 경우 실행
      // 예상치 못한 응답(500 에러 + HTML)일 때 경고 출력력
      console.warn('응답이 JSON이 아닙니다:', contentType, 'endpoint:', endpoint); // 콘솔에 경고 출력
      return null; // null을 반환하고 함수 종료
    }

    // 응답 텍스트를 먼저 확인
    const text = await response.text();
    // 응답 본문(body)에 문자열을 읽어와서 재할당 불가 변수 text에 할당 
    // 참고로 비동기 함수는 Promise를 즉시 반환하는 함수고 실제 작업은 백그라운드에서 진행함
    // 동기: 작업이 완료될 때까지 기다림 -> 결과 반환
    // 비동기: Promise를 즉시 반환 -> 백그라운드에서 작업 진행 -> 나중에 결과 전달
    // 동기 함수는 작업 완료될 때 까지 기다리니 await 처럼 작동하고 / 다른 작업 못함
    // 비동기 함수는 Promise를 즉시 반환하고 await 키워드는 값이 들어올 때까지 대기한다는뜻임 // 다른 작업 가능
    // 정확히 await 키워드는 비동기 함수를 동기 함수처럼 쓰게 해주는 키워드임 
    // 실행 영역은 백그라운드이지만, await 키워드를 붙이면 메인 스레드에서 기다림
    // 지금까지 설명을 들으면 어 그럼 백그라운드에 동기 함수를 띄워두면 되지않나? 왜 귀찮게 비동기함수 + await 조합을 사용하지라 할 수 있는데
    // 백그라운드에서 동기 함수를 쓰면 메인 스레드가 블로킹됨
    // 메인 스레드가 블로킹 된다는건 그 작업이 끝날 때까지 다른 작업을 실행할 수 없다는 뜻
    // 그래서 백그라운드에서 동기 함수를 사용하면 블로킹 당해서 그 함수가 끝날때까지 다른 함수를 사용할 수 없어서 병목 현상 발생
    // 이걸 해결하기 위해 백그라운드에서 비동기 함수 + await 조합을 사용해서 백그라운드에 메인 스레드에서 기다리게함
    // 이러면 메인 스레드를 블로킹안하고 응답 올때까지 대기하다가 응답이오면 즉시 반환하고 메인 스레드가 블로킹 당하지않으니
    // 이 함수를 대기하면서 다른 함수 실행 가능함 병목현상도 없음 이게 바로 비동기 함수 + await 조합을 사용하는 이유임 
    if (!text || text.trim() === '') {
      // 만약 text 값이 falsy거나 또는 빈 문자열인 경우 실행
      console.warn('빈 응답을 받았습니다'); // 콘솔에 경고 출력
      return null; // null을 반환하고 함수 종료
    }

    try { // 예외처리를 위해 try catch문 사용
      return JSON.parse(text); // 모든 조건을 무사 통과하면 여기가 리턴됨
      // JSON은 JAVASCRIPT의 전역 객체 (브라우저가 제공)
      // import 없이 바로 사용 가능 JSON 데이터를 다루는 유틸리티 제공
      // JSON.parse()는 서버에서 응답 받은 JSON 문자열을 값 유무를 체크한 다음 있으면면 JavaScript 객체로 변환하고 이걸 반환해줌
      // res.json()은 자동 파싱이지만 빈 응답 체크가 어려움 왜나하면 바로 파싱해서 Javascript 객체로 변환하고 리턴하기때문
      // responset.text 로 응답 본문에 문자열을 읽어오고
      // JSON.parse()로 빈 응답을 먼저 체크한 뒤 파싱해서 Javascript 객체로 변환하기떄문에에 빈 응답을 체크할 수 있음
    } catch (parseError) { // JSON.parse() 함수 실행 중 에러가 나면, 자바스크립트 엔진이 그 애러 객체를
      // catch(parseError)안으로 전달해줌
      console.error('JSON 파싱 실패:', parseError); // 콘솔에 에러 출력
      console.error('응답 텍스트:', text); // 응답 본문(body)을 콘솔에 출력
      throw new Error(`JSON 파싱 실패: ${parseError instanceof Error ? parseError.message : 'Unknown error'}`);
      // ${}로 변수값을 문자열에 포함시키고 ``백틱으로 문자열을 합침 // 참고로 instanceof 연산자는 객체의 타입을 확인하는 연산자임
      // 저 삼항 연산자 뜨은 parseError에 객체가 Error 객체면 parseError.message 값을 문자열로 변환하고 Error 객체로 던짐
      // parseError가 Error 객체가 아니면 'Unknown error' 문자열을 던짐
    }
  } catch (error) { // 위에 catch에서 던진 Error 객체를 다시 받음
    // 네트워크 오류인지 확인
    if (error instanceof TypeError && error.message === 'Failed to fetch') {
      // 에러 객체가 TypeError 타입이고 error.message 값이 'Failed to fetch' 문자열인 경우 실행함
      throw new Error('서버에 연결할 수 없습니다. 백엔드 서버가 실행 중인지 확인해주세요.'); // 에러 객체를 만들고 던짐
       // instanceOF에 자주 사용하는 에러 타입
       // Error - 기본 에러
       // TypeError - 타입 에러 (fetch 실패 등)
       // SynteaxError - 구문 에러 (JSON 파싱 실패 등)
       // ReferenceError - 참조 에러 (undefined 접근 등)
       // RangeError - 범위 에러 (배열 인덱스 범위 초과 등)
    }
    // 기존 에러는 그대로 전달
    throw error;
  }
}

// 애니메이션 목록 조회 (홈페이지 메인)
export async function getAnimeList(page: number = 0, size: number = 20, sort: string = 'id') {
  // import해서 사용할 수 있는 비동기 함수 getAnimeList 선언
  // 파라미터로 page, size를 number 타입으로 받고 각각 기본값은 0, 20으로 세팅
  // 파라미터 sort는 string 타입으로 받을 건데, 만약에 값이 안들어오면 'id'를 기본값으로 사용할거야라는뜻뜻
  // getAnimeList(0, 20); / sort는 'id'로 설정됨
  // getAnimeList(0, 20, 'popular'); // sort는 'popular'로 설정됨
  // 타입스크립트는 인자에 기본값을 설정해둘수 있어서 편함
  // 자바는 파라미터에 기본값을 설정할 수 없어서 메서드 오버로딩이나 빌더패턴을 사용해야함
  // 오버로딩은 선택적 파라미터가 1~2개 정도이고 기본값이 명확할 때 / 파라미터 하나 안줘도 기본값으로 리턴해주고싶을때때
  // 빌더 패턴은 선택적 파라미터가 많을 때(3개 이상) 조합이 다양할 때 사용함 / 값을 비워둬도 될 때때
  // 빌더클래스에 보통 기본값이 필요하면 기본값을 명시해둠
  // .builder()를 호출해서 사용할떄 값을 명시해서 사용하면 기본값이 있을 시 덮어쓰고 기본값이 없으면 그 값을 설정하는 방식임
  // 자바에서 필드만 정의해두면 선택적 파라미터처럼 쓸수있음 null을 허용하기 떄문임
  // TypeScript는 ?을 붙여야 선택적 파라미터를 사용할 수 있음 null 허용을 안하기 때문
  return apiCall(`/anime?page=${page}&size=${size}&sort=${encodeURIComponent(sort)}`);
  // 파라미터로 받은 page, size, sort를 ${}에 넣어서 값을 문자열로 만들고 apiCall에 endpoint에 태워보냄
  // apicAll에서 올바른 응답이 나오면 JSON.parse(text) 값이 return으로 오는데 이건 Javacscript 객체고
  // 이걸 반환받은 뒤 바로 리턴해주는 형식임
  // encodeURIComponent() 함수는 URL 인코딩 함수고 sort 값을 URL에서 안전하게 사용할 수 있도록 인코딩 해주는 역할
  // URL에는 특수문자(&, =, ?, 공백 등)를 그대로 쓰면 문제가 생길 수 있어서임
  // URL 경로 + 쿼리 파라미터라 아마 GET 요청
  // GET 욫어에 필요한 데이터는 쿼리 파라미터로 전달하고
  // ?key=value&key=value&key=value 형식으로 여러 파라미터를 문자열로 보내고 그런식
}

// 범용 목록 조회(필터/정렬/페이지) - 필터링 기능 유지, 응답 처리만 getAnimeList와 동일
export async function listAnime(params: {
  // import해서 사용하는 비동기 함수 
  // { ... } : 객체 타입을 인라인으로 정의 / 자바에서는 별도 클래스가 필요요
  // = {}: 기본값이 빈 객체 {}라는 뜻
  // paramse에는 필터 조건 객체가 들어옴
  // 필터 조건 객체는 DB에서 오는게 아니라 프론트에서 만든 Javascript 객체임임
  // paramse 객체에 타입을 여기서 인라인으로 정의함
  // 인라인 타입 정의는 이름이 없음 한 곳에서만 쓰면 인라인으로 정의함 재사용은 불가
  // 여로 곳에서 사용할꺼면 별도로 정의해둬야함
  // listAnime 함수에서만 쓰니까 객체 리터럴 + 인라인으로 정의해두고 재사용 안할꺼니 이름도 안붙인것
  status?: string | null; // status는 선택적 속성(optinal)이고 string 타입 또는 null 타입 (유니온 타입) 유니온 타입은 여러 타입 중 하나라는 뜻뜻
  genreIds?: number[] | null; // genreIds는 선택적 속성(optinal)이고 number 타입 배열 또는 null 타입 (유니온 타입)
  tagIds?: number[] | null; // tagIds는 선택적 속성(optinal)이고 number 타입 배열 또는 null 타입 (유니온 타입)
  minRating?: number | null; // minRating는 선택적 속성(optinal)이고 number 타입 또는 null 타입 (유니온 타입)
  year?: number | null; // year는 선택적 속성(optinal)이고 number 타입 또는 null 타입 (유니온 타입)
  quarter?: number | null; // quarter는 선택적 속성(optinal)이고 number 타입 또는 null 타입 (유니온 타입)
  type?: string | null; // type는 선택적 속성(optinal)이고 string 타입 또는 null 타입 (유니온 타입)
  isDub?: boolean | null; // isDub는 선택적 속성(optinal)이고 boolean 타입 또는 null 타입 (유니온 타입)
  isSubtitle?: boolean | null; // isSubtitle는 선택적 속성(optinal)이고 boolean 타입 또는 null 타입 (유니온 타입)
  isExclusive?: boolean | null; // isExclusive는 선택적 속성(optinal)이고 boolean 타입 또는 null 타입 (유니온 타입)
  isCompleted?: boolean | null; // isCompleted는 선택적 속성(optinal)이고 boolean 타입 또는 null 타입 (유니온 타입)
  isNew?: boolean | null; // isNew는 선택적 속성(optinal)이고 boolean 타입 또는 null 타입 (유니온 타입)
  isPopular?: boolean | null; // isPopular는 선택적 속성(optinal)이고 boolean 타입 또는 null 타입 (유니온 타입)
  sort?: string; // sort는 선택적 속성(optinal)이고 string 타입 또는 null 타입 (유니온 타입)
  page?: number; // page는 선택적 속성(optinal)이고 number 타입 또는 null 타입 (유니온 타입)
  size?: number; // size는 선택적 속성(optinal)이고 number 타입 또는 null 타입 (유니온 타입)
  cursorId?: number; // cursorId는 선택적 속성(optinal)이고 number 타입 또는 null 타입 (유니온 타입)
  cursorRating?: number; // cursorRating는 선택적 속성(optinal)이고 number 타입 또는 null 타입 (유니온 타입)
} = {}) { // ={}는 paramse에 값이 안들어오면 기본값으로 {}를 사용하겠다는 뜻
  const qp = new URLSearchParams(); // 재할당 불가 URLSearchParams 클래스의 인스턴스
  // URLSearchParamse 인스턴스를 만듬 URLSearchParams는 Web API고 브라우저가 제공하는 전역 객체임
  // feach, navigator처럼 import 없이 바로 사용 가능함 / JSON같은 거인듯?
  // URL 쿼리 파라미터를 생성/관리해주는 유틸리티
  // 여기서 조건문이 있는건 선택 파라미터임 sort, page, size도 선택적이긴하지만 기본값이 있어서 조건 없이 항상 추가됨
  if (params.status) qp.append('status', params.status);
  // if (params.status)는 params.status가 truthy인지 확인함 
  // truthy: 존재하고, null / undefined / empty가 아니면 성립
  // status는 sttring이라 truthy 체크로 충분
  // string은 빈 문자열이면 falsy, null도 falsy / 값이 있으면 truthy, 0은 truthy
  // string은 "0"도 유효한 값이고 truthy이므로 if (params.status) 조건으로 추웁ㄴ
  // year(number)는 0도 유효한 값이지만 falsy로 체크되서 if (params.year) 조건으로 체크하면 0이 제외됨
  // 그래서 != null로 체크해야 0이 포함함
  // 조건이 참이면 qp.append('status', params.status)fh qp에 'status' 키로 params.status 값을 추가함
  // 자바에서는 StringBuilder나 StringBuffer에서 .append를 사용하면 (키,값)형태로 쿼리 파라미터를 추가해줌
  // List: add / Map: put() / Set: add()
  // 여기서 쓰인 append 함수는 파라미터에 들어온 값을 키/값쌍으로 저장해줌
  if (params.genreIds && params.genreIds.length) params.genreIds.forEach(id => qp.append('genreIds', String(id)));
  // if (params.genreIds && params.genreIds.length)는 params.genreIds가 truthy이고, 배열이 0이 아니면 성립
  // 조건이 참이면 params.genreIds.forEach(id => qp.append('genreIds', String(id)))로 qp에 'genreIds' 키로 id 값을 추가함
  // forEach는 배열 반복문이고 콜백 함수를 이자로 받음 / params.genreIds.forEach(...)로 체인 호출하고, 콜백 함수를 전달함
  // 여기서 콜백은 id => qp.append('genreIds', String(id)) 이렇게 작성되었고 화살표 함수 형태의 콜백임
  // 콜백 함수란 나중에 호출될 함수고 forEach가 배열을 순회하면서 각 요소마다 이 콜백 함수를 호출한다는뜻임
  // genreIds는 number타입 배열이고 자바의 List<Integer>와 비슷함
  // 만약 params.genreIds에 [1, 2, 3]이 상태일때 forEach 함수에 콜백함수를 위처럼 작성하고 호출하면
  // forEach가 배열의 첫 번쨰 요소 1을 가져오고 / 콜백 함수를 호출하면서 id = 1을 전달
  // 화살표 함수 실행: id => qp.append('genereIds', String(id))
  // id = 1이므로 qp.append('genereIds', String(1)) 실행함
  // string(1) = '1'로 변환
  // qp.append('genereIds', '1') 실행 -> qp에 'genereIds=1' 저장
  // 그니까 genreIds 배열을 forEach 함수로 순회하고 각 요소마다 콜백 함수를 호출하는데 그때 각 요소는
  // id 인자에 하나씩 들어가고 그게 qp.append('genreIds', String(id)) 함수 표현식에 id에 들어간다음 문자열로 변환되고
  // 'genereIds=1' 이런식에 키값쌍으로 저장됨
  if (params.tagIds && params.tagIds.length) params.tagIds.forEach(id => qp.append('tagIds', String(id)));
  // if (params.tagIds && params.tagIds.length)는 params.tagIds가 truthy이고, 배열이 0이 아니면 성립
  // tadIds 배열에 각 요소를 forEach 함수로 순회하고 각 요소를 id에 한번씩 넘기고 넘길때마다 콜백함수를 호출함
  // id에 1이 들어오면 화살표 함수 앞에 인자에 들어가고 그걸 함수 표현식에 전달함
  // 그럼 qp.append('tagIds', String(id)) 함수 표현식에 id = 1이 되고
  // string(1) = '1'로 변환
  // qp.append('tagIds', '1') 실행 -> qp에 'tagIds=1' 키값쌍으로 저장함
  if (params.minRating != null) qp.append('minRating', String(params.minRating));
  // if (params.minRating != null)는 params.minRating가 null이 아닌지 체크함
  // (params.mianRating)으로 체크하면 truthy / falshy 체크를 하는데 여기서 0은 falsy로 체크되서 조건이 성립이안됨
  // number 타입은 0도 유효한 값이라 조건에 포함을 시켜아해서 조건을 != null로 체크해서 0이 true가 될수있게해줌
  // 여기서 쓰인 append 함수는 파라미터에 들어온 값을 키/값쌍으로 저장해줌
  // String(params.minRating)는 params.minRating 값을 문자열로 변환해줘서
  // qp에 'minRating=1' 키값쌍으로 저장됨 
  if (params.year != null) qp.append('year', String(params.year));
  // if (params.year != null)는 params.year가 null이 아닌지 체크함
  // (params.year)로 체크하면 truthy / falshy 체크를 하는데 여기서 0은 falsy로 체크되서 조건이 성립이안됨
  // number 타입은 0도 유효한 값이라 조건에 포함을 시켜아해서 조건을 != null로 체크해서 0이 true가 될수있게해줌
  // String(params.year)는 params.year 값을 문자열로 변환해줘서
  // qp에 'year=2024' 키값쌍으로 저장됨 
  if (params.quarter != null) qp.append('quarter', String(params.quarter));
  // if (params.quarter != null)는 params.quarter가 null이 아닌지 체크함
  // (params.quarter)로 체크하면 truthy / falshy 체크를 하는데 여기서 0은 falsy로 체크되서 조건이 성립이안됨
  // number 타입은 0도 유효한 값이라 조건에 포함을 시켜아해서 조건을 != null로 체크해서 0이 true가 될수있게해줌
  // String(params.quarter)는 params.quarter 값을 문자열로 변환해줘서
  // qp에 'quarter=3' 키값쌍으로 저장됨 
  if (params.type) qp.append('type', params.type);
  // if (params.type)는 params.type가 truthy인지 확인함
  // truthy: 존재하고, null / undefined / empty가 아니면 성립
  // string은 빈 문자열이면 falsy, null도 falsy / 값이 있으면 truthy, 0은 truthy
  // string은 "0"도 유효한 값이고 truthy이므로 if (params.type) 조건으로 충분분
  // 조건이 참이면 qp.append('type', params.type)로 qp에 'type' 키로 params.type 값을 추가함
  // 여기서 쓰인 append 함수는 파라미터에 들어온 값을 키/값쌍으로 저장해줌
  if (params.isDub != null) qp.append('isDub', String(params.isDub));
  // if (params.isDub != null)는 params.isDub가 null이 아닌지 체크함
  // (params.isDub)으로 체크하면 truthy / falshy 체크를 하는데 만약 false가 나올 경우 falsy로 체크되서 조건이 성립이안됨
  // boolean 타입은 false도 유효한 값이라 조건에 포함을 시켜아해서 조건을 != null로 체크해서 false가 true가 될수있게해줌
  // String(params.isDub)는 params.isDub 값을 문자열로 변환해줘서
  // qp에 'isDub=true' 키값쌍으로 저장됨 
  if (params.isSubtitle != null) qp.append('isSubtitle', String(params.isSubtitle));
  // if (params.isSubtitle != null)는 params.isSubtitle가 null이 아닌지 체크함
  // (params.isSubtitle)으로 체크하면 truthy / falshy 체크를 하는데 만약 false가 나올 경우 falsy로 체크되서 조건이 성립이안됨
  // boolean 타입은 false도 유효한 값이라 조건에 포함을 시켜아해서 조건을 != null로 체크해서 false가 true가 될수있게해줌
  // String(params.isSubtitle)는 params.isSubtitle 값을 문자열로 변환해줘서
  // qp에 'isSubtitle=true' 키값쌍으로 저장됨 
  if (params.isExclusive != null) qp.append('isExclusive', String(params.isExclusive));
  // if (params.isExclusive != null)는 params.isExclusive가 null이 아닌지 체크함
  // (params.isExclusive)으로 체크하면 truthy / falshy 체크를 하는데 만약 false가 나올 경우 falsy로 체크되서 조건이 성립이안됨
  // boolean 타입은 false도 유효한 값이라 조건에 포함을 시켜아해서 조건을 != null로 체크해서 false가 true가 될수있게해줌
  // String(params.isExclusive)는 params.isExclusive 값을 문자열로 변환해줘서
  // qp에 'isExclusive=true' 키값쌍으로 저장됨 
  if (params.isCompleted != null) qp.append('isCompleted', String(params.isCompleted));
  // if (params.isCompleted != null)는 params.isCompleted가 null이 아닌지 체크함
  // (params.isCompleted)으로 체크하면 truthy / falshy 체크를 하는데 만약 false가 나올 경우 falsy로 체크되서 조건이 성립이안됨
  // boolean 타입은 false도 유효한 값이라 조건에 포함을 시켜아해서 조건을 != null로 체크해서 false가 true가 될수있게해줌
  // String(params.isCompleted)는 params.isCompleted 값을 문자열로 변환해줘서
  // qp에 'isCompleted=true' 키값쌍으로 저장됨 
  if (params.isNew != null) qp.append('isNew', String(params.isNew));
  // if (params.isNew != null)는 params.isNew가 null이 아닌지 체크함
  // (params.isNew)으로 체크하면 truthy / falshy 체크를 하는데 만약 false가 나올 경우 falsy로 체크되서 조건이 성립이안됨
  // boolean 타입은 false도 유효한 값이라 조건에 포함을 시켜아해서 조건을 != null로 체크해서 false가 true가 될수있게해줌
  // String(params.isNew)는 params.isNew 값을 문자열로 변환해줘서
  // qp에 'isNew=true' 키값쌍으로 저장됨 
  if (params.isPopular != null) qp.append('isPopular', String(params.isPopular));
  // if (params.isPopular != null)는 params.isPopular가 null이 아닌지 체크함
  // (params.isPopular)으로 체크하면 truthy / falshy 체크를 하는데 만약 false가 나올 경우 falsy로 체크되서 조건이 성립이안됨
  // boolean 타입은 false도 유효한 값이라 조건에 포함을 시켜아해서 조건을 != null로 체크해서 false가 true가 될수있게해줌
  // String(params.isPopular)는 params.isPopular 값을 문자열로 변환해줘서
  // qp에 'isPopular=true' 키값쌍으로 저장됨

  qp.append('sort', params.sort ?? 'id');
  // ?? 연산자는 왼쪽 값이 null 또는 undefiend면 오른쪽 값을 사용한다는 뜻
  // params.sort가 있으면 그걸 사용하고 없으면 id값을 사용하는것
  // qp에 'sort' 키로 params.sort 값을 추가하거나 없으면 'id'로 추가함
  qp.append('page', String(params.page ?? 0));
  // params.page가 있으면 그걸 사용하고 없으면 0값을 사용하는것
  // qp에 'page' 키로 params.page 값을 추가하거나 없으면 '0'로 추가함
  qp.append('size', String(params.size ?? 20));
  // params.size가 있으면 그걸 사용하고 없으면 20값을 사용하는것
  // qp에 'size' 키로 params.size 값을 추가하거나 없으면 '20'로 추가함
  if (params.cursorId != null) qp.append('cursorId', String(params.cursorId));
  // if (params.cursorId != null)는 params.cursorId가 null이 아닌지 체크함
  // (params.cursorId)으로 체크하면 truthy / falshy 체크를 하는데 만약 false가 나올 경우 falsy로 체크되서 조건이 성립이안됨
  // boolean 타입은 false도 유효한 값이라 조건에 포함을 시켜아해서 조건을 != null로 체크해서 false가 true가 될수있게해줌
  // String(params.cursorId)는 params.cursorId 값을 문자열로 변환해줘서
  // qp에 'cursorId=1' 키값쌍으로 저장됨 
  if (params.cursorRating != null) qp.append('cursorRating', String(params.cursorRating));
  // if (params.cursorRating != null)는 params.cursorRating가 null이 아닌지 체크함
  // (params.cursorRating)으로 체크하면 truthy / falshy 체크를 하는데 만약 false가 나올 경우 falsy로 체크되서 조건이 성립이안됨
  // boolean 타입은 false도 유효한 값이라 조건에 포함을 시켜아해서 조건을 != null로 체크해서 false가 true가 될수있게해줌
  // String(params.cursorRating)는 params.cursorRating 값을 문자열로 변환해줘서
  // qp에 'cursorRating=1' 키값쌍으로 저장됨 

  // getAnimeList와 동일한 응답 처리: 단순히 apiCall만 반환
  const url = `/anime?${qp.toString()}`;
  console.log('[DEBUG] listAnime API 호출 URL:', url);
  return apiCall(url);
}

// 애니메이션 상세 정보 조회
export async function getAnimeDetail(animeId: number) {
  return apiCall(`/anime/${animeId}`);
}

// 요일별 신작 애니메이션 조회
export async function getWeeklyAnime(dayOfWeek: string) {
  return apiCall(`/anime/weekly/${dayOfWeek}`);
}

// 장르별 애니메이션 검색
export async function getAnimeByGenre(genre: string, page: number = 0, size: number = 20) {
  return apiCall(`/anime/genre/${genre}?page=${page}&size=${size}`);
}

// 태그별 애니메이션 검색
export async function getAnimeByTag(tag: string, page: number = 0, size: number = 20) {
  return apiCall(`/anime/tag/${tag}?page=${page}&size=${size}`);
}

// 애니메이션 검색
export async function searchAnime(query: string, page: number = 0, size: number = 20) {
  return apiCall(`/anime/search?query=${encodeURIComponent(query)}&page=${page}&size=${size}`);
}

// 추천 애니메이션 조회
export async function getRecommendedAnime() {
  return apiCall('/anime/recommended');
}

// 인기 애니메이션 조회
export async function getPopularAnime() {
  return apiCall('/anime/popular');
}

// 최신 애니메이션 조회
export async function getLatestAnime() {
  return apiCall('/anime/latest');
}

// 실시간 트렌딩(24h) 조회
export async function getTrendingAnime24h(limit: number = 10) {
  return apiCall(`/anime/trending-24h?limit=${limit}`);
}

// 마스터: 장르/태그 목록
export async function getGenres() {
  return apiCall('/anime/genres');
}

export async function getTags() {
  return apiCall('/anime/tags');
}

// 필터 옵션 목록
export async function getSeasons() {
  return apiCall('/anime/seasons');
}

export async function getYearOptions() {
  return apiCall('/anime/year-options');
}

export async function getStatuses() {
  return apiCall('/anime/statuses');
}

export async function getTypes() {
  return apiCall('/anime/types');
}