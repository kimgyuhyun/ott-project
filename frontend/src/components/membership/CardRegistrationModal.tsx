"use client";

interface CardRegistrationModalProps {
  isOpen: boolean;
  onClose: () => void;
  onBack: () => void;
  onSubmit: () => void;
}

export default function CardRegistrationModal({
  isOpen,
  onClose,
  onBack,
  onSubmit,
}: CardRegistrationModalProps) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 flex items-center justify-center z-50 p-4" style={{ backgroundColor: 'var(--background-dim-1, rgba(0,0,0,0.7))' }}>
      <div className="rounded-lg p-6 max-w-md w-full border max-h-[90vh] overflow-y-auto" style={{ backgroundColor: 'var(--background-1, #121212)', borderColor: 'var(--border-1, #323232)' }}>
        {/* 모달 헤더 */}
        <div className="flex justify-between items-center mb-6">
          <button
            onClick={onBack}
            className="text-xl hover:opacity-80 transition-opacity"
            style={{ color: 'var(--foreground-1, #F7F7F7)' }}
          >
            ←
          </button>
          <h3 className="text-xl font-bold" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>간편 결제 등록</h3>
          <button
            onClick={onClose}
            className="text-xl hover:opacity-80 transition-opacity"
            style={{ color: 'var(--foreground-1, #F7F7F7)' }}
          >
            ×
          </button>
        </div>

        {/* 카드 정보 입력 */}
        <div className="space-y-6">
          <div>
            <h4 className="font-semibold mb-3" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>카드 정보 입력</h4>
            
            {/* 카드 번호 */}
            <div className="mb-4">
              <label className="block text-sm mb-2" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>카드 번호</label>
              <div className="flex space-x-2">
                <input
                  type="text"
                  placeholder="0000"
                  maxLength={4}
                  className="flex-1 p-2 rounded outline-none"
                  style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
                />
                <span style={{ color: 'var(--foreground-1, #F7F7F7)' }}>-</span>
                <input
                  type="text"
                  placeholder="0000"
                  maxLength={4}
                  className="flex-1 p-2 rounded outline-none"
                  style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
                />
                <span style={{ color: 'var(--foreground-1, #F7F7F7)' }}>-</span>
                <input
                  type="text"
                  placeholder="0000"
                  maxLength={4}
                  className="flex-1 p-2 rounded outline-none"
                  style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
                />
                <span style={{ color: 'var(--foreground-1, #F7F7F7)' }}>-</span>
                <input
                  type="text"
                  placeholder="0000"
                  maxLength={4}
                  className="flex-1 p-2 rounded outline-none"
                  style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
                />
              </div>
            </div>

            {/* 유효기간 */}
            <div className="mb-4">
              <label className="block text-sm mb-2" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>유효기간</label>
              <input
                type="text"
                placeholder="MM/YY"
                maxLength={5}
                className="w-full p-2 rounded outline-none"
                style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
              />
            </div>

            {/* 생년월일 */}
            <div className="mb-4">
              <label className="block text-sm mb-2" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>생년월일</label>
              <input
                type="text"
                placeholder="YYMMDD (6자리)"
                maxLength={6}
                className="w-full p-2 rounded outline-none"
                style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
              />
            </div>
          </div>

          {/* 카드 비밀번호 */}
          <div>
            <h4 className="font-semibold mb-3" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>카드 비밀번호</h4>
            <div className="mb-4">
              <label className="block text-sm mb-2" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>카드 비밀번호</label>
              <p className="text-xs mb-2" style={{ color: 'var(--foreground-3, #ABABAB)' }}>비밀번호 앞 2자리</p>
              <input
                type="password"
                maxLength={2}
                className="w-full p-2 rounded outline-none"
                style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
              />
            </div>
          </div>

          {/* 동의 체크박스 */}
          <div className="mb-6">
            <label className="flex items-start cursor-pointer">
              <input
                type="checkbox"
                defaultChecked
                className="mr-3 mt-1"
                style={{ accentColor: 'var(--foreground-slight, #816BFF)' }}
              />
              <span className="text-sm" style={{ color: 'var(--foreground-1, #F7F7F7)' }}>
                결제사 정보 제공에 동의합니다.
              </span>
            </label>
          </div>

          {/* 등록하기 버튼 */}
          <button
            onClick={onSubmit}
            className="w-full py-4 rounded-lg font-bold transition-colors"
            style={{ backgroundColor: 'var(--button-slight-1, #323232)' }}
          >
            <span style={{ color: 'var(--foreground-1, #F7F7F7)' }}>등록하기</span>
          </button>
        </div>
      </div>
    </div>
  );
}
