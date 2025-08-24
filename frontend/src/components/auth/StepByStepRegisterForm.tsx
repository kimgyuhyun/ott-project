"use client";
import { sendVerificationCode, verifyCode, register } from "@/lib/api/auth";

type RegisterStep = 'email' | 'verification' | 'password';

interface StepByStepRegisterFormProps {
  onClose: () => void;
  onSuccess: () => void;
}

export default function StepByStepRegisterForm({ onClose, onSuccess }: StepByStepRegisterFormProps) {
  const [currentStep, setCurrentStep] = useState<RegisterStep>('email');
  const [email, setEmail] = useState('');
  const [verificationCode, setVerificationCode] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  // 1단계: 이메일 입력 및 인증코드 발송
  const handleSendVerificationCode = async () => {
    if (!email) {
      setError('이메일을 입력해주세요.');
      return;
    }

    setIsLoading(true);
    setError('');
    
    try {
      await sendVerificationCode(email);
      setSuccessMessage('인증코드가 이메일로 발송되었습니다. 이메일을 확인해주세요.');
      setCurrentStep('verification');
    } catch (err) {
      setError(err instanceof Error ? err.message : '인증코드 발송에 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  // 2단계: 인증코드 검증
  const handleVerifyCode = async () => {
    if (!verificationCode) {
      setError('인증코드를 입력해주세요.');
      return;
    }

    setIsLoading(true);
    setError('');
    
    try {
      await verifyCode(email, verificationCode);
      setSuccessMessage('이메일 인증이 완료되었습니다!');
      setCurrentStep('password');
    } catch (err) {
      setError(err instanceof Error ? err.message : '인증코드가 올바르지 않습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  // 3단계: 비밀번호 및 닉네임 입력 후 회원가입
  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!password || !name) {
      setError('모든 필드를 입력해주세요.');
      return;
    }

    if (password.length < 6) {
      setError('비밀번호는 최소 6자 이상이어야 합니다.');
      return;
    }

    if (name.length < 2 || name.length > 20) {
      setError('이름은 2자 이상 20자 이하여야 합니다.');
      return;
    }

    setIsLoading(true);
    setError('');
    
    try {
      await register(email, password, name);
      onSuccess();
    } catch (err) {
      setError(err instanceof Error ? err.message : '회원가입에 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  const resetForm = () => {
    setCurrentStep('email');
    setEmail('');
    setVerificationCode('');
    setPassword('');
    setName('');
    setError('');
    setSuccessMessage('');
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className="bg-white rounded-lg p-8 max-w-md w-full mx-4">
        {/* 헤더 */}
        <div className="flex justify-between items-center mb-6">
          <h2 className="text-2xl font-bold text-gray-800">회원가입</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            ✕
          </button>
        </div>

        {/* 진행 단계 표시 */}
        <div className="flex justify-center mb-6">
          <div className="flex space-x-2">
            <div className={`w-3 h-3 rounded-full ${currentStep === 'email' ? 'bg-purple-600' : 'bg-gray-300'}`}></div>
            <div className={`w-3 h-3 rounded-full ${currentStep === 'verification' ? 'bg-purple-600' : 'bg-gray-300'}`}></div>
            <div className={`w-3 h-3 rounded-full ${currentStep === 'password' ? 'bg-purple-600' : 'bg-gray-300'}`}></div>
          </div>
        </div>

        {/* 1단계: 이메일 입력 */}
        {currentStep === 'email' && (
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                이메일
              </label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-purple-500"
                placeholder="example@email.com"
                required
              />
            </div>
            
            <button
              onClick={handleSendVerificationCode}
              disabled={isLoading || !email}
              className={`w-full py-3 px-4 rounded-md font-medium transition-colors ${
                isLoading || !email
                  ? 'bg-gray-300 cursor-not-allowed'
                  : 'bg-purple-600 hover:bg-purple-700 text-white'
              }`}
            >
              {isLoading ? '발송 중...' : '인증코드 발송'}
            </button>
          </div>
        )}

        {/* 2단계: 인증코드 입력 */}
        {currentStep === 'verification' && (
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                인증코드
              </label>
              <input
                type="text"
                value={verificationCode}
                onChange={(e) => setVerificationCode(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-purple-500"
                placeholder="이메일로 받은 6자리 코드를 입력하세요"
                maxLength={6}
                required
              />
            </div>
            
            <div className="flex space-x-2">
              <button
                onClick={() => setCurrentStep('email')}
                className="flex-1 py-3 px-4 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50"
              >
                이전
              </button>
              <button
                onClick={handleVerifyCode}
                disabled={isLoading || !verificationCode}
                className={`flex-1 py-3 px-4 rounded-md font-medium transition-colors ${
                  isLoading || !verificationCode
                    ? 'bg-gray-300 cursor-not-allowed'
                    : 'bg-purple-600 hover:bg-purple-700 text-white'
                }`}
              >
                {isLoading ? '검증 중...' : '인증코드 확인'}
              </button>
            </div>
          </div>
        )}

        {/* 3단계: 비밀번호 및 닉네임 입력 */}
        {currentStep === 'password' && (
          <form onSubmit={handleRegister} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                비밀번호
              </label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-purple-500"
                placeholder="최소 6자 이상"
                minLength={6}
                required
              />
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                닉네임
              </label>
              <input
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-purple-500"
                placeholder="2-20자"
                minLength={2}
                maxLength={20}
                required
              />
            </div>

            <div className="flex space-x-2">
              <button
                type="button"
                onClick={() => setCurrentStep('verification')}
                className="flex-1 py-3 px-4 border border-gray-300 text-gray-700 rounded-md hover:bg-gray-50"
              >
                이전
              </button>
              <button
                type="submit"
                disabled={isLoading || !password || !name}
                className={`flex-1 py-3 px-4 rounded-md font-medium transition-colors ${
                  isLoading || !password || !name
                    ? 'bg-gray-300 cursor-not-allowed'
                    : 'bg-purple-600 hover:bg-purple-700 text-white'
                }`}
              >
                {isLoading ? '가입 중...' : '회원가입 완료'}
              </button>
            </div>
          </form>
        )}

        {/* 에러 메시지 */}
        {error && (
          <div className="mt-4 text-red-500 text-sm text-center">{error}</div>
        )}

        {/* 성공 메시지 */}
        {successMessage && (
          <div className="mt-4 text-green-600 text-sm text-center">{successMessage}</div>
        )}

        {/* 하단 링크 */}
        <div className="mt-6 text-center">
          <button
            onClick={resetForm}
            className="text-purple-600 hover:text-purple-700 text-sm"
          >
            처음부터 다시 시작
          </button>
        </div>
      </div>
    </div>
  );
}
