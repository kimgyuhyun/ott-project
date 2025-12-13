"use client"; // CSR
import { useState, useEffect } from "react"; // React Hooks
import { useRouter } from "next/navigation"; // Next.js 라우터
// useRoter는 next.js 13+ (App Router)에서 제공하는 클라이언트 라우팅 훅임
// 주요 기능으로는 페이지 이동, 뒤로가기, 현재 페이지 새로로고침, 히스토리 없이 이동이 있음
// 참고로 Pages Router(pages 디렉토리)에서는 next/router의 useRouter를 사용하지만, 
// APp Router(app 디렉토리)에서는 next/navigation의 useRouter를 사용함
// 내 프로젝트는 pages 디렉토리를 안만들고 app 디렉토리에서 도메인별로 폴더를 만들고 거기안에 page.tsx 파일을 사용하는
// App Router 방식을 사용하고있음 = next/navigation
// App Router를 선택한 이유는 
// 서버 컴포넌트 지원, React 18+ 기능 활용, 더 나은 데이터 패칭, Next.js의 권장 방식이기 떄문
// 서버 컴포넌트 ㅣ원은 서버에서 랜더링해 성능향상해주는것
// 더 나은 데이터 패칭은 서버 컴포넌트에서 직접 데이터 가져오는것을 뜻함
// next/navigation은 Next.js 전용임
// React에서 사용하려면 react-router-dom의 useNavigate 또는 useHistory를 사용해야함
// Next.js는 CSR< SSR, SSG, ISR, SPA도 가능
// useRouter는 페이지 이동 (히스토리에 추가),(히스토리 교체), 이전 페이지로 돌아가기, 현재 페이지 새로고침 등이 있고
// 일반 html은 페이지 이동할때 링크를 클릭해야하지만 useRotuer는 함수를 호출함
// 그리고 roter.push() 함수는 페이지 전체를 새로고침하지 않고 필요한 부분만 업데이트하는 SPA 방식임
import ReviewList from "@/components/reviews/ReviewList"; // 리뷰 목록 컴포넌트
import { getAnimeDetail, listAnime } from "@/lib/api/anime"; // 애니메이션 상세 정보 관련 API
import AnimeCard from "@/components/home/AnimeCard"; // 애니메이션 카드 컴포넌트
import { getAnimeWatchHistory } from "@/lib/api/user"; // 시청 기록 관련 API
import { toggleFavorite, isFavorited } from "@/lib/api/favorites"; // 보고싶다 기능 관련 API
import { deleteFromBinge } from "@/lib/api/user"; // 시청 기록 초기화 관련 API
import { Anime, Episode, User } from "@/types/common"; // 공통 타입 정의
import styles from "./AnimeDetailModal.module.css"; // 스타일 정의
import AnimeFullInfoModal from "@/components/anime/AnimeFullInfoModal"; // 애니메이션 상세 정보 모달


interface AnimeDetailModalProps { // 여기 컴포넌트가 받을 props의 타입을 정의
  anime: Anime; // anime는 Anime 타입의 객체를 받음
  isOpen: boolean; // isOpen은 boolean 타입의 값을 받음 / 모달이 열려있는지 여부를 나타냄
  onClose: () => void; // 매개변수 없이 호출하고, 아무것도 반환하지 않는 함수
}

/**
 * 애니메이션 상세 정보 모달
 * 평점, 제목, 장르, 액션 버튼, 시놉시스, 탭 메뉴, 에피소드 목록 포함
 */
