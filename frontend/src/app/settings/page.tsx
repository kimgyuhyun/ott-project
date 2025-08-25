"use client";
import { useState, useEffect } from "react";
import Header from "@/components/layout/Header";
import { getUserSettings, updateUserSettings, changePassword, changeEmail } from "@/lib/api/user";

/**
 * 설정 페이지
 * 사용자 설정, 비밀번호 변경, 이메일 변경 등
 */
export default function SettingsPage() {
  const [userSettings, setUserSettings] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  
  // 폼 상태
  const [settingsForm, setSettingsForm] = useState({
    language: 'ko',
    subtitleLanguage: 'ko',
    autoPlay: true,
    autoPlayNext: true,
    quality: 'HD',
    notifications: true
  });
  
  const [passwordForm, setPasswordForm] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  });
  
  const [emailForm, setEmailForm] = useState({
    currentEmail: '',
    newEmail: '',
    password: ''
  });

  // 사용자 설정 로드
  useEffect(() => {
    const loadUserSettings = async () => {
      try {
        setIsLoading(true);
        setError(null);
        
        const settingsData = await getUserSettings();
        const settings = (settingsData as any) || {};
        
        setUserSettings(settings);
        setSettingsForm({
          language: settings.language || 'ko',
          subtitleLanguage: settings.subtitleLanguage || 'ko',
          autoPlay: settings.autoPlay !== false,
          autoPlayNext: settings.autoPlayNext !== false,
          quality: settings.quality || 'HD',
          notifications: settings.notifications !== false
        });
        
      } catch (err) {
        console.error('사용자 설정 로드 실패:', err);
        setError('사용자 설정을 불러오는데 실패했습니다.');
      } finally {
        setIsLoading(false);
      }
    };

    loadUserSettings();
  }, []);

  // 설정 저장
  const handleSaveSettings = async () => {
    try {
      setIsSaving(true);
      await updateUserSettings(settingsForm);
      alert('설정이 저장되었습니다.');
    } catch (err) {
      console.error('설정 저장 실패:', err);
      alert('설정 저장에 실패했습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  // 비밀번호 변경
  const handleChangePassword = async () => {
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      alert('새 비밀번호가 일치하지 않습니다.');
      return;
    }
    
    try {
      setIsSaving(true);
      await changePassword({
        currentPassword: passwordForm.currentPassword,
        newPassword: passwordForm.newPassword
      });
      
      alert('비밀번호가 변경되었습니다.');
      setPasswordForm({
        currentPassword: '',
        newPassword: '',
        confirmPassword: ''
      });
    } catch (err) {
      console.error('비밀번호 변경 실패:', err);
      alert('비밀번호 변경에 실패했습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  // 이메일 변경
  const handleChangeEmail = async () => {
    try {
      setIsSaving(true);
      await changeEmail({
        currentEmail: emailForm.currentEmail,
        newEmail: emailForm.newEmail,
        password: emailForm.password
      });
      
      alert('이메일 변경 요청이 전송되었습니다. 새 이메일을 확인해주세요.');
      setEmailForm({
        currentEmail: '',
        newEmail: '',
        password: ''
      });
    } catch (err) {
      console.error('이메일 변경 실패:', err);
      alert('이메일 변경에 실패했습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-xl text-gray-600">로딩 중...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-xl text-red-600">{error}</div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <Header />
      
      <main className="pt-16">
        <div className="max-w-4xl mx-auto px-6 py-8">
          {/* 페이지 제목 */}
          <h1 className="text-3xl font-bold text-gray-800 mb-8">설정</h1>

          {/* 재생 설정 */}
          <div className="bg-white rounded-lg p-6 mb-8 shadow-sm border border-gray-200">
            <h2 className="text-xl font-semibold text-gray-800 mb-6">재생 설정</h2>
            
            <div className="space-y-6">
              {/* 언어 설정 */}
              <div className="grid md:grid-cols-2 gap-6">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    오디오 언어
                  </label>
                  <select
                    value={settingsForm.language}
                    onChange={(e) => setSettingsForm({...settingsForm, language: e.target.value})}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                  >
                    <option value="ko">한국어</option>
                    <option value="ja">일본어</option>
                    <option value="en">영어</option>
                  </select>
                </div>
                
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-2">
                    자막 언어
                  </label>
                  <select
                    value={settingsForm.subtitleLanguage}
                    onChange={(e) => setSettingsForm({...settingsForm, subtitleLanguage: e.target.value})}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                  >
                    <option value="ko">한국어</option>
                    <option value="ja">일본어</option>
                    <option value="en">영어</option>
                  </select>
                </div>
              </div>

              {/* 화질 설정 */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  기본 화질
                </label>
                <select
                  value={settingsForm.quality}
                  onChange={(e) => setSettingsForm({...settingsForm, quality: e.target.value})}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                >
                  <option value="SD">SD (480p)</option>
                  <option value="HD">HD (720p)</option>
                  <option value="FHD">FHD (1080p)</option>
                </select>
              </div>

              {/* 체크박스 설정들 */}
              <div className="space-y-4">
                <label className="flex items-center space-x-3 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={settingsForm.autoPlay}
                    onChange={(e) => setSettingsForm({...settingsForm, autoPlay: e.target.checked})}
                    className="w-4 h-4 text-purple-600 bg-white border-gray-300 rounded focus:ring-purple-500"
                  />
                  <span className="text-gray-700">자동 재생</span>
                </label>
                
                <label className="flex items-center space-x-3 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={settingsForm.autoPlayNext}
                    onChange={(e) => setSettingsForm({...settingsForm, autoPlayNext: e.target.checked})}
                    className="w-4 h-4 text-purple-600 bg-white border-gray-300 rounded focus:ring-purple-500"
                  />
                  <span className="text-gray-700">다음 에피소드 자동 재생</span>
                </label>
                
                <label className="flex items-center space-x-3 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={settingsForm.notifications}
                    onChange={(e) => setSettingsForm({...settingsForm, notifications: e.target.checked})}
                    className="w-4 h-4 text-purple-600 bg-white border-gray-300 rounded focus:ring-purple-500"
                  />
                  <span className="text-gray-700">알림 받기</span>
                </label>
              </div>

              {/* 저장 버튼 */}
              <button
                onClick={handleSaveSettings}
                disabled={isSaving}
                className="px-6 py-3 bg-purple-600 hover:bg-purple-700 text-white font-semibold rounded-lg transition-colors disabled:bg-gray-400"
              >
                {isSaving ? '저장 중...' : '설정 저장'}
              </button>
            </div>
          </div>

          {/* 비밀번호 변경 */}
          <div className="bg-white rounded-lg p-6 mb-8 shadow-sm border border-gray-200">
            <h2 className="text-xl font-semibold text-gray-800 mb-6">비밀번호 변경</h2>
            
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  현재 비밀번호
                </label>
                <input
                  type="password"
                  value={passwordForm.currentPassword}
                  onChange={(e) => setPasswordForm({...passwordForm, currentPassword: e.target.value})}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                  placeholder="현재 비밀번호를 입력하세요"
                />
              </div>
              
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  새 비밀번호
                </label>
                <input
                  type="password"
                  value={passwordForm.newPassword}
                  onChange={(e) => setPasswordForm({...passwordForm, newPassword: e.target.value})}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                  placeholder="새 비밀번호를 입력하세요"
                />
              </div>
              
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  새 비밀번호 확인
                </label>
                <input
                  type="password"
                  value={passwordForm.confirmPassword}
                  onChange={(e) => setPasswordForm({...passwordForm, confirmPassword: e.target.value})}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                  placeholder="새 비밀번호를 다시 입력하세요"
                />
              </div>
              
              <button
                onClick={handleChangePassword}
                disabled={isSaving}
                className="px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-lg transition-colors disabled:bg-gray-400"
              >
                {isSaving ? '변경 중...' : '비밀번호 변경'}
              </button>
            </div>
          </div>

          {/* 이메일 변경 */}
          <div className="bg-white rounded-lg p-6 mb-8 shadow-sm border border-gray-200">
            <h2 className="text-xl font-semibold text-gray-800 mb-6">이메일 변경</h2>
            
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  현재 이메일
                </label>
                <input
                  type="email"
                  value={emailForm.currentEmail}
                  onChange={(e) => setEmailForm({...emailForm, currentEmail: e.target.value})}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                  placeholder="현재 이메일을 입력하세요"
                />
              </div>
              
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  새 이메일
                </label>
                <input
                  type="email"
                  value={emailForm.newEmail}
                  onChange={(e) => setEmailForm({...emailForm, newEmail: e.target.value})}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                  placeholder="새 이메일을 입력하세요"
                />
              </div>
              
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  비밀번호 확인
                </label>
                <input
                  type="password"
                  value={emailForm.password}
                  onChange={(e) => setEmailForm({...emailForm, password: e.target.value})}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-purple-500"
                  placeholder="비밀번호를 입력하세요"
                />
              </div>
              
              <button
                onClick={handleChangeEmail}
                disabled={isSaving}
                className="px-6 py-3 bg-green-600 hover:bg-green-700 text-white font-semibold rounded-lg transition-colors disabled:bg-gray-400"
              >
                {isSaving ? '변경 중...' : '이메일 변경'}
              </button>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
