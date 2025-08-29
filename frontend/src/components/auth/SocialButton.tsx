"use client";
import Image from "next/image";
import styles from "./SocialButton.module.css";

type Provider = "google" | "naver" | "kakao" | "email";

type Props = {
  provider: Provider;
  label?: string;
  href?: string;
  onClick?: () => void;
};

const COLORS: Record<Provider, string> = {
  google: "#ffffff",
  naver: "#03c75a",
  kakao: "#fee500",
  email: "#6b6bff",
};

export default function SocialButton({ provider, label, href, onClick }: Props) {
  const content = (
    <div
      className={styles.socialButtonContainer}
      style={{ backgroundColor: provider === "google" ? "#ffffff" : COLORS[provider] }}
    >
      {provider === "email" ? (
        <span className={styles.emailIcon} />
      ) : (
        <Image
          alt={`${provider} icon`}
          src={`/icons/${provider}.svg`}
          width={20}
          height={20}
        />
      )}
      <span className={styles.buttonText}>
        {label}
      </span>
    </div>
  );

  if (href) {
    return (
      <a href={href} onClick={onClick} className={styles.buttonLink}>
        {content}
      </a>
    );
  }
  return (
    <button type="button" onClick={onClick} className={styles.buttonElement}>
      {content}
    </button>
  );
}


