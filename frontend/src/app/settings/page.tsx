"use client";
import { useState, useEffect } from "react";
import Header from "@/components/layout/Header";
import { getUserSettings, updateUserSettings, changePassword, changeEmail } from "@/lib/api/user";
import { useAuth } from "@/lib/AuthContext";
import styles from "./settings.module.css";

/**
 * 설정 페이지
 * 사용자 설정, 비밀번호 변경, 이메일 변경 등
 */
export default function SettingsPage() {
  const { logout } = useAuth();
  const [userSettings, setUserSettings] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  // 다크모드 적용
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', 'dark');
  }, []);
  
  // 폼 상태
  const [settingsForm, setSettingsForm] = useState({
    notifications: {
      workUpdates: false,
      communityActivity: false,
      eventBenefits: false
    },
    emailNotifications: {
      eventBenefits: true
    },
    theme: 'light' // light, dark, system
  });
  
  const [passwordForm, setPasswordForm] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  });
  
  const [userInfo, setUserInfo] = useState({
    email: 'kgh9806@naver.com',
    name: '김규현'
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
          notifications: {
            workUpdates: settings.notifications?.workUpdates || false,
            communityActivity: settings.notifications?.communityActivity || false,
            eventBenefits: settings.notifications?.eventBenefits || false
          },
          emailNotifications: {
            eventBenefits: settings.emailNotifications?.eventBenefits !== false
          },
          theme: settings.theme || 'light'
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

  // 로그아웃
  const handleLogout = async () => {
    await logout();
  };

  // 테마 변경
  const handleThemeChange = (theme: string) => {
    setSettingsForm({...settingsForm, theme});
    // 테마 변경 로직 구현
    console.log('테마 변경:', theme);
  };

  if (isLoading) {
    return (
      <div className={styles.settingsPageContainer}>
        <Header />
        <div className={styles.settingsLoadingContainer}>
          <div className={styles.settingsLoadingText}>로딩 중...</div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className={styles.settingsPageContainer}>
        <Header />
        <div className={styles.settingsErrorContainer}>
          <div className={styles.settingsErrorText}>{error}</div>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.settingsPageContainer}>
      <Header />
      
      <main className={styles.settingsMain}>
        <div className={styles.settingsContent}>
          {/* 페이지 제목 */}
          <h1 className={styles.settingsPageTitle}>설정</h1>

          {/* 계정 섹션 */}
          <div className={styles.settingsSection}>
            <h2 className={styles.settingsSectionTitle}>계정</h2>
            
            <div className={styles.accountItem}>
              <div className={styles.accountInfo}>
                <span className={styles.accountLabel}>이메일</span>
                <span className={styles.accountValue}>{userInfo.email}</span>
              </div>
            </div>
            
            <div className={styles.accountItem}>
              <div className={styles.accountInfo}>
                <span className={styles.accountLabel}>비밀번호</span>
                <span className={styles.accountValue}>*********</span>
              </div>
              <button
                onClick={() => {/* 비밀번호 변경 모달 열기 */}}
                className={styles.accountButton}
              >
                비밀번호 변경
              </button>
            </div>
            
            <div className={styles.accountItem}>
              <div className={styles.accountInfo}>
                <span className={styles.accountLabel}>로그아웃</span>
              </div>
              <div className={styles.accountButtons}>
                <button
                  onClick={handleLogout}
                  className={styles.logoutButton}
                >
                  로그아웃
                </button>
                <button
                  onClick={handleLogout}
                  className={styles.logoutAllButton}
                >
                  모든 기기에서 로그아웃
                </button>
              </div>
            </div>
          </div>

          {/* 알림 섹션 */}
          <div className={styles.settingsSection}>
            <h2 className={styles.settingsSectionTitle}>알림</h2>
            
            <div className={styles.notificationGroup}>
              <h3 className={styles.notificationGroupTitle}>알림 수신</h3>
              <div className={styles.checkboxGroup}>
                <label className={styles.checkboxItem}>
                  <input
                    type="checkbox"
                    checked={settingsForm.notifications.workUpdates}
                    onChange={(e) => setSettingsForm({
                      ...settingsForm,
                      notifications: {...settingsForm.notifications, workUpdates: e.target.checked}
                    })}
                    className={styles.checkbox}
                  />
                  <span className={styles.checkboxLabel}>관심있는 작품의 업데이트 소식</span>
                </label>
                
                <label className={styles.checkboxItem}>
                  <input
                    type="checkbox"
                    checked={settingsForm.notifications.communityActivity}
                    onChange={(e) => setSettingsForm({
                      ...settingsForm,
                      notifications: {...settingsForm.notifications, communityActivity: e.target.checked}
                    })}
                    className={styles.checkbox}
                  />
                  <span className={styles.checkboxLabel}>커뮤니티 활동 소식</span>
                </label>
                
                <label className={styles.checkboxItem}>
                  <input
                    type="checkbox"
                    checked={settingsForm.notifications.eventBenefits}
                    onChange={(e) => setSettingsForm({
                      ...settingsForm,
                      notifications: {...settingsForm.notifications, eventBenefits: e.target.checked}
                    })}
                    className={styles.checkbox}
                  />
                  <span className={styles.checkboxLabel}>이벤트 및 혜택 정보 소식</span>
                </label>
              </div>
            </div>
            
            <div className={styles.notificationGroup}>
              <h3 className={styles.notificationGroupTitle}>이메일 알림</h3>
              <div className={styles.checkboxGroup}>
                <label className={styles.checkboxItem}>
                  <input
                    type="checkbox"
                    checked={settingsForm.emailNotifications.eventBenefits}
                    onChange={(e) => setSettingsForm({
                      ...settingsForm,
                      emailNotifications: {...settingsForm.emailNotifications, eventBenefits: e.target.checked}
                    })}
                    className={styles.checkbox}
                  />
                  <span className={styles.checkboxLabel}>이벤트 및 혜택 정보 소식</span>
                </label>
              </div>
            </div>
          </div>

          {/* 테마 섹션 */}
          <div className={styles.settingsSection}>
            <h2 className={styles.settingsSectionTitle}>테마</h2>
            
            <div className={styles.themeGroup}>
              <button
                onClick={() => handleThemeChange('light')}
                className={`${styles.themeButton} ${settingsForm.theme === 'light' ? styles.themeButtonActive : ''}`}
              >
                <div className={styles.themeIcon}>☀️</div>
                <span className={styles.themeLabel}>라이트</span>
              </button>
              
              <button
                onClick={() => handleThemeChange('dark')}
                className={`${styles.themeButton} ${settingsForm.theme === 'dark' ? styles.themeButtonActive : ''}`}
              >
                <div className={styles.themeIcon}>🌙</div>
                <span className={styles.themeLabel}>다크</span>
              </button>
              
              <button
                onClick={() => handleThemeChange('system')}
                className={`${styles.themeButton} ${settingsForm.theme === 'system' ? styles.themeButtonActive : ''}`}
              >
                <div className={styles.themeIcon}>⚙️</div>
                <span className={styles.themeLabel}>시스템</span>
              </button>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
