"use client";
import { useState } from "react";
import Modal from "@/components/ui/Modal";
import PosterWall from "@/components/auth/PosterWall";
import SocialButton from "@/components/auth/SocialButton";
import { oauthUrl } from "@/lib/auth";

export default function LoginPage() {
  const [open, setOpen] = useState(true);

  return (
    <main className="relative min-h-dvh bg-black text-white">
      <PosterWall />

      <Modal open={open} onClose={() => setOpen(false)}>
        <div className="flex flex-col items-center gap-6">
          <div className="text-4xl font-extrabold tracking-widest">LAFTEL</div>
          <div className="text-center text-sm leading-6 text-white/80">
            동시방영 신작부터 역대 인기작까지
            <br />한 곳에서 편-안하게!
          </div>

        
          <SocialButton provider="email" label="이메일로 시작" onClick={() => alert("이메일 회원가입 흐름 연결 예정")}/>

          <div className="text-xs text-white/60">또는</div>

          <div className="flex w-full items-center justify-center gap-4">
            <a href={oauthUrl("kakao")} aria-label="kakao" className="rounded-full bg-[#fee500] p-3">
              <img alt="kakao" src="/icons/kakao.svg" width={24} height={24} />
            </a>
            <a href={oauthUrl("google")} aria-label="google" className="rounded-full bg-white p-3">
              <img alt="google" src="/icons/google.svg" width={24} height={24} />
            </a>
            <a href={oauthUrl("naver")} aria-label="naver" className="rounded-full bg-[#03c75a] p-3">
              <img alt="naver" src="/icons/naver.svg" width={24} height={24} />
            </a>
          </div>

          <a href="#" className="text-[11px] text-white/50 underline">로그인의 어려움을 겪고 계신가요?</a>
        </div>
      </Modal>
    </main>
  );
}