export default function AnimeDetailModal({ anime, isOpen, onClose }: AnimeDetailModalProps) {
  // import해서 사용할 수 있는 함수 default는 기본 내보기이고 이름 변경해서 사용 가능함
  // default가 없으면 다른 파일에서 가져올때 { AnimeDetailModal } 이렇게 정확한 이름을 사용해야함
  // 파라미터로 전달받은 props 객체 값에 구조 분해 할당 문법을 사용해서 anime, isOpen, onClose 속성을 추출해 각각 변수에 할당함
  // : AnimeDetailModalProps는 이 props 객체에 타입을 정의한것
  // 결론은 props 객체를 받아서 구조 분해로 바로 할당해서 사용하는 방식
  // React는 props를 구조 분해로 받는 것이 관례임
  // TypeScript에서는 : AnmimeDetailModalProps로 타입 체크가 가능함
  type ExtendedAnime = Anime & {
    // ExtendAnime 타입을 정의할꺼고 여기에 Anime 타입과 { } 안의 속성들을 합친다는 뜻 여기서 &는 타입을 합치는 연산자로 사용됨.
    // 자바는 extends로 런타임에서 합치지만 TypeScript는 &로 타입을 컴파일 타임에서 합침
    // Java는 다른 클래스를 상속 TypeScript는 타입이 다른 타입과 합쳐진다는것
    // Anime 타입의 모든 속성 + { } 안의 추가 속성을 ExtendedAnime 타입으로 정의한다는 뜻
    aniId?: number | string; // aniId는 number 또는 string 타입이고 선택 필드 / 애니메이션 고유 식별자
    episodes?: Episode[]; // episodes는 Episode 타입의 배열이고 선택 필드 / 에피소드 목록
    fullSynopsis?: string; // fullSynopsis는 string 타입이고 선택 필드 / 전체 줄거리
    synopsis?: string; // synopsis는 string 타입이고 선택 필드 / 요약 줄거리
    badges?: string[]; // badges는 string 타입의 배열이고 선택 필드 / 배지 목록
    isDub?: boolean; // isDub는 boolean 타입이고 선택 필드 / 더빙 여부
    isSubtitle?: boolean; // isSubtitle는 boolean 타입이고 선택 필드 / 자막 여부
    titleJp?: string; // titleJp는 string 타입이고 선택 필드 / 일본어 제목
    titleEn?: string; // titleEn는 string 타입이고 선택 필드 / 영어 제목
    backdropUrl?: string; // backdropUrl는 string 타입이고 선택 필드 / 배경 이미지 URL
    posterUrl?: string; // posterUrl는 string 타입이고 선택 필드 / 포스터 이미지 URL
    imageUrl?: string; // imageUrl는 string 타입이고 선택 필드 / 이미지 URL
    thumbnail?: string; // thumbnail는 string 타입이고 선택 필드 / 썸네일 URL
    posterImage?: string; // posterImage는 string 타입이고 선택 필드 / 포스터 이미지
    ageRating?: string; // ageRating는 string 타입이고 선택 필드 / 연령 등급
    type?: string; // type는 string 타입이고 선택 필드 / 타입 없는 애니도 있기에 / 못가져올수도있고 // TV / 영화 / OVA / OAD 등등
    animeStatus?: 'COMPLETED' | 'ONGOING' | 'UPCOMING' | 'CANCELLED' | string;
     // animeStatus는 'COMPLETED' | 'ONGOING' | 'UPCOMING' | 'CANCELLED' | string 타입이고 선택 필드 / 방영 상태
     // 4개의 특정 값 중 하나이거나, 또는 아무 string 값이 들어올 수 있음
     // 더 염격하게 할려면 | String을 뺴면됨됨
    rating?: number; // rating는 number 타입이고 선택 필드 / 평점
    genres?: Array<string | { id?: number; name?: string }>; // genres는 string 타입의 배열이고 선택 필드 / 장르 목록
    // 타입, 일본어제목, 포스터, 연령등급, 평점은 중복 정의므로 삭제 가능
  };
  type WatchHistory = { // WatchHistory 타입을 정의의
    episodeId: number; // episodeId는 number 타입이고 필수 필드 / 에피소드 고유 식별자
    episodeNumber: number; // episodeNumber는 number 타입이고 필수 필드 / 에피소드 번호
    positionSec: number; // positionSec는 number 타입이고 필수 필드 / 시청 위치 (초)
    duration?: number; // duration는 number 타입이고 선택 필드 / 에피소드 러닝타임 (초)
    completed: boolean; // completed는 boolean 타입이고 필수 필드 / 에피소드 시청 완료 여부 / 진행중인 에피소드는 false로 처리함
    watchedAt?: string | number | Date; // watchedAt는 string 또는 number 또는 Date 타입이고 선택 필드 / 시청 일시
  } | null; // | null의 의미는 유니온 타입. WatchHistory는 객체이거나 null일 수 있다는것
  // 시청기록이 있으면 객체, 없으면 null
  const router = useRouter(); // useRouter() 함수를 호출해 반환된 router 객체를 재할당 불가 변수 roter에 저장함
  const [activeTab, setActiveTab] = useState<'episodes' | 'reviews' | 'shop' | 'similar'>('episodes');
  // useState는 React Hooks중 하나이고 [값, 함수]를 반환해주고 이때 반환해주는 함수는 상태 변경 함수임
  // useState<'episodes' | 'reviews' | 'shop' | 'similar'>('episodes')의 뜻은
  // usetState 리턴 값의 타입은 episodes, reviews, shop, similar 중 하나만 가능하고 기본값은 'episodes'로 설정한다는뜻
  // 그리고 useSState는 처음 렌더링될 때 한 번만 호출하기에 AnimeDetailModal 컴포넌트에 기본 탭은 episodes로 설정됨
  // useState<'episodes' | 'reviews' | 'shop' | 'similar'>('episodes')는
  // ['episodes', 상태 변경 함수] 형태로 반환되고 이걸 구조 분해 할당으로 받아서
  // 첫 번째 요소 'episodes'는 activeTab에 할당
  // 두 번째 요소 상태 변경 함수는 setActiveTab에 할당됨
  const [detail, setDetail] = useState<ExtendedAnime>(anime as ExtendedAnime);
  // useState<ExtendedAnime>(anime as ExtendedAnime)의 뜻은
  // 우선 useState()의 반환값을 ExtendAnime 타입으로 정의한단뜻이고
  // anime as ExtendAnime는 타입 단언으로 anime를 ExtendAnime 타입으로 취급하라는 뜻
  // 즉 Anime 타입을 ExtendAnime 타입으로 단언 하는것임
  // 여기서 사용하는 anime는 props로 전달된 Anime 타입 객체이고
  // 이걸 UseState의 anime as ExtendAnime로 넣어주면 타입 단언이 들어감
  // anime 객체를 ExtendedAnime 타입으로 취급한다는 뜻이고 실제 캐스팅은 하지않음 anime 객체는 그대로
  // useState()에 반환값은 [props로 전달된 anime 객체, 상태 변경 함수] 형태로 반환될꺼고
  // 이 값은 첫 번째 요소 anime 객체는 detail에 두 번째 요소 상태 변경 함수는 setDetail에 구조 분해 할당됨
  // useState()는 [상태 값, 상태 변경 함수]를 반환함 이해하기 편하게
  const [watchHistory, setWatchHistory] = useState<WatchHistory>(null);
  // useState 함수의 반환값은 WatchHistory 타입의 객체 또는 null이 올꺼고 기본값은 null로 설정됨
  // WatchHistory 타입의 객체 또는 null 이란건 타입 정의할때 | null을 사용해서 유니온 타입으로 정의했기 때문
  // [null, 상태 변경 함수]로 리턴되고 첫 번 째 요소 null은 watchHistory에
  // 두번째 요소 상태 변경 함수는 setWatchHistory에 구조 분해 할당됨
  const [isLoadingHistory, setIsLoadingHistory] = useState(false);
  // useState(false)의 뜻은
  // useState 함수의 반환값은 boolean 타입의 값이 올꺼고 기본값은 false로 설정됨
  // [false, 상태 변경 함수]로 리턴되고 첫 번 째 요소 false은 isLoadingHistory에
  // 두번째 요소 상태 변경 함수는 setIsLoadingHistory에 구조 분해 할당됨
  const [isFavoritedState, setIsFavoritedState] = useState<boolean>(false);
  // useState() 함수의 반환값은 boolean 타입일꺼고 기본값은 false로 설정됨
  // [false, 상태 변경 함수]로 리턴되고 첫 번 째 요소 false은 isFavoritedState에
  // 두번째 요소 상태 변경 함수는 setIsFavoritedState에 구조 분해 할당됨
  const [isLoadingFavorite, setIsLoadingFavorite] = useState(false);
  // useState() 함수의 기본값은 false로 설정되고
  // [false, 상태 변경 함수]로 리턴되고 첫 번 째 요소 false은 isLoadingFavorite에
  // 두번째 요소 상태 변경 함수는 setIsLoadingFavorite에 구조 분해 할당됨
  // 위에는 <boolean>을 사용해서 제네릭을 명시했지만 여기서는 제네릭을 생략함
  // 제네릭을 안넘겨도 TypeScript가 초기값 false로부터 타입을 추론해서 리턴타입을 정함
  // Java는 자료구조 중중심, TypeScript는 함수/타입 중심으로 제네릭을 사용
  const [currentRating, setCurrentRating] = useState<number | null>(null); // 실시간 평점 상태
  // useState() 함수의 반환값은 number 또는 null 타입일꺼고 기본값은 null로 설정
  // [null, 상태 변경 함수]로 리턴되고 첫 번 째 요소 null은 currentRating에
  // 두번째 요소 상태 변경 함수는 setCurrentRating에 구조 분해 할당됨
  // 저 첫번째 요소에 타입이 number 또는 null 이란거임
  const [similarAnimes, setSimilarAnimes] = useState<Anime[]>([]);
  // useState() 함수의 반환값은 Anime 타입의 배열이 올꺼고 기본값은 []로 설정됨 빈 배열이란뜻
  // [Anime[], 상태 변경 함수]로 리턴되고 첫 번 째 요소 []은 similarAnimes에
  // 두번째 요소 상태 변경 함수는 setSimilarAnimes에 구조 분해 할당됨
  const [isLoadingSimilar, setIsLoadingSimilar] = useState(false);
  // useState() 함수의 기본값은 false로 설정되고
  // [false, 상태 변경 함수]로 리턴되고 첫번 째 요소 false은 isLoadingSimilar에
  // 두번째 요소 상태 변경 함수는 setIsLoadingSimilar에 구조 분해 할당됨
  const [showFullSynopsis, setShowFullSynopsis] = useState<boolean>(false);
  // useState() 함수는 boolean 타입을 반환하고 기본값은 false로 설정됨
  // [false, 상태 변경 함수]로 리턴되고 첫번 째 요소 false은 showFullSynopsis에
  // 두번째 요소 상태 변경 함수는 setShowFullSynopsis에 구조 분해 할당됨
  const MAX_SYNOPSIS_CHARS = 180;
  // MAX_SYNOPSIS_CHARS는 180으로 설정됨 줄거리 최대 글자수를 뜻함
  const [isFullInfoOpen, setIsFullInfoOpen] = useState<boolean>(false);
  // useState() 함수는 boolean 타입을 반환하고 기본값은 false로 설정됨
  // [false, 상태 변경 함수]로 리턴되고 첫번 째 요소 false은 isFullInfoOpen에
  // 두번째 요소 상태 변경 함수는 setIsFullInfoOpen에 구조 분해 할당됨
  const [isDropdownOpen, setIsDropdownOpen] = useState<boolean>(false);
  // useState() 함수는 boolean 타입을 반환하고 기본값은 false로 설정됨
  // [false, 상태 변경 함수]로 리턴되고 첫번 째 요소 false은 isDropdownOpen에
  // 두번째 요소 상태 변경 함수는 setIsDropdownOpen에 구조 분해 할당됨
  const [showDeleteConfirm, setShowDeleteConfirm] = useState<boolean>(false);
  // useState() 함수는 boolean 타입을 반환하고 기본값은 false로 설정됨
  // [false, 상태 변경 함수]로 리턴되고 첫번 째 요소 false은 showDeleteConfirm에
  // 두번째 요소 상태 변경 함수는 setShowDeleteConfirm에 구조 분해 할당됨
  // 여기 false부분에 제네릭을 다 명시하던가 아니면 다 생략해서 타입 추론으로 넘기던가 통일해야함

  // 평점 변경 콜백 함수
  const handleRatingChange = (newRating: number) => {
    // 파라미터로 newRating 값을 number 타입으로 받고 화살표 함수로 함수 정의를 한뒤 handleRatingChange 재할당 불가 변수에 할당함
    // 이때 newRating는 새로운 평점 값을 뜻함
    setCurrentRating(newRating);
    // 현재 평점 상태를 newRating 값으로 업데이트
    setDetail((prev: ExtendedAnime) => ({ ...prev, rating: newRating }));
    // newRating을 그대로 보내면 편할꺼같지만 타입이 달라서 전달안됨
    // 그래서 setDetail 함수에 인자로 함수를 정의해서 보내는것
    // (prev: ExtendedAnime) => ({ ...prev, rating: newRating })에 뜼은
    // prev 파라미터는 ExtendAnime 타입을 받을꺼고
    // 함수 본문은 받은 파라미터 prev 객체를 ... 스프레 문법으로 펼쳐서 prev의 모든 속성을 복사함
    // 그리고 prev에 rating 속성을 newRating 값으로 업데이트함
    // 이러면 rating 속성만 newRating으로 덮어씌워지고 다른 속성은 그대로 유지가됨
    // CurrentRating은 AnimeDetailModal에 상단 왼쪽에  "4.5" 이런 형태로 표시됨 사용자가 평점을 변경하면 실시간으로 업데이트됨
    // detail 객체 내부의 rating 속성은 화면에 직접 표시되지는 않고 데이터 저장/동기화용임
    // 초기값은 anime.rating에서 가져오고 anime 객체를 detail이 받아서 쓰니
    // detail.rating 값과 currentRating 값은 모두 동일한 값으로 시작함
    // currnetRating은 화면 표시용(즉시 업데이트)
    // detail.rating은 모달 내부 상태 관리용(모달이 열려있는 동안만 유지)
    // 모달을 닫았다 다시 열면 props의 원래 갑래 값으로 초기화됨
    // 이렇게 보면 detail.rating은 필요 없어보이지만 콘솔 로그에는 출력됨 (디버깅용)

    // 모달이 먼저 렌더링되고 모달 상단 평점은 props의 amnime.rating으로 초기화되고
    // 리뷰 탭은 별도 섹션으로 관리함
    // 이때 리뷰에서 평점 변경 시 모달 상단도 업데이트가 필요함 섹션이 분리되어 있어 직접 접근이 불가하기때문에
    // 여기서 정의한 콜백 함수로 통신하는것
    // 리뷰 리스트는 실제 DB 저장을 담항하고 API 호출해서 서버에 저장 후 최신 평균 평점 재조회해서 유지함
    // 저장 후 콜백으로 부모에게 알리면 모달 상단에 평점이 변경됨
    // 모달을 닫고 다시 열면 변경된 평점이 유지되는데 이는 리뷰 리스트에서 APi 호출해서 DB에 평점 데이터를 갱신해둔걸 새로 받아오기때문
    // 이 방법이 React에서 여러 컴포넌트를 합쳐서 쓸 때 상태를 관리하는 방법 중 하나임
  };

  // 시청 기록 초기화 핸들러
  const handleDeleteWatchHistory = async () => {
    // 화살표 함수로 함수를 정의해서 재할당 불가 변수 handleDeleteWatchHistory에 비동기 함수를 할당함
    try { // try-catch 문으로 예외 처리를 함
      console.log('🗑️ 시청 기록 초기화 시작 - aniId:', (detail as any)?.aniId);
      // '시청 기록 초기화 시작 - aniId:' 메시지에 (detal as any)?.aniId값을 추가해서 콘솔로 출력함
      // (detail as any)는 타입 단언을 사용한것이고 detail을 any 타입으로 취급한다는 뜻
      // ci/cd에서 오류가나서 as any로 타입 체크 우회했는데
      // 타입 안전성 손실, 런타임 에러 가능성, 유지보수 어려워서 나중에 리팩토링해야함
      // ?.aniID는 옵셔널 체이닝을 사용해서 detail 객체 안의 aniId 속성에 안전하게 접근한것
      // detail이 null / undefiend 일 수 있어서 안전하게 접근하기 위함
      // detail이 mnull이면 undefiend를 반환, 에러는 발생안함
      // null 방어 코딩임 단건 조회 시 사용하는 옵셔널 리턴은 반환 시점의 방어를 하고
      // 여긴 접근 시점의 방어를 하는것
      // detail?.aniId는 detal 객체가 null/undefiend 인지 확인하고 있으면 detail 객체 사용해서 aniId 속성에 접근
      // 없으면 undefiend 반환(접근 중단)
      await deleteFromBinge(Number((detail as any)?.aniId ?? (detail as any)?.id));
      // (detail as any)는 detail을 any 타입으로 타입 단언한것이고
      // 여기에 ?.aniId로 옵셔널 체이닝을 사용해서 detail 객체가 null/undefiend 인지 체크하고 있으면 detail 객체 사용해서 aniId 속성에 접근
      // 없으면 undefiend 반환(접근 중단)함
      // 그 다음 ?? 는 Nullish Coalescing 연산자임 왼쪽이 null/undefiend 면 오른쪽 값을 사용한다는뜻
      // (detail as any)?.aniId ?? (detail as any)?.id 이 부분을 해석하면
      // detail이 null/undefind가 아니면 detail.aniId 값을 사용하고 만약 aniId 속성이 없으면
      // id 속성을 사용한다는 뜻
      // detail이 null이면 둘다 null/undefiend 이므로 NaN 상태가 되어서 문제가 발생
      // NaN 상태는 숫자가 아닌 값을 숫자로 변환하려 할 때 발생함 여기 방어 코드가 없으므로 추후 방어 코드 추가 필요
      // 이 조건식을 number()안에 쓴 이유는 deleteFromBinge 함수가 number 타입만 받기 떄문에 number 타입으로 캐스팅해야하기때문
      // 참고로 deleteFromBinge는 import 받아서 쓰는 함수고 25라인을 확인하면 알 수 있음
      // 다른 곳에 정의된 함수를 import해와서 새로 함수 정의할 때 함수 본문에 사용가능함
      // handleDeleteWatchHistory 함수는 deleteFromBinge를 호출하는 래퍼 함수임
      // 이렇게 함수를 import해서 핸들러 함수에 함수 본문에 호출해서 추가 로직을 처리하는 패턴은
      // 관심사 분리: API 호출과 UI 로직 분리
      // 재사용성: deleteFromBinge를 여러 곳에서 사용 가능
      // 유지보수: API 함수 수정 시 원본이 되는 한 곳만 수정하면됨
      console.log('🗑️ 시청 기록 초기화 완료'); // '시청 기록 초기화 완료' 메시지를 콘솔로 출력함
      
      // 시청 기록 상태 초기화
      setWatchHistory(null); // 시청기록을 null로 초기화
      setShowDeleteConfirm(false); // 삭제 확인 모달 닫기
      setIsDropdownOpen(false); // 드롭다운 메뉴 닫기
      // 별도 컴포넌트로 따로 따로 관리하고있으면 자식 컴포넌트에서 API 호출해서 DB 값변경시 콜백함수로 부모에게 알려줘서 상태를 업데이트해야하지만
      // 여기는 별도 컴포넌트가 아니라 같은 컴포넌트 내부에서
      // 드롭다운 메뉴 -> 삭제 버튼 클릭하면 DB 값 변경이되는 방식이라
      // 콜백 필요없이 직접 상태 변경해주면됨
      alert('시청 기록이 초기화되었습니다.'); // '시청 기록이 초기화되었습니다.' 메시지를 팝업창으로 알려줌
    } catch (error) { // try 블록에서 error 발생 시 실행행
      console.error('시청 기록 초기화 실패:', error); // '시청 기록 초기화 실패:' 메시지에 error 객체값을 추가해서 콘솔로 출력함
      alert('시청 기록 초기화에 실패했습니다.'); // '시청 기록 초기화에 실패했습니다.' 메시지를 팝업창으로 알려줌
    }
  };

  // useEffect는 화면 렌더링 후 실행되는 함수 몇번 실행될지는 의존성 배열에 따라 다름
  // 만약 빈 배열 []이면 컴포넌트가 처음 마운트 될 때 한 번만 실행되고 리렌더링되어도 실행 안 됨
  // 초기화 작업에 적합 (예: 이벤트 리스너 등록, 초기 데이터 로드)
  // 여기는 의존성 배열에 [anime]를 사용했음
  // 컴포넌트가 마운트 될 때 실행되고
  // amnime props가 변경될 때마다 실행됨 props 변경에 반응해야 할 때 사용
  // 로그인/회원가입 폼에는 빈 배열을 사용한 이유는 props 변경에 반응할 필요가 없고 초기화만 필요하기 때문
  // 여기는 anime props가 변경되면 상태를 업데이트해야 함 즉, 다른 애니메이션을 선택하면 모달 내용이 바뀌어야 하기 떄문임
  // 여기 useEffect가 6개 정의되어있는 이유는 서로 다른 목적을 담당하기 위해서, 관심사 분리를 위해서임
  // useEffact는 익명 함수로 정의, 함수 선언 후 전달, 콜백 함수 사용 등이 있음
  // 익명 함수로 정의는 바로 아래 useEffect 형식이고
  // 함수 선언 후 전달은 const로 함수 선언해서 함수본문 정의하고 그걸 인자로 전달하는것
  // 콜백 함수 사용은 우선 익명 함수 선언하고 그 안에 콜백함수를 선언해서 전달하는것 화살표 함수가 두 개 필요함
  // 첫 번째 화살표 함수는 useEffect 함수의 인자로 전달되는 것
  // 두 번째 화살표 함수는 콜백 함수를 정의할 때 사용되는것
  // 호출 순서는 초기값 세팅 -> JSX 렌더링 -> DOM 업데이트 -> 렌더링 완료 후 useEffect 실행
  // useEffect에 쓰인 Effect는 부수 효과(Side Effect)란 의미임
  // 야기서 말하는 부수효과란 컴포넌트의 주요 역할(렌더링) 외에 발생하는 작업들을 뜻함
  // API 호출(데이터 가져오기)
  // DOM 조작(스타일 변경, 이벤트 리스너)
  // 타이머 설정(setTimeOut, setInterval)
  // 구독 설정(DOM 이벤트 구독)
  // Effect라고 부르는 이유는 React의 철학으로 컴포넌트의 주요 역할 = UI 렌더링(순수 함수처럼)
  // 그 외의 작업들은 부수 효과로 취급해서 함수로 정의한뒤 useEffect에 인자로 전달하는것임
  // 이것만 보면 그냥 함수로 정의해서 쓰면 되지 왜 useEffect에 인자로 태워보내냐는 의문이 들꺼임 하지만 이걸 useEffect에 태워보내면
  // Reactr가 렌더링 완료 후 함수 실행, 의존성 배열 값 변경 감지, 적절한 시점에 함수 재실행, 컴포넌트 언마마운트 시 CleanUp을 실행해줌
  // 의존성 배열 값 변경 감지는 useEffect에 함수를 등록하면 의존성 배열 값을 계속 감시하다 변경되면 등록된 함수를 실행하는 방식
  // 인마운트가 뭔지 설명하기 위해 컴포넌트 생명주기를 설명하겠음
  // 컴포넌트 생명 주기에는
  // 마운트 - 컴포넌트가 화면에 나타남
  // 업데이트 - props/state 변경으롤 ㅣ렌더링
  // 언마운트 - 컴포넌트가 화면에서 사라짐
  // CleanUp - 컴포넌트가 언마운트 될 때 실행되는 함수이고
  // 이벤트 리스너 제거, 타이머 정리, 구독 취소(이벤트 구독), 스타일 복원 (DOM 조작 되돌리기) 등이 있음
  // 이벤트 구독은 useEffect 안에서 DOM 이벤트를 구독한다는 뜻임
  // DOM 클릭, 스크롤, 키보드 입력, 마우스 이동, 페이지 로드, 리사이즈 등이 있음
  // 의존성 배열은 상태 변경을 감지하는데 여기 useEffect를 예로 들자면 의존성 배열에 props로 전달된 anime가 변경되면
  // React가 자동으로 감지하고 useEffect 내부 함수를 다시 실행해줌
  // 만약 useEffect 없이 직접 호출하면 렌더링 중에 실행되어 성능 문제 발생, 무한 루프 가능, React가 관리할 수 없음
  useEffect(() => {
    // 익명 함수를 정의해서 useEffect에 인자로 보내 함수를 호출함
    setDetail(anime as ExtendedAnime);
    // 의존성 배열에 anime 객체가 들어있고 useEffect에서는 렌더링 사이클마다 의존성 배열의 값들을 이전 렌더링과 비교함
    // 여기서 변경이 감지되면 콜백 함수를 실행함 / 지속적으로 감시가 아니라 랜더링 마다 비교
    // anime as ExtendAnime는 anime 객체를 타입 단언해서 ExtendAnime 객체로 취급한다는뜻
    // 랜더링 사이클이란
    // 랜더링 단계: 컴포넌트 함수 실행 JSX 반환 Virtual DOm 생성/업데이트
    // 커밋 단계: 실제 DOM 업데이트, 화면에 반영
    // Effect 실행 단계: useEffect 콜백 실행, 랜더링이 끝난 후 실행
    // 랜더링 사이클이 발생하는 경우는 컴포넌트가 처음 마운트될 때, props가 변경될 때, state가 변경될 때 setDetail() 호출, 부모 컴포넌트가 리렌더링될 떄
    if (anime?.rating) {
      // anime?.rating으로 옵셔널 체이닝을 걸면 anime가 null/undefined면 undefined를 반환하고
      // anime가 있으면 anime.rating 을 반환함
      // anime가 있고 rating이 truthy 값이면 조건성립함
      // 그러니까 anime 객체가있고 rating 속성이 있어야하 조건이 성립함
      setCurrentRating(anime.rating);
      // anime 객체에 rating을 현재 평점에 세팅함
    }
  }, [anime]); // 의존성 배열에 anime 객체 할당
  // anime prop이 변경될 때마다 detail과 currentRating을 새 값으로 동기화해주는 useEffect임

  useEffect(() => {
    // 익명 함수를 정의해서 useEffect에 인자로 보내 함수를 호출함
    if (!isOpen) return;
    // 만약 isOpen이 false면 !로  true 치환해서 조건성립후 return 즉, 모달이 닫혀있으면 return 해서 바로 종료
    const id = (anime as any)?.aniId ?? (anime as any)?.id;
    // (anime as any)로 anime를 any타입으로 취급한다음 ?.aniId 옵셔널 체이닝걸어서 객체가 있는지 확인하고
    // 객체가 있고 aniId 값이 있으면 그걸 사용 만약에 aniId 속성값이 없으면 id를 사용함 
    // ??는 Nullish Coalescing Operator(널 병합 연산자)고 "truth면 왼쪽, falsh면 오른쪽"
    // 만약 anime 객체가 null / undefiend면 둘다 조건 성립이 안되면 undefiend가 반환되고 그 값이 id에 할당됨
    // const needsFetch = !Array.isArray((anime as any)?.genres) || (anime as any).genres.length === 0 || !Array.isArray((anime as any)?.episodes);
    // Array.isArray()는 JavaScript 내장 함수로, 값이 배열인지 확인하는 함수고 ㅕ기 인자로 anime.generes를 보내면
    // generes가 배열인지 확인하고 배열이면 true 배열이 아니면 false를 리턴하는데 !를 붙였으므로 배열이 아닐때 false -> true로 치환 조건성립임
    // 또는 gernes의 길이가 0 즉, 비어있으면 true로 조건 성립
    // episodes가 배열인지 확인하고 배열이 아니면 false를 리턴할테고 이걸 !로 치환해서 true로바꾸고 조건식 성립
    // 즉 gerners가 배열이 아니거나, gerners가 비어있거나, episodes가 배열이 아닐경우 실행되는 조건문임
    // generes가 배열이 아닌 경우, 배열이지만 비어있는 경우, epsodesr가 배열이 아닌 경우에는 needsFetch에 true가 들어가고
    // generes가 배열이고 비어있지 않은 경우, episodes가 배열인 경우에는 false값이 들어감
    // 참고로 논리합은 첫 번째 값이 true면 뒤의 값은 평가하지 않음
    // genere가 배열이고, 비어있지않고, episodes가 배열이면 true를 반환하는데 여기에 부정연산자를 넣어서 false로 치환해서
    // 조건식을 성립 안시키고 API를 호출하지않음
    // 즉 API를 호출하는 조건은 id가 truthy고 genres가 배열이아니고고 0이 아니고 epsidoes가 배열이 아닐때 애니 상세정보를 가져옴
    // 이 조건때문에 Anime 정보가 DB에 새롭게 저장되도 API를 호출을 안해서 주석처리
    // AnimeDetailModal이 열릴때마다 API를 매번 호출해서 최신 정보를 가져옴
    if (!id) return;  // id가 없으면 함수 실행 종료 가드
      getAnimeDetail(Number(id))
      // id값을 Number 타입으로 캐스팅한뒤  getAnimeDetail 함수에 태워보냄
      // getAnimeDetail 함수는 animeId를 number 타입으로 보내면 그 id에 해당하는 애니메이션의 상세 정보를 반환해주는 함수/ Promise를 반환
      // Promise는 비동기 작업의 결과를 나타내는 객체. 즉시 값을 반환하지 않고, 나중에 완료되면 값을 제공. async 함수는 항상 Promise를 반환
      // apiCall() 내부에서 response.json()이 any를 반환하므로 T는 명시적으로 지정히지않으면 Unknown이 됨
      // 사용하는 곳에서 타입 단언(as)를 사용해야함
        .then((d) => setDetail((prev: ExtendedAnime) => ({ ...prev, ...(d as Partial<ExtendedAnime>) })))
        // then(() => ...) 는 Promise가 성공하면 d에 API 응답 데이터가 들어옴
        // setDetail(..) 호출 - SetDetail의 인자는 함수임
        // setDetail이 내부적으로 이전 상태 prev를 인자로 함수를 호출함
        // { ...prev, ...(d as Partial<ExtendAnime) } 객체 생성
        // ...prev: 이전 detail 상태의 모든 속성을 펼침
        // ...(d as Partial<ExtendAnim): API에서 받은 d의 속성을 펼침
        // 뒤에 오는 속성이 앞의 속성을 덮어씀(병합)
        // 생성된 객체로 detail state 업데이트
        // d에는 Promise가 성공적으로(resolve)되었을때 반환된 값이 들어감 / animeId로 가져온 애니메이션의 상세 정보가 있는 객체
        // 그니까 상태 변경 함수는 인자로 함수를 받으면 React가 해당 상태 변경 함수에 state 값을 가져오고 그 값을 함수의 첫 번째 인자로 전달한뒤 함수를 호출함
        // 그 다음 함수가 반환한 값을 새로운 state로 설정함

        // Promise가 성공하면 Promise의 .then 메서드를 호출함
        // Promise가 reslove한 객체를 첫 번째 인자로 전달함 그럼 d에 전달되는것
        // 여기에 함수본문을 작성하는데 setDetail() 함수 호출을함
        // 익명함수는 prev 인자를 ExtendAnime 타입으로 받겠다는 거고 prev에는 React가 자동으로 상태 변경 함수에 state값을 가져오고
        // 그 값을 함수의 첫 번째 인자로 전달한뒤 함수를 호출하고 반환한 값을 새로운 state로 설정
        // Partial<T>는 타입 T의 모든 속성을 선택적(Optional)으로 만든다는것
        // 그니까 d를 모든 속성이 Otpinal인 ExtendAnime 타입으로 취급하는 타입 단언임
        // 따라서 d가 일부 속성만 있어도 타입 체크를 통과
        // id로 가져온 애니메이션 상세정보에 모든 속성을 Optional로 만들어줌 이 값을 prev값과 병합해서 반환함
        // prev는 전에 있던 값이고 현재 아직 안바뀐 anime 객체임 현재 state / d는 API로 새로 받아온 값

        .catch(() => {}); // Promise가 실패(recject)되면 호출되고 인자 없는 빈 함수를 전달 / 에러를 무시하고 아무 동작도 하지 않음
        // 이렇게 하는 이유는 API 호출 실패 시 에러를 무시하고 조용히 처리하기 위해
    
  }, [isOpen, anime?.id]); // 의존성 배열
  // anime prop에 새로운 anime 객체가 들어오면 감지하고 실행
  // isOpen이 변경되면 실행
  // 모달이 닫힐 때는 실행할 필요가 없는데 실행됨 리펙토링 필요함
  // anime를 anime?.id로 변경
  // 다른 애니면 감지 후 함수 실행에서
  // 다른 애니 클릭 -> 다른 id니까 실행됨 거기다 옵셔널 체인을 사용해서 더 방어적인 코드
  //   refactor: 애니메이션 상세 정보 fetch 로직 개선
  // - needsFetch 조건 제거하여 모달 열릴 때마다 최신 데이터 가져오기
  // - 의존성 배열을 [isOpen, anime?.id]로 최적화 - 2025-11-29
  // 나중에 Redis 캐싱 전략 추가해야함
  
  // 비슷한 작품 로드
  useEffect(() => {
    if (activeTab === 'similar' && similarAnimes.length === 0) {
        // 만약 activeTab이 similar고 similarAnimes.length가 0이면 즉, similarAnimes가 아직 비어있으면
      loadSimilarAnimes(); // 작품 로딩
    }
  }, [activeTab]); // activateTab이 변경될 때마다 이 useEffect가 다시 실행되고, 조건을 만족하면 loadSimilarAnimes 함수를 호출함
  // 함수가 실행문보다 아래에있는데 호출되는 이유는 리엑트의 실행 순서(컴포넌트 전체 실행 -> 그 안에서 함수들 정의 -> 렌더 끝
  // 이후에 useEffect 콜백 실행)때문이다.

  const loadSimilarAnimes = async () => {
    // 함수 본문을 작성해 재할당 불가 변수 loadSimilarAnimes 에 할당함
    // 이 함수는 비동기 함수임
    setIsLoadingSimilar(true); // 탭이 Simaila고 similarAnimes가 비어있을때 setIsLoadingSimilar 함수를 호출해서
    // 로딩 상태를 true로 설정함 그 다음 loadSimilarAnimes 호출해서 비슷한 작품 목록을 가져옴
    try { // try 블록은 예외가 발생할 수 있는 코드 블록임
      // 현재 작품과 장르가 겹치는 작품 목록을 조회
      const genreIds: number[] = Array.isArray(detail?.genres)
      // detail?.genres가 배열이면 그 배열을 number 배열에 넘기고 genereIds 변수에 할당
      // detail.genres가 객체 배열 일수도, 문자열이 섞여 있을 수도 있는데
      // listAnime는 gerneIds에 숫자 배열만 오기를 원하니 깨끗한 number[] 배열로 변환해서 넘겨줘야함
      // 배열이 아니면 Array.isArray에서 fasle가 나와서 number는 빈 배열 상태 그대로 유지
        ? (detail.genres as any[])
        // 여기서 쓰인 ?은 삼항 연산자
        // Array.isArray(detail.genres)가 true이면 detail.genres를 any[] 타입으로 단언
            .map((g: { id: number } | number) => Number(typeof g === 'object' ? g?.id : g))
            // .map 함수는 배열의 각 요소를 변환해서 새 배열을 만드는 함수
            // JavaSCript에서 Array.prototype.map (배열 메서드)로 사용
            // g는 { id: number } 형태의 객체 또는 그냥 number 타입임
            // 그니까 g에 파라미터 타입은 { id: number } 형태의 객체거나 number 둘 중 하나란 의미
            // g에는 배열의 각 요소를 map이 하나씩 꺼내서 함수 실행함
            // typeof g === 'object' ? g?.id : g는
            // g가 객체면 g.id를 사용 / g가 숫자면 숫자 그대로 사용한다는 의미
            // 그리고 이 조건식을 Number 함수로 감싸뒀으니 객체든 숫자돈 마지막에 Number로 숫자로 변환함
            .filter((v: number) => Number.isFinite(v))
            // filter는 조건에 맞는 값만 넘기는 함수
            // 파라미터 v는 number 타입임
            // v는 map(..)의 결과로 나온 number[] 배열을 filter가 다시 순회하면서 각 욧를 v에 넣고 콜백을 호출하는 형식
            //  Number.isFinite(v) 는 v가 유한한 숫자(finite number)일 때만 ture
            // NaN, Infinity, -Infinity같은 값은 fasle
            // filter는 콜백이 true를 반환하는 요소만 넘기고, false인 요소는 제거함
            // 전체 흐름은
            // .map(...)에서 g를 숫자로 변환해서 number[] 배열을 얻음
            // 그 다음 이 배열에서 이상한 숫자들을 제거하고 깨끗한 number[] (정상적인 장르 ID들만) 남김

            // Genres는 백엔드에서 객체배열로 내려와서 map으로 id만 추출해서 숫자 배열로 변환해줘야함
            //왜냐하면 listAnime는 숫자 배열만 받기때문에 객체배열로 주면 받지를 못함
        : []; // detail?.genres가 배열이 아니면 빈배열만 반환

      if (genreIds.length === 0) { // 만약 detail?gneres가 배열이 아니면 genreIds에 빈 배열이 들어가게 되는데 이때 실행됨
        console.log('⚠️ 비슷한 작품 로드: 장르 정보 없음'); // 콘솔 로그 출력
        setSimilarAnimes([]); // 비슷한 작품 목록을 빈 배열로 초기화
        return; // 함수 실행을 여기서 바로 종료료
      }
      // gerneIds 배열에 하나 이상에 값이 있으면 실행
      const response: any = await listAnime({ genreIds, sort: 'rating', page: 0, size: 30 });
      // listAnime는 Proimise를 반환하는 비동기 함수고 Promise가 완료될 때까지 기다렸다가 결과값이 오면 response에 할당함
      // listAnime에 파라미터로 아까 만든 깨끗한 number[] (장르 ID)들을 넘기고
      // 정령른 평점 기준으로 page는 0번 size즈는 한 번에 30개 가져와라고 기본값 세팅해서 태워보냄
      // response는 any 타입으로 선언해둠
      
      // 응답이 대기하다가 응답이 오면 실행
      const rawItems: ExtendedAnime[] = Array.isArray(response?.items)
      // response?.items가 배열이면 ExtendedAnime[] 타입으로 단언해서 rawItems에 할당함
      // 그리고 ?.은 옵셔널 체인을 건것으로 response가 null이나 undefiend면 undefined를 반환해서 오류가안뜨게함 방어코딩임
      // 1차로 response가 null / unidefined인지 아닌지 검증하고 2차로 resposne.items이 배열인지 아닌지 검증해서 반환해주는것
      // 재할당 불가 변수 rawImtes는 ExtendedAMNime[] 타입으로 정의
        ? (response.items as ExtendedAnime[]) // response.itmes가 배열이면 여기가 실행
        // response.items를 ExtendAnime[] 타입으로 단언함
        // rawImtes가 ExtendedAnime[] 타입이라 할당할려면 타입을 단언해줘야함
        : (Array.isArray(response) ? (response as ExtendedAnime[]) : []); // response.items가 배열이 아니면 여기가 실행
        // response.items가 배열인 경우는
        // 백앤드가 { imtes: [...], total, page, size } 이런 DTO를 보내는 경우에 해당
        // response 자체가 배열인 경우는
        // 백앤드가 그냥 [...] 배열만 보내는 경우(옜날 코드거나, 다른 API일 수도 있음)

      const baseId = Number((detail as any)?.aniId ?? (detail as any)?.id);
      // 여기서 쓰인 ??는 Nulish Coalescing이고 왼쪽이 null 또는 undefind일 떄만 오른쪽 사용함 flasy 전체가 아니라 null/undefined만 체크
      // ?.로 옵셔널 체인을 걸었으니 null/undefiend면 undefined를 반환함 detail이 null/undefeind이면
      // 양쪽 조건 둘다 false가 뜨고 ?. 옵셔널 체인이 undefined를 반환한 상태일태니 baseId에는 NAN이 들어감 (NUmber(undefined)) 결과
      // 만약 detail에 값이 있으면 detail.aniId를 쓰는데 여기 값이 없으면 undefined로 취급해서 detail.id를 사용하고
      // Number 타입으로 변환해서 baseId에 할당함
      const filtered = rawItems.filter((a: ExtendedAnime) => Number((a as any)?.aniId ?? (a as any)?.id) !== baseId);
      // 백엔드에서 받아온 비슷한 애니 목록인 rawItems에 filter 함수를 호출하고 인자로 익명 함수를 작성해서 태워보냄
      // 익명함수는 ExtendAnime 타입을 a로 받고 함수 본문은 a.aniID를 쓰고 없으면 a.id를 사용함 그 다음 그결과를 Number 타입으로 변환
      // baseId는 현재 열려있는 애니의 ID임
      // 그다음 !== baseId로 비교를하는데  가져온 각 애니의 ID가 baseId와 다르면 true로 필터링 되지않고 비슷한 작품 목록에 포함됨
      // 만약 가져온 애니의 ID가 baseId와 같으면 fasle로 필터링되고 제거됨 비슷한 작품 목록에서 제외
      // filter는 콜백함수가 true를 반환하면 그 요소를 남기고 false를 반환하면 그 요소를 제거함
      // 그러면 filtered에는 현재 열려있는 애니와 겹치지 않는 비슷한 작품 목록이 들어가게됨
    // 여기는 현재 띄워둔 애니메이션을 비슷한 작품 목록에서 제외시키는 로직

      // 중복 제거 (aniId 기준)
    // 가져온 비슷한 작품목록에서 같은 작품이 여러 번 나올 경우 하나만 남기는 로직
      const seen = new Set<number>();
      // Number 타입만 받을수 있는 Set 자료 구조를 seen 변수에 할당
      // Set은 중복 없는 값들의 집합을 표현할때 사용
      const unique = filtered.filter((a: ExtendedAnime) => {
        // 위에서 현재 열려있는 애니를 제외한 비슷한 작품 목록인 filtered에 filter 함수를 호출하고 익명함수를 작성해서 태워보냄
        // 익명함수는 인자 ExtendedAnime 타입을 a로 받음 이걸 함수 본문에서 사용할것
        // filter 함수가 filtered 배열의 각 요소를 a로 꺼내와서 콜백함수를 호출함
        // ture면 포함 false면 제거하는 식으로해서 조건을 통과해 true가 나온 요소를 unique 변수에 할당함
        const id = Number((a as any)?.aniId ?? (a as any)?.id);
        // ?.은 옵셔널 체인이고 설명은 생략
        // a가 있다고 가정시 aniId가 있으면 aniID를 사용 없으면 id를 사용함 
        // ??는 Nulish Coalescing 연산자고 왼쪽이 null 또는 undefiend 일 때 오른쪽 사용함
        // a에서 aniId 또는 id꺼내서 Number 타입으로 캐스팅후 재할당 불가 변수 id에 할당
        if (!Number.isFinite(id) || seen.has(id)) return false;
        // 만약 id가 유한숫자가 아니거나 seen 변수에 id가 있으면 fasle를 반환해서 제거됨
        seen.add(id); // 유한숫자고 seen 변수에 id가 없으면 여기가 실행
        // seen 변수에 위에서 가져온 id를 추가함
        return true; // true를 반환해서 포함됨
        // 여기서 unique는 최종적으로 화면에 보여줄 중복 없는 비슷한 작품 목록배열이고
        // seen은 중복 파별에만 사용하는 임시 변수임
        // 배열에서 중복인지 찾으려면 id로 직접 접근할 수 없어서
        // 매번 배열 전체를 순회해야해서 O(n)이 걸리지만
        // Set을 사용하면 O(1)로 중복 여부를 확인할 수 있으니 효율적임
        // seen.add(id)에서 id를 해쉬인덱스로바꿔서 그대로 내부 배열에 들어감
        // 이 다음 seen.has(id)를 호출하면 id를 해쉬인덱스로 사용해서 바로 접근하기때문에 O(1)로 중복 여부를 확인할 수 있음
        // 대용량이거나 중복체크를 반복적으로 자주 할수록 Set 방식을 같이 써야함
      });

      const limited = unique.slice(0, 6);
      // slice 함수는 첫번째 인자를 여기서부터 자르라는 기준으로 쓰고, 두 번째 인자를 여기 인덱스 '앞'까지 잘라낸다는 의미로 사용
      // unique 배열에 0번 인겍스부터 6번 인덱스에 앞까지 자르란뜻 그러면 0 1 2 3 4 5 즉 6개가 짤려나옴
      // 그러면 비슷한 작품 목록이 13개가 불려와도 앞에 최대 6개까지만 제한되게 나오게됨 
      console.log('📦 비슷한 작품 로드 결과:', limited.length, '(장르 기반)');
      setSimilarAnimes(limited); // 비슷한 작품 목록 상태에 최종적으로 화면에 보여줄 작품 목록 limited 배열을 할당함
    } catch (error) {
      console.error('비슷한 작품 로드 실패:', error);
      setSimilarAnimes([]); // 빈 배열로 초기화
    } finally { // try/catch가 성공하든 실패하든 무조건 마지막에 실행됨
      setIsLoadingSimilar(false); // 로딩 상태를 false로 세팅하고 함수 마무리
    }
  };

  // 특정 애니메이션의 시청 기록 조회
  useEffect(() => {
    // 익명함수 정의해서 useEffect에게 넘기는 형식
    if (!isOpen || !(detail as any)?.aniId) return;
    // 만약 isOpen이 false이거나 detail.aniId가 없으면 함수 종료
    console.log('🔍 시청 기록 조회 시작 - animeId:', (detail as any).aniId);
    setIsLoadingHistory(true);
    // 시청기록 로딩 상태를 true로 설정
    getAnimeWatchHistory(Number((detail as any).aniId))
    // user.ts 파일에 getAnimeWatchHistory 함수에 detail.aniId를 넘겨서 호출함 이 함수는 비동기함수임
      .then((history: any) => { 
        // .then()은 Promise가 성공적으로 끝난 다음 그 결과값을 가지고 추가 작업(콜백 함수)을 실행할 때 사용하는 함수임
        // 비동기 함수는 항상 Promise를 리턴하고 .then() 함수 체인연결이 가능함
        // 인자로 익며함수를 정의해서 보내는 형식
        // history를 any타입으로 받을것임
        // history에는 특정 애니의 여러 에피소드 시청기록이 배열로 들어오는 구조임
        console.log('🔍 시청 기록 조회 결과:', history);
        // 시청기록 상태를 업데이트하고 화면에 보여줌
        setWatchHistory(history as WatchHistory);
        // 가져온 특정 애니의 시청기록을 WatchHistory 타입으로 단언한뒤 watchHistory 상태에 할당함
      })
      .catch((error) => {
        // .catch()는 Promise가 실패한 다음 그 에러를 가지고 추가 작업(콜백 함수)을 실행할 때 사용하는 함수임
        console.error('시청 기록 조회 실패:', error);
        setWatchHistory(null);
        // 시청기록 상태를 null로 초기화
      })
      .finally(() => {
        // .finally()는 Promise가 성공하든 실패하든 무조건 마지막에 실행됨
        setIsLoadingHistory(false);
        // 시청기록 로딩 상태를 false로 설정
      });
  }, [isOpen, detail?.aniId]);
  // 의존성 배열로 isOpen과 detail?.aniId를 받고 렌더링 사이클마다 이 값들을 확인하고 변경시 useEEffect에 콜백함수를 실행함

  // 보고싶다 상태 확인
  useEffect(() => {
      // 익명함수 만들어서 useEffect에 넘기는 형식
    if (!isOpen || !(detail as any)?.aniId) return;
    // 만약 isOpen이 false로 들어오거나 또는 detail.aniId가 없을때 함수 종료
    isFavorited(Number((detail as any).aniId)) // isOpen이 true 또는 detail.aniId가 있으면 여기부터 실행
    // 특정 애니메이션의 보고싶다 상태를 확인하는 함수 isFavorited에 detail.aniId를 any 타입으로 단언하고 Number로 캐스팅해서 넘김
      .then((favorited) => {
          // 비동기 함수가 올바른 응답에 성공해 Promise가 resolve되면 그 값이
          // .then 콜백의 첫 번째 인자로 전달됨 즉, isFavorited에 응답값이 favorited 인자에 값이 전달됨 
          // .then 함수에 익명함수를 정의해서 넘기는 형식임
          // 익명함수는 인자로 favorited를 받고 화살표 함수를 사용해 함수 본문을 작성
        setIsFavoritedState(favorited);
        // 보고싶다 상태값에 favorited 값을 할당함 / ture / false
      })
      .catch((error) => { // .catch()는 Promise가 실패한 다음 그 에러를 가지고 추가 작업(콜백 함수)을 실행할 때 사용하는 함수임
        console.error('보고싶다 상태 조회 실패:', error); // 보고싶다 상태 조회 실패 에러 출력
        setIsFavoritedState(false);
        // 보고싶다 상태값을 false로 설정함
      });
  }, [isOpen, detail?.aniId]);
  // 의존성 배열로 isOpen과 detail?.aniId를 받고 렌더링 사이클마다 이 값들을 확인하고 변경시 useEEffect에 콜백함수를 실행함

  // 라프텔 방식: 모달 열 때 CSS 동적 주입
  useEffect(() => {
    if (isOpen) {
        // 만약 모달이 열려있으면
      // html 태그에 data-theme="light" 추가
      document.documentElement.setAttribute('data-theme', 'light');
      //
      
      // body에 overflow: hidden !important 적용
      document.body.style.overflow = 'hidden';
      document.body.style.setProperty('overflow', 'hidden', 'important');
      //
    } else {
        // 모달이 열려있지 않으면
      // 모달 닫을 때 원래 상태로 복원
      document.documentElement.removeAttribute('data-theme');
      document.body.style.overflow = 'auto';
      document.body.style.removeProperty('overflow');
    }

    // 컴포넌트 언마운트 시 정리
    return () => {
      document.documentElement.removeAttribute('data-theme');
      document.body.style.overflow = 'auto';
      document.body.style.removeProperty('overflow');
    };
  }, [isOpen]);

  // 디버깅: anime 객체 확인
  console.log('🔍 AnimeDetailModal - anime 객체:', detail);
  console.log('🔍 AnimeDetailModal - anime.aniId:', (detail as any)?.aniId);
  console.log('🔍 AnimeDetailModal - anime 타입:', typeof detail);
  console.log('🔍 장르 정보:', (detail as any)?.genres);
  console.log('🔍 평점 정보:', detail?.rating);
  console.log('🔍 관람등급:', detail?.ageRating);
  console.log('🔍 줄거리:', (detail as any)?.fullSynopsis || (detail as any)?.synopsis);
  console.log('🔍 에피소드:', (detail as any)?.episodes);
  console.log('🔍 시청 기록 상태:', {
    watchHistory,
    isLoadingHistory,
    hasWatchHistory: !!watchHistory,
    isCompleted: (watchHistory as any)?.completed,
    episodeNumber: (watchHistory as any)?.episodeNumber,
    positionSec: (watchHistory as any)?.positionSec,
    shouldShowContinue: !isLoadingHistory && !!watchHistory && !(watchHistory as any).completed,
    shouldShowPlay: !isLoadingHistory && (!watchHistory || (watchHistory as any).completed)
  });

  if (!isOpen) return null;

  const tabs: { id: 'episodes' | 'reviews' | 'shop' | 'similar'; label: string; count: number | null }[] = [
    { id: 'episodes', label: '에피소드', count: null },
    { id: 'reviews', label: '사용자 평', count: null },
    { id: 'shop', label: '상점', count: null },
    { id: 'similar', label: '비슷한 작품', count: null }
  ];

  const episodes = Array.isArray((detail as any)?.episodes) ? ((detail as any).episodes as Episode[]) : [];
  const getFallbackEpisodeThumb = (episodeNumber?: number) => {
    const n = Number(episodeNumber);
    if (n === 1) return 'https://placehold.co/120x80/111827/ffffff?text=EP1+Thumbnail';
    if (n === 2) return 'https://placehold.co/120x80/1f2937/ffffff?text=EP2+Thumbnail';
    return 'https://placehold.co/120x80/374151/ffffff?text=Episode';
  };

  return (
    <div className={styles.animeDetailModalOverlay}>
      {/* 배경 오버레이 */}
      <div 
        className={styles.animeDetailModalBackdrop}
        onClick={onClose}
      />
      
      {/* 모달 컨테이너 */}
      <div className={`${styles.animeDetailModalContainer} ${isFullInfoOpen ? styles.dimTabs : ''}`}>
        {/* 점3개 메뉴 버튼 - X버튼 왼쪽 */}
        <div className={styles.menuButtonContainer}>
          <button
            onClick={() => setIsDropdownOpen(!isDropdownOpen)}
            className={styles.menuButton}
            aria-label="메뉴"
          >
            <svg fill="currentColor" viewBox="0 0 24 24">
              <path d="M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"/>
            </svg>
          </button>
          
          {/* 드롭다운 메뉴 */}
          {isDropdownOpen && (
            <div className={styles.dropdownMenu}>
              <button
                onClick={() => {
                  setShowDeleteConfirm(true);
                  setIsDropdownOpen(false);
                }}
                className={styles.dropdownItem}
              >
                시청 기록 초기화
              </button>
            </div>
          )}
        </div>

        {/* 닫기 버튼 - 상단 오른쪽 */}
        <button
          onClick={onClose}
          className={styles.animeDetailModalCloseButton}
          aria-label="닫기"
        >
          <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>

        {/* 상단 정보 섹션 */}
        <div className={styles.topInfoSection}>
          {/* 배경 이미지: DB의 backdropUrl을 우선 사용, 없으면 다크 배경만 */}
          <div className={styles.backgroundImage}>
            <div className={styles.backgroundContainer}>
              {detail?.backdropUrl ? (
                <div
                  className={styles.characterImage}
                  style={{ backgroundImage: `url(${detail.backdropUrl})` }}
                />
              ) : (
                <div className={`${styles.characterImage} ${styles.noBackdrop}`} />
              )}
            </div>
          </div>

          {/* 작은 포스터 - 오른쪽 중간에 위치 */}
          <div className={styles.smallPoster}>
            <div className={styles.posterContainer}>
              <img 
                src={detail?.posterUrl || "https://placehold.co/96x128/ff69b4/ffffff?text=LAFTEL+ONLY"} 
                alt={`${(detail?.title || detail?.titleEn || detail?.titleJp || '애니메이션')} 포스터`}
                className={styles.posterImage}
              />
            </div>
          </div>

          {/* 상단 정보 오버레이 */}
          <div className={styles.topInfoOverlay}>
              {/* 평점 및 배지 - 왼쪽 상단 */}
              <div className={styles.ratingSection}>
                <div className={styles.ratingContainer}>
                  <span className={styles.ratingStar}>★</span>
                  <span className={styles.ratingValue}>
                    {typeof currentRating === 'number' ? currentRating.toFixed(1) : 'N/A'}
                  </span>
                </div>
                <span className={styles.ratingBadge}>
                  {Array.isArray((detail as any)?.badges) ? (detail as any).badges[0] : 'ONLY'}
                </span>
              </div>

              {/* 애니메이션 제목 */}
              <h1 className={styles.animeTitle}>
                {(() => {
                  // 더빙과 자막 여부 확인
                  const isDub = (detail as any)?.isDub === true;
                  const isSubtitle = (detail as any)?.isSubtitle === true;
                  
                  let prefix = '';
                  if (isDub && isSubtitle) {
                    // 둘 다 true인 경우 자막으로 표시
                    prefix = '(자막) ';
                  } else if (isDub) {
                    prefix = '(더빙) ';
                  } else if (isSubtitle) {
                    prefix = '(자막) ';
                  }
                  
                  const title = (detail as any)?.title || (detail as any)?.titleEn || (detail as any)?.titleJp || '제목 없음';
                  return `${prefix}${title}`;
                })()}
              </h1>

              {/* 장르 및 정보 */}
              <div className={styles.genreSection}>
                {Array.isArray((detail as any)?.genres) && (detail as any).genres.length > 0 ? (
                  ((detail as any).genres as Array<string | { name?: string }>).slice(0, 6).map((g: any, idx: number) => (
                    <span key={idx} className={styles.genreTag}>
                      {typeof g === 'string' ? g : (g?.name || '')}
                    </span>
                  ))
                ) : (
                  <span className={styles.genreTag}>장르 정보 없음</span>
                )}
                
                {/* 애니메이션 타입·상태 */}
                <span className={styles.typeStatusBadge}>
                  {(detail as any)?.type || 'TV'}·{(detail as any)?.animeStatus === 'COMPLETED' ? '완결' : 
                   (detail as any)?.animeStatus === 'ONGOING' ? '방영중' : 
                   (detail as any)?.animeStatus === 'UPCOMING' ? '예정' : 
                   (detail as any)?.animeStatus === 'CANCELLED' ? '중단' : '완결'}
                </span>
                
                {/* 관람등급 */}
                <div className={styles.ageRatingBadge}>
                  <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <circle cx="10" cy="10" r="9" fill="#E9B62F" stroke="#FFFFFF" strokeWidth="2" />
                    <text x="10" y="10" textAnchor="middle" dominantBaseline="central" fill="#000" fontSize="7" fontWeight="700">
                      {(() => {
                        const rating = detail?.ageRating;
                        if (rating === '전체 이용가') return 'ALL';
                        if (rating === '15세이상') return '15';
                        if (rating === '12세이상') return '12';
                        if (rating === '19세이상') return '19';
                        if (rating === 'ALL') return 'ALL';
                        return 'ALL';
                      })()}
                    </text>
                  </svg>
                </div>
                
              </div>

              {/* 액션 버튼들 */}
              <div className={styles.animeDetailModalActionButtons}>
                {/* 로딩 중일 때 */}
                {isLoadingHistory && (
                  <div className={styles.loadingMessage}>시청 기록을 불러오는 중...</div>
                )}
                
                {/* 이어보기 버튼 - 시청 기록이 있고 완료되지 않은 경우 */}
                {!isLoadingHistory && watchHistory && !watchHistory.completed && (
                  <div className={styles.playButtonContainer}>
                    <button 
                      onClick={() => {
                        console.log('🎬 이어보기 버튼 클릭:', {
                          episodeId: (watchHistory as any).episodeId,
                          animeId: (detail as any)?.aniId,
                          positionSec: (watchHistory as any).positionSec,
                          episodeNumber: (watchHistory as any).episodeNumber
                        });
                        // 이어보기: 마지막으로 본 에피소드부터 재생
                        const position = (watchHistory as any).positionSec > 0 ? `&position=${(watchHistory as any).positionSec}` : '';
                        const url = `/player?episodeId=${(watchHistory as any).episodeId}&animeId=${(detail as any)?.aniId}${position}`;
                        console.log('🔗 이동할 URL:', url);
                        router.push(url);
                        onClose();
                      }}
                      className={styles.playButton}
                    >
                      <div className={styles.playButtonIcon}>
                        <svg fill="currentColor" viewBox="0 0 24 24">
                          <path d="M8 5v14l11-7z"/>
                        </svg>
                      </div>
                      <span className={styles.playButtonText}>{watchHistory.episodeNumber}화 이어보기</span>
                    </button>
                  </div>
                )}
                
                {/* 처음보기 또는 완료된 경우 보러가기 버튼 */}
                {!isLoadingHistory && (!watchHistory || watchHistory.completed) && (
                  <div className={styles.playButtonContainer}>
                    <button 
                      onClick={() => {
                        console.log('🎬 재생하기 버튼 클릭:', {
                          watchHistory,
                          hasWatchHistory: !!watchHistory,
                          isCompleted: (watchHistory as any)?.completed,
                          animeId: (detail as any)?.aniId
                        });
                        
                        // 시청 기록이 있지만 완료된 경우: 다음 에피소드부터 시작
                        // 시청 기록이 없는 경우: 1화부터 시작
                        let nextEpisodeId = 1;
                        if (watchHistory && (watchHistory as any).completed) {
                          // 완료된 경우 다음 에피소드
                          nextEpisodeId = (watchHistory as any).episodeNumber + 1;
                        }
                        
                        const url = `/player?episodeId=${nextEpisodeId}&animeId=${(detail as any)?.aniId}`;
                        console.log('🔗 이동할 URL:', url);
                        router.push(url);
                        onClose();
                      }}
                      className={styles.playButton}
                    >
                      <div className={styles.playButtonIcon}>
                        <svg fill="currentColor" viewBox="0 0 24 24">
                          <path d="M8 5v14l11-7z"/>
                        </svg>
                      </div>
                      <span className={styles.playButtonText}>
                        {watchHistory && (watchHistory as any).completed 
                          ? `${(watchHistory as any).episodeNumber + 1}화 재생하기`
                          : '1화 재생하기'
                        }
                      </span>
                    </button>
                  </div>
                )}
                
                {/* 보고싶다 버튼 */}
                <div className={styles.favoriteButtonContainer}>
                  <button 
                    onClick={async () => {
                      if (isLoadingFavorite) return;
                      
                      try {
                        setIsLoadingFavorite(true);
                        const newState = await toggleFavorite(Number((detail as any)?.aniId));
                        setIsFavoritedState(newState);
                        console.log('보고싶다 토글 완료:', newState);
                      } catch (error) {
                        console.error('보고싶다 토글 실패:', error);
                        alert('보고싶다 기능을 사용할 수 없습니다.');
                      } finally {
                        setIsLoadingFavorite(false);
                      }
                    }}
                    disabled={isLoadingFavorite}
                    className={`${styles.favoriteButton} ${isFavoritedState ? styles.favorited : ''}`}
                  >
                    <div className={styles.favoriteButtonContent}>
                      {isFavoritedState ? (
                        <svg 
                          className={styles.checkIcon} 
                          fill="currentColor" 
                          viewBox="0 0 24 24"
                        >
                          <path d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/>
                        </svg>
                      ) : (
                        <span className={styles.plusIcon}>+</span>
                      )}
                      <span className={styles.favoriteButtonText}>
                        {isFavoritedState ? '보관중' : '보고싶다'}
                      </span>
                    </div>
                  </button>
                  <div className={styles.favoriteTooltip}>
                    {isFavoritedState ? '보관함에서 제거' : '보관함에 추가'}
                  </div>
                </div>
                
                {/* 공유 버튼 */}
                <button className={`${styles.animeDetailModalActionButton} ${styles.animeDetailModalActionButtonSecondary}`}>
                  <svg fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.367 2.684 3 3 0 00-5.367-2.684z" />
                  </svg>
                  <span>공유</span>
                </button>
              </div>

              {/* 줄거리 */}
              <div className={styles.synopsisSection}>
                {(() => {
                  const raw = (((detail as any)?.fullSynopsis ?? (detail as any)?.synopsis ?? "")).toString().trim();
                  const isLong = raw.length > MAX_SYNOPSIS_CHARS;
                  const text = showFullSynopsis || !isLong ? raw : `${raw.slice(0, MAX_SYNOPSIS_CHARS)}…`;
                  return (
                    <div className={styles.synopsisInlineRow}>
                      <span className={styles.synopsisText}>{text || "줄거리 정보가 없습니다."}</span>
                      {isLong && (
                        <button
                          type="button"
                          className={styles.synopsisToggle}
                          onClick={() => {
                            if (!showFullSynopsis) {
                              // 처음 '더보기' 누르면 별도 전체 정보 모달을 띄움
                              setIsFullInfoOpen(true);
                            } else {
                              setShowFullSynopsis(false);
                            }
                          }}
                          aria-expanded={showFullSynopsis}
                        >
                          {showFullSynopsis ? '접기' : '더보기'}
                        </button>
                      )}
                    </div>
                  );
                })()}
              </div>
            {/* 전체 작품 정보 모달 */}
            <AnimeFullInfoModal isOpen={isFullInfoOpen} onClose={() => setIsFullInfoOpen(false)} detail={detail} />
          </div>
        </div>

        {/* 시청 기록 초기화 확인 모달 */}
        {showDeleteConfirm && (
          <div className={styles.confirmModalOverlay}>
            <div className={styles.confirmModal}>
              <h3 className={styles.confirmModalTitle}>시청 기록 초기화</h3>
              <p className={styles.confirmModalMessage}>
                이 작품의 모든 시청 기록이 완전히 삭제됩니다.<br/>
                정말로 초기화하시겠습니까?
              </p>
              <div className={styles.confirmModalButtons}>
                <button
                  onClick={() => setShowDeleteConfirm(false)}
                  className={styles.confirmModalCancel}
                >
                  취소
                </button>
                <button
                  onClick={handleDeleteWatchHistory}
                  className={styles.confirmModalConfirm}
                >
                  확인
                </button>
              </div>
            </div>
          </div>
        )}

        {/* 탭 메뉴 */}
        <div className={styles.tabMenu}>
          <div className={styles.tabContainer}>
            {tabs.map((tab) => (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`${styles.tabButton} ${activeTab === tab.id ? styles.active : ''}`}
              >
                <span className={styles.tabLabel}>{tab.label}</span>
                {tab.count !== null && (
                  <span className={styles.tabCount}>({tab.count})</span>
                )}
              </button>
            ))}
          </div>
        </div>

        {/* 탭 콘텐츠 */}
        <div className={styles.tabContent}>
          {activeTab === 'episodes' && (
            <div className={styles.episodesSection}>
              <h3 className={styles.episodesTitle}>에피소드 목록</h3>
              <div className={styles.episodesList}>
                {episodes.length > 0 ? (
                  episodes.map((episode: Episode) => (
                  <div 
                    key={episode.id} 
                    className={styles.episodeItem}
                    onClick={() => {
                      // 플레이어 페이지로 이동 (현재 탭에서)
                      router.push(`/player?episodeId=${episode.id}&animeId=${detail?.aniId ?? detail?.id}`);
                      onClose(); // 모달 닫기
                    }}
                    style={{ cursor: 'pointer' }}
                  >
                    <div className={styles.episodeThumbnail}>
                      <img 
                        src={episode.thumbnailUrl || getFallbackEpisodeThumb(episode.episodeNumber)} 
                        alt={episode.title}
                        className={styles.episodeThumbnailImage}
                      />
                    </div>
                    <div className={styles.episodeInfo}>
                      <div className={styles.episodeHeader}>
                        <h4 className={styles.episodeTitle}>
                          {episode.episodeNumber}화
                        </h4>
                        <div className={styles.episodeMeta}>
                          <span>{episode.duration ? `${episode.duration}분` : ''}</span>
                          <span>{episode.createdAt ? String(episode.createdAt).slice(0,10) : ''}</span>
                        </div>
                      </div>
                      <p className={styles.episodeDescription}>
                        {episode.description || ''}
                      </p>
                    </div>
                  </div>
                ))
                ) : (
                  <div className={styles.emptyState}>에피소드 정보가 없습니다.</div>
                )}
              </div>
            </div>
          )}

          {/* 리뷰 탭: ReviewList 항상 마운트되도록 렌더링, 탭 아닐 때는 hidden 처리 */}
          <div className={styles.reviewsSection} style={{ display: activeTab === 'reviews' ? 'block' : 'none' }}>
            {detail?.aniId ? (
              <ReviewList 
                key={detail?.aniId ?? detail?.id} 
                animeId={(detail?.aniId ?? detail?.id) as number} 
                onRatingChange={handleRatingChange}
              />
            ) : (
              <div className={styles.reviewsError}>
                <p className={styles.reviewsErrorMessage}>⚠️ 애니메이션 ID를 찾을 수 없습니다.</p>
                <p className={styles.reviewsErrorDetails}>
                  anime 객체: {JSON.stringify(detail, null, 2)}
                </p>
              </div>
            )}
          </div>

          {activeTab === 'shop' && (
            <div className={styles.shopSection}>
              상점 기능은 준비 중입니다
            </div>
          )}

          {activeTab === 'similar' && (
            <div className={styles.similarSection}>
              {isLoadingSimilar ? (
                <div className={styles.loadingContainer}>
                  비슷한 작품을 불러오는 중...
                </div>
              ) : similarAnimes.length > 0 ? (
                <div className={styles.similarGrid}>
                  {similarAnimes.map((anime: Anime, index: number) => {
                    const a = anime as unknown as ExtendedAnime;
                    const itemId = Number((a as any)?.aniId ?? (a as any)?.id ?? index);
                    const title = (a as any)?.title || (a as any)?.titleEn || (a as any)?.titleJp || '제목 없음';
                    const posterUrl =
                      (a as any)?.posterUrl ||
                      (a as any)?.imageUrl ||
                      (a as any)?.thumbnail ||
                      (a as any)?.posterImage ||
                      '/icons/default-avatar.svg';

                    return (
                      <AnimeCard
                        key={`${itemId}-${title}`}
                        aniId={itemId}
                        title={title}
                        posterUrl={posterUrl}
                        rating={typeof (a as any)?.rating === 'number' ? (a as any).rating : null}
                        badge={Array.isArray((a as any)?.badges) ? (a as any).badges[0] : undefined}
                        onClick={() => {
                          onClose();
                          router.push(`/player?animeId=${itemId}`);
                        }}
                      />
                    );
                  })}
                </div>
              ) : (
                <div className={styles.emptyState}>
                  추천할 작품이 없습니다.
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
