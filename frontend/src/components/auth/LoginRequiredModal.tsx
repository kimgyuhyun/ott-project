import { useRouter } from 'next/navigation';
import styles from './LoginRequiredModal.module.css';

interface LoginRequiredModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function LoginRequiredModal({ isOpen, onClose }: LoginRequiredModalProps) {
  const router = useRouter();

  if (!isOpen) return null;

  const handleLogin = () => {
    router.push('/login');
    onClose();
  };

  return (
    <div className={styles.overlay}>
      <div className={styles.modal}>
        <div className={styles.content}>
          <h2 className={styles.title}>작품 감상을 위해 로그인이 필요해요</h2>
          <p className={styles.description}>
            로그인하시면 모든 에피소드를 시청할 수 있습니다.
          </p>
          <div className={styles.buttons}>
            <button onClick={handleLogin} className={styles.loginButton}>
              로그인
            </button>
            <button onClick={onClose} className={styles.cancelButton}>
              취소
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
