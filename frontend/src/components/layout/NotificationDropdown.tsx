"use client";
import { useState, useEffect, useRef } from "react";
import { getNotifications, getUnreadNotificationCount, markNotificationAsRead, markAllNotificationsAsRead } from "@/lib/api/notification";
import styles from "./NotificationDropdown.module.css";

interface NotificationData {
  animeId?: number;
  episodeId?: number;
  animeTitle?: string;
  episodeNumber?: number;
  activityType?: string;
  contentType?: string;
  contentId?: number;
  commentContent?: string;
}

interface Notification {
  id: number;
  type: string;
  title: string;
  content: string;
  data: NotificationData;
  isRead: boolean;
  createdAt: string;
}

interface NotificationDropdownProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function NotificationDropdown({ isOpen, onClose }: NotificationDropdownProps) {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // 드롭다운 외부 클릭 시 닫기
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        onClose();
      }
    }

    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
      return () => document.removeEventListener('mousedown', handleClickOutside);
    }
  }, [isOpen, onClose]);

  // 알림 데이터 로드
  const loadNotifications = async (pageNum: number = 0, append: boolean = false) => {
    try {
      setIsLoading(true);
      const response = await getNotifications(pageNum, 20);
      const newNotifications = (response as any).content || [];
      
      if (append) {
        setNotifications(prev => [...prev, ...newNotifications]);
      } else {
        setNotifications(newNotifications);
      }
      
      setHasMore(!(response as any).last);
      setPage(pageNum);
    } catch (error) {
      console.error('알림 로드 실패:', error);
    } finally {
      setIsLoading(false);
    }
  };

  // 읽지 않은 알림 개수 로드
  const loadUnreadCount = async () => {
    try {
      const count = await getUnreadNotificationCount();
      setUnreadCount(count as number);
    } catch (error) {
      console.error('읽지 않은 알림 개수 로드 실패:', error);
    }
  };

  // 드롭다운 열릴 때 데이터 로드
  useEffect(() => {
    if (isOpen) {
      loadNotifications(0, false);
      loadUnreadCount();
    }
  }, [isOpen]);

  // 알림 읽음 처리
  const handleMarkAsRead = async (notificationId: number) => {
    try {
      await markNotificationAsRead(notificationId);
      setNotifications(prev => 
        prev.map(notif => 
          notif.id === notificationId ? { ...notif, isRead: true } : notif
        )
      );
      setUnreadCount(prev => Math.max(0, prev - 1));
    } catch (error) {
      console.error('알림 읽음 처리 실패:', error);
    }
  };

  // 전체 읽음 처리
  const handleMarkAllAsRead = async () => {
    try {
      await markAllNotificationsAsRead();
      setNotifications(prev => 
        prev.map(notif => ({ ...notif, isRead: true }))
      );
      setUnreadCount(0);
    } catch (error) {
      console.error('전체 알림 읽음 처리 실패:', error);
    }
  };

  // 더보기 로드
  const handleLoadMore = () => {
    if (!isLoading && hasMore) {
      loadNotifications(page + 1, true);
    }
  };

  // 알림 클릭 시 처리
  const handleNotificationClick = async (notification: Notification) => {
    if (!notification.isRead) {
      await handleMarkAsRead(notification.id);
    }
    
    // 알림 타입에 따른 네비게이션
    if (notification.type === 'EPISODE_UPDATE' && notification.data.animeId) {
      window.location.href = `/player?animeId=${notification.data.animeId}&episodeId=${notification.data.episodeId}`;
    } else if (notification.type === 'COMMENT_ACTIVITY' && notification.data.animeId) {
      if (notification.data.contentType === 'REVIEW_COMMENT') {
        window.location.href = `/anime/${notification.data.animeId}#reviews`;
      } else if (notification.data.contentType === 'EPISODE_COMMENT') {
        window.location.href = `/player?animeId=${notification.data.animeId}&episodeId=${notification.data.episodeId}#comments`;
      }
    }
    
    onClose();
  };

  // 시간 포맷팅
  const formatTime = (createdAt: string) => {
    const now = new Date();
    const created = new Date(createdAt);
    const diffInMinutes = Math.floor((now.getTime() - created.getTime()) / (1000 * 60));
    
    if (diffInMinutes < 1) return '방금 전';
    if (diffInMinutes < 60) return `${diffInMinutes}분 전`;
    if (diffInMinutes < 1440) return `${Math.floor(diffInMinutes / 60)}시간 전`;
    return `${Math.floor(diffInMinutes / 1440)}일 전`;
  };

  if (!isOpen) return null;

  return (
    <div className={styles.dropdown} ref={dropdownRef}>
      <div className={styles.header}>
        <h3 className={styles.title}>알림</h3>
        {unreadCount > 0 && (
          <button 
            className={styles.markAllReadButton}
            onClick={handleMarkAllAsRead}
          >
            모두 읽음
          </button>
        )}
      </div>
      
      <div className={styles.notificationList}>
        {notifications.length === 0 ? (
          <div className={styles.emptyState}>
            <p>알림이 없습니다.</p>
          </div>
        ) : (
          notifications.map((notification) => (
            <div
              key={notification.id}
              className={`${styles.notificationItem} ${!notification.isRead ? styles.unread : ''}`}
              onClick={() => handleNotificationClick(notification)}
            >
              <div className={styles.notificationContent}>
                <div className={styles.notificationTitle}>
                  {notification.title}
                  {!notification.isRead && <div className={styles.unreadDot} />}
                </div>
                <div className={styles.notificationText}>
                  {notification.content}
                </div>
                {notification.data.commentContent && (
                  <div className={styles.commentPreview}>
                    "{notification.data.commentContent}"
                  </div>
                )}
                <div className={styles.notificationTime}>
                  {formatTime(notification.createdAt)}
                </div>
              </div>
            </div>
          ))
        )}
        
        {hasMore && (
          <button
            className={styles.loadMoreButton}
            onClick={handleLoadMore}
            disabled={isLoading}
          >
            {isLoading ? '로딩 중...' : '더보기'}
          </button>
        )}
      </div>
    </div>
  );
}
