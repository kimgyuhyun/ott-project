// 포매터 설정은 최소로 둔다. 옵션 하나는 나중에 다시 논의할 안건 하나다.
// 여기 있는 한 줄은 취향이 아니라 환경 때문에 필요하다.
//
// endOfLine: 이 저장소는 core.autocrlf=true 라 워킹트리가 CRLF, 저장소가 LF 다.
// Prettier 기본값('lf')로 두면 리눅스 CI 는 통과하는데 윈도우 로컬에서 --check 가
// 전 파일 불합격으로 나온다. 'auto' 는 파일이 이미 쓰는 줄바꿈을 그대로 인정한다.
//
// 익명 객체를 그대로 export 하지 않는 것은 ESLint import/no-anonymous-default-export 때문이다.
/** @type {import("prettier").Config} */
const config = {
  endOfLine: "auto",
};

export default config;
