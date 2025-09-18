"use client";
import { useEffect, useState, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";

/**
 * OAuth2 소셜 로그인 실패 페이지
 * 에러 메시지를 표시하고 홈으로 돌아갈 수 있는 옵션 제공
 */
function OAuth2FailureContent() {
  const [errorMessage, setErrorMessage] = useState<string>("");
  const router = useRouter();
  const searchParams = useSearchParams();

  useEffect(() => {
    // URL 파라미터에서 에러 메시지 추출
    const error = searchParams.get('error');
    if (error) {
      setErrorMessage(decodeURIComponent(error));
    }
  }, [searchParams]);

  const handleRetry = () => {
    router.push('/');
  };

  return (
    <div >
      <div >
        <div >
          <div >
            <svg  fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </div>
          <h2 >로그인 실패</h2>
          <p >소셜 로그인 중 문제가 발생했습니다.</p>
        </div>

        {errorMessage && (
          <div >
            <p >{errorMessage}</p>
          </div>
        )}

        <div >
          <button
            onClick={handleRetry}
            
          >
            홈으로 돌아가기
          </button>
          
          <Link
            href="/login"
            
          >
            다시 시도하기
          </Link>
        </div>

        <div >
          <p>문제가 지속되면 관리자에게 문의해주세요.</p>
        </div>
      </div>
    </div>
  );
}

export default function OAuth2FailurePage() {
  return (
    <Suspense fallback={<div><div>로딩 중...</div></div>}>
      <OAuth2FailureContent />
    </Suspense>
  );
}
