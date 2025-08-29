import styles from "./PosterWall.module.css";

export default function PosterWall() {
  return (
    <div
      aria-hidden
      className={styles.posterWallContainer}
    >
      {Array.from({ length: 24 }).map((_, i) => (
        <div key={i} className={styles.posterItem} />
      ))}
      <div className={styles.posterWallOverlay} />
    </div>
  );
}


