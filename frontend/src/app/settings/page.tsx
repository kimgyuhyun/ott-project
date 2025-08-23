"use client";
import { useState } from "react";
import Header from "@/components/layout/Header";

/**
 * 설정 페이지
 * 계정 정보, 알림 설정, 테마 선택 포함
 */
export default function SettingsPage() {
  // 알림 설정 상태
  const [notifications, setNotifications] = useState({
    updates: false,
    community: false,
    events: false,
    emailEvents: true
  });

  // 테마 설정 상태
  const [selectedTheme, setSelectedTheme] = useState<'light' | 'dark' | 'auto'>('light');

  // 알림 설정 변경 핸들러
  const handleNotificationChange = (key: keyof typeof notifications) => {
    setNotifications(prev => ({
      ...prev,
      [key]: !prev[key]
    }));
  };

  // 테마 변경 핸들러
  const handleThemeChange = (theme: 'light' | 'dark' | 'auto') => {
    setSelectedTheme(theme);
    // TODO: 실제 테마 변경 로직 구현
    console.log('테마 변경:', theme);
  };

  return (
    <div className="min-h-screen bg-white">
      <Header />
      
      <main className="pt-16">
        <div className="max-w-4xl mx-auto px-6 py-8">
          {/* 페이지 제목 */}
          <h1 className="text-3xl font-bold text-gray-900 mb-8">설정</h1>

          {/* 계정 섹션 */}
          <div className="bg-white border border-gray-200 rounded-lg p-6 mb-8">
            <h2 className="text-xl font-semibold text-gray-900 mb-6">계정</h2>
            
            <div className="space-y-6">
              {/* 이메일 */}
              <div className="flex items-center justify-between py-4 border-b border-gray-100">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    이메일
                  </label>
                  <p className="text-gray-900">kgh9806@naver.com</p>
                </div>
                <button className="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg transition-colors text-sm">
                  이메일 변경
                </button>
              </div>

              {/* 비밀번호 */}
              <div className="flex items-center justify-between py-4 border-b border-gray-100">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    비밀번호
                  </label>
                  <p className="text-gray-900">*********</p>
                </div>
                <button className="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg transition-colors text-sm">
                  비밀번호 변경
                </button>
              </div>

              {/* 로그아웃 */}
              <div className="flex items-center justify-between py-4">
                <div>
                  <p className="text-gray-700">모든 기기에서 로그아웃</p>
                </div>
                <button className="px-4 py-2 bg-red-600 hover:bg-red-700 text-white rounded-lg transition-colors text-sm">
                  로그아웃
                </button>
              </div>
            </div>
          </div>

          {/* 알림 섹션 */}
          <div className="bg-white border border-gray-200 rounded-lg p-6 mb-8">
            <h2 className="text-xl font-semibold text-gray-900 mb-6">알림</h2>
            
            <div className="space-y-6">
              {/* 알림 수신 */}
              <div>
                <h3 className="text-lg font-medium text-gray-900 mb-4">알림 수신</h3>
                <div className="space-y-4">
                  <label className="flex items-center space-x-3 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={notifications.updates}
                      onChange={() => handleNotificationChange('updates')}
                      className="w-5 h-5 text-purple-600 bg-gray-100 border-gray-300 rounded focus:ring-purple-500 focus:ring-2"
                    />
                    <span className="text-gray-700">관심있는 작품의 업데이트 소식</span>
                  </label>
                  
                  <label className="flex items-center space-x-3 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={notifications.community}
                      onChange={() => handleNotificationChange('community')}
                      className="w-5 h-5 text-purple-600 bg-gray-100 border-gray-300 rounded focus:ring-purple-500 focus:ring-2"
                    />
                    <span className="text-gray-700">커뮤니티 활동 소식</span>
                  </label>
                  
                  <label className="flex items-center space-x-3 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={notifications.events}
                      onChange={() => handleNotificationChange('events')}
                      className="w-5 h-5 text-purple-600 bg-gray-100 border-gray-300 rounded focus:ring-purple-500 focus:ring-2"
                    />
                    <span className="text-gray-700">이벤트 및 혜택 정보 소식</span>
                  </label>
                </div>
              </div>

              {/* 이메일 알림 */}
              <div>
                <h3 className="text-lg font-medium text-gray-900 mb-4">이메일 알림</h3>
                <div className="space-y-4">
                  <label className="flex items-center space-x-3 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={notifications.emailEvents}
                      onChange={() => handleNotificationChange('emailEvents')}
                      className="w-5 h-5 text-purple-600 bg-gray-100 border-gray-300 rounded focus:ring-purple-500 focus:ring-2"
                    />
                    <span className="text-gray-700">이벤트 및 혜택 정보 소식</span>
                  </label>
                </div>
              </div>
            </div>
          </div>

          {/* 테마 섹션 */}
          <div className="bg-white border border-gray-200 rounded-lg p-6">
            <h2 className="text-xl font-semibold text-gray-900 mb-6">테마</h2>
            
            <div className="flex space-x-4">
              {/* 밝은 테마 */}
              <button
                onClick={() => handleThemeChange('light')}
                className={`flex flex-col items-center space-y-2 p-4 rounded-lg border-2 transition-all ${
                  selectedTheme === 'light'
                    ? 'border-purple-500 bg-purple-50'
                    : 'border-gray-200 bg-gray-50 hover:border-gray-300'
                }`}
              >
                <div className="w-12 h-12 bg-yellow-400 rounded-full flex items-center justify-center">
                  <svg className="w-6 h-6 text-white" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M10 2a1 1 0 011 1v1a1 1 0 11-2 0V3a1 1 0 011-1zm4 8a4 4 0 11-8 0 4 4 0 018 0zm-.464 4.95l.707.707a1 1 0 001.414-1.414l-.707-.707a1 1 0 00-1.414 1.414zm2.12-10.607a1 1 0 010 1.414l-.706.707a1 1 0 11-1.414-1.414l.707-.707a1 1 0 011.414 0zM17 11a1 1 0 100-2h-1a1 1 0 100 2h1zm-7 4a1 1 0 011 1v1a1 1 0 11-2 0v-1a1 1 0 011-1zM5.05 6.464A1 1 0 106.465 5.05l-.708-.707a1 1 0 00-1.414 1.414l.707.707zm1.414 8.486l-.707.707a1 1 0 01-1.414-1.414l.707-.707a1 1 0 011.414 1.414zM4 11a1 1 0 100-2H3a1 1 0 000 2h1z" clipRule="evenodd" />
                  </svg>
                </div>
                <span className="text-sm font-medium text-gray-700">밝은</span>
              </button>

              {/* 어두운 테마 */}
              <button
                onClick={() => handleThemeChange('dark')}
                className={`flex flex-col items-center space-y-2 p-4 rounded-lg border-2 transition-all ${
                  selectedTheme === 'dark'
                    ? 'border-purple-500 bg-purple-50'
                    : 'border-gray-200 bg-gray-50 hover:border-gray-300'
                }`}
              >
                <div className="w-12 h-12 bg-gray-800 rounded-full flex items-center justify-center">
                  <svg className="w-6 h-6 text-white" fill="currentColor" viewBox="0 0 20 20">
                    <path d="M17.293 13.293A8 8 0 016.707 2.707a8.001 8.001 0 1010.586 10.586z" />
                  </svg>
                </div>
                <span className="text-sm font-medium text-gray-700">어두운</span>
              </button>

              {/* 자동 테마 */}
              <button
                onClick={() => handleThemeChange('auto')}
                className={`flex flex-col items-center space-y-2 p-4 rounded-lg border-2 transition-all ${
                  selectedTheme === 'auto'
                    ? 'border-purple-500 bg-purple-50'
                    : 'border-gray-200 bg-gray-50 hover:border-gray-300'
                }`}
              >
                <div className="w-12 h-12 bg-blue-500 rounded-full flex items-center justify-center">
                  <svg className="w-6 h-6 text-white" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm1-12a1 1 0 10-2 0v4a1 1 0 00.293.707l2.828 2.829a1 1 0 101.415-1.415L11 9.586V6z" clipRule="evenodd" />
                  </svg>
                </div>
                <span className="text-sm font-medium text-gray-700">자동</span>
              </button>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
