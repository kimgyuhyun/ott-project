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
    <div  style={{ backgroundColor: 'var(--background-dim-1, rgba(0,0,0,0.7))' }}>
      <div  style={{ backgroundColor: 'var(--background-1, #121212)', borderColor: 'var(--border-1, #323232)' }}>
        {/* 모달 헤더 */}
        <div >
          <button
            onClick={onBack}
            style={{ color: 'var(--foreground-1, #F7F7F7)' }}
          >
            ←
          </button>
          <h3  style={{ color: 'var(--foreground-1, #F7F7F7)' }}>간편 결제 등록</h3>
          <button
            onClick={onClose}
            style={{ color: 'var(--foreground-1, #F7F7F7)' }}
          >
            ×
          </button>
        </div>

        {/* 카드 정보 입력 */}
        <div >
          <div>
            <h4  style={{ color: 'var(--foreground-1, #F7F7F7)' }}>카드 정보 입력</h4>
            
            {/* 카드 번호 */}
            <div >
              <label  style={{ color: 'var(--foreground-1, #F7F7F7)' }}>카드 번호</label>
              <div >
                <input
                  type="text"
                  placeholder="0000"
                  maxLength={4}
                  
                  style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
                />
                <span style={{ color: 'var(--foreground-1, #F7F7F7)' }}>-</span>
                <input
                  type="text"
                  placeholder="0000"
                  maxLength={4}
                  
                  style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
                />
                <span style={{ color: 'var(--foreground-1, #F7F7F7)' }}>-</span>
                <input
                  type="text"
                  placeholder="0000"
                  maxLength={4}
                  
                  style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
                />
                <span style={{ color: 'var(--foreground-1, #F7F7F7)' }}>-</span>
                <input
                  type="text"
                  placeholder="0000"
                  maxLength={4}
                  
                  style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
                />
              </div>
            </div>

            {/* 유효기간 */}
            <div >
              <label  style={{ color: 'var(--foreground-1, #F7F7F7)' }}>유효기간</label>
              <input
                type="text"
                placeholder="MM/YY"
                maxLength={5}
                
                style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
              />
            </div>

            {/* 생년월일 */}
            <div >
              <label  style={{ color: 'var(--foreground-1, #F7F7F7)' }}>생년월일</label>
              <input
                type="text"
                placeholder="YYMMDD (6자리)"
                maxLength={6}
                
                style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
              />
            </div>
          </div>

          {/* 카드 비밀번호 */}
          <div>
            <h4  style={{ color: 'var(--foreground-1, #F7F7F7)' }}>카드 비밀번호</h4>
            <div >
              <label  style={{ color: 'var(--foreground-1, #F7F7F7)' }}>카드 비밀번호</label>
              <p  style={{ color: 'var(--foreground-3, #ABABAB)' }}>비밀번호 앞 2자리</p>
              <input
                type="password"
                maxLength={2}
                
                style={{ backgroundColor: 'var(--background-2, #000000)', borderBottom: '2px solid var(--border-1, #323232)', color: 'var(--foreground-1, #F7F7F7)' }}
              />
            </div>
          </div>

          {/* 동의 체크박스 */}
          <div >
            <label >
              <input
                type="checkbox"
                defaultChecked
                
                style={{ accentColor: 'var(--foreground-slight, #816BFF)' }}
              />
              <span  style={{ color: 'var(--foreground-1, #F7F7F7)' }}>
                결제사 정보 제공에 동의합니다.
              </span>
            </label>
          </div>

          {/* 등록하기 버튼 */}
          <button
            onClick={onSubmit}
            
            style={{ backgroundColor: 'var(--button-slight-1, #323232)' }}
          >
            <span style={{ color: 'var(--foreground-1, #F7F7F7)' }}>등록하기</span>
          </button>
        </div>
      </div>
    </div>
  );
}
