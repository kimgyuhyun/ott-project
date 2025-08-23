"use client";
import Image from "next/image";

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
      className="flex h-12 w-full items-center justify-center gap-3 rounded-xl px-4 font-medium"
      style={{ backgroundColor: provider === "google" ? "#ffffff" : COLORS[provider] }}
    >
      {provider === "email" ? (
        <span className="inline-block h-5 w-5 rounded bg-white/20" />
      ) : (
        <Image
          alt={`${provider} icon`}
          src={`/icons/${provider}.svg`}
          width={20}
          height={20}
        />
      )}
      <span className={provider === "google" ? "text-black" : "text-black"}>
        {label}
      </span>
    </div>
  );

  if (href) {
    return (
      <a href={href} onClick={onClick} className="block">
        {content}
      </a>
    );
  }
  return (
    <button type="button" onClick={onClick} className="block w-full">
      {content}
    </button>
  );
}


