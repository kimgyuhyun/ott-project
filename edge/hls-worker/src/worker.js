/**
 * ott-hls-edge — Cloudflare Worker
 *
 * 큰 흐름
 * - R2 버킷(HLS) 앞단에서 secure_link 서명(e/st)을 검증하는 접근 게이트.
 * - 백엔드(PlaybackAuthService)는 master.m3u8 URL 하나만 서명해 발급한다.
 *   Worker 는 그 진입 요청을 검증한 뒤, 응답 플레이리스트의 하위 URI(자식
 *   재생목록·세그먼트)에 자기가 서명을 붙여 되쓴다(캐스케이드 서명).
 *   → 세그먼트마다 백엔드가 서명할 필요가 없고, 쿠키/경로깊이 규칙도 불필요.
 *
 * 서명 포맷 (백엔드 HlsSignedUrlUtil 과 동일해야 함)
 * - st = URL-safe-Base64(무패딩)( MD5( expires + uriPath + " " + secret ) )
 * - uriPath = 요청 경로(도메인 제거, 선행 슬래시 포함). 예: /sintel/master.m3u8
 * - e = 만료 epoch(초). 만료 지나면 403.
 *
 * 시크릿
 * - env.SECURE_LINK_SECRET (wrangler secret). 백엔드 .env 값과 동일.
 */

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const pathname = url.pathname; // 예: /sintel/master.m3u8

    if (request.method === "OPTIONS") return cors(new Response(null, { status: 204 }));
    if (request.method !== "GET" && request.method !== "HEAD") {
      return cors(new Response("method not allowed", { status: 405 }));
    }

    const secret = env.SECURE_LINK_SECRET;
    if (!secret) return new Response("SECURE_LINK_SECRET not configured", { status: 500 });

    const e = url.searchParams.get("e");
    const st = url.searchParams.get("st");
    if (!e || !st) return cors(new Response("missing token", { status: 403 }));

    const expires = Number.parseInt(e, 10);
    if (!Number.isFinite(expires) || expires * 1000 < Date.now()) {
      return cors(new Response("expired", { status: 403 }));
    }

    const expected = sign(expires, pathname, secret);
    if (!timingSafeEqual(expected, st)) {
      return cors(new Response("bad signature", { status: 403 }));
    }

    const key = pathname.replace(/^\/+/, ""); // 선행 슬래시 제거 → R2 key
    const object = await env.HLS_BUCKET.get(key);
    if (!object) return cors(new Response("not found", { status: 404 }));

    // 플레이리스트면 하위 URI 에 캐스케이드 서명을 붙여 되쓴다.
    if (key.endsWith(".m3u8")) {
      const body = await object.text();
      const rewritten = rewritePlaylist(body, pathname, expires, secret);
      return cors(
        new Response(rewritten, {
          headers: {
            "Content-Type": "application/vnd.apple.mpegurl",
            "Cache-Control": "no-store", // 서명이 만료되므로 캐시 금지
          },
        })
      );
    }

    // 세그먼트/기타 정적 리소스는 그대로 스트리밍.
    const headers = new Headers();
    object.writeHttpMetadata(headers);
    headers.set("Content-Type", contentTypeFor(key, headers.get("Content-Type")));
    headers.set("Cache-Control", "public, max-age=60");
    return cors(new Response(object.body, { headers }));
  },
};

// ── 플레이리스트 되쓰기 ───────────────────────────────────────────────

function rewritePlaylist(text, basePathname, expires, secret) {
  const baseDir = basePathname.slice(0, basePathname.lastIndexOf("/") + 1); // /sintel/
  return text
    .split("\n")
    .map((line) => {
      const trimmed = line.trim();
      if (trimmed === "") return line;
      if (trimmed.startsWith("#")) {
        // EXT-X-KEY / EXT-X-MEDIA 등 태그 내부 URI="..." 도 서명.
        if (trimmed.includes('URI="')) return signUriAttr(line, baseDir, expires, secret);
        return line;
      }
      // URI 라인(자식 재생목록 또는 세그먼트)
      const abs = resolvePath(baseDir, trimmed);
      if (abs === null) return line; // 외부 절대 URL 은 서명 불가 → 그대로
      return trimmed + appendToken(trimmed, abs, expires, secret);
    })
    .join("\n");
}

function signUriAttr(line, baseDir, expires, secret) {
  return line.replace(/URI="([^"]+)"/g, (m, ref) => {
    const abs = resolvePath(baseDir, ref);
    if (abs === null) return m;
    return `URI="${ref}${appendToken(ref, abs, expires, secret)}"`;
  });
}

function appendToken(ref, absPath, expires, secret) {
  const sep = ref.includes("?") ? "&" : "?";
  return `${sep}e=${expires}&st=${sign(expires, absPath, secret)}`;
}

// baseDir 기준 상대참조를 절대 경로(선행 슬래시)로 해석. 외부 절대 URL 이면 null.
function resolvePath(baseDir, ref) {
  if (/^https?:\/\//i.test(ref)) return null;
  const raw = ref.startsWith("/") ? ref : baseDir + ref;
  const parts = [];
  for (const seg of raw.split("/")) {
    if (seg === "" || seg === ".") continue;
    if (seg === "..") parts.pop();
    else parts.push(seg);
  }
  return "/" + parts.join("/");
}

// ── 서명 (백엔드와 동일 포맷) ─────────────────────────────────────────

function sign(expires, uriPath, secret) {
  const data = `${expires}${uriPath} ${secret}`;
  return base64url(md5bytes(utf8(data)));
}

function timingSafeEqual(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

function utf8(str) {
  return new TextEncoder().encode(str);
}

function base64url(bytes) {
  let bin = "";
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function contentTypeFor(key, existing) {
  if (key.endsWith(".ts")) return "video/mp2t";
  if (key.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
  if (key.endsWith(".m4s") || key.endsWith(".mp4")) return "video/mp4";
  if (key.endsWith(".vtt")) return "text/vtt";
  return existing || "application/octet-stream";
}

function cors(resp) {
  const h = new Headers(resp.headers);
  h.set("Access-Control-Allow-Origin", "*");
  h.set("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
  h.set("Access-Control-Allow-Headers", "Range");
  h.set("Access-Control-Expose-Headers", "Content-Length, Content-Range");
  return new Response(resp.body, { status: resp.status, headers: h });
}

// ── MD5 (Workers 의 Web Crypto 는 MD5 미지원 → 순수 JS 구현) ──────────
// 입력: Uint8Array, 출력: Uint8Array(16). Joseph Myers 구현 기반.

function md5bytes(input) {
  const x = bytesToWords(input);
  const bitLen = input.length * 8;

  let a = 1732584193, b = -271733879, c = -1732584194, d = 271733878;

  for (let i = 0; i < x.length; i += 16) {
    const oa = a, ob = b, oc = c, od = d;

    a = ff(a, b, c, d, g(x, i, 0), 7, -680876936);
    d = ff(d, a, b, c, g(x, i, 1), 12, -389564586);
    c = ff(c, d, a, b, g(x, i, 2), 17, 606105819);
    b = ff(b, c, d, a, g(x, i, 3), 22, -1044525330);
    a = ff(a, b, c, d, g(x, i, 4), 7, -176418897);
    d = ff(d, a, b, c, g(x, i, 5), 12, 1200080426);
    c = ff(c, d, a, b, g(x, i, 6), 17, -1473231341);
    b = ff(b, c, d, a, g(x, i, 7), 22, -45705983);
    a = ff(a, b, c, d, g(x, i, 8), 7, 1770035416);
    d = ff(d, a, b, c, g(x, i, 9), 12, -1958414417);
    c = ff(c, d, a, b, g(x, i, 10), 17, -42063);
    b = ff(b, c, d, a, g(x, i, 11), 22, -1990404162);
    a = ff(a, b, c, d, g(x, i, 12), 7, 1804603682);
    d = ff(d, a, b, c, g(x, i, 13), 12, -40341101);
    c = ff(c, d, a, b, g(x, i, 14), 17, -1502002290);
    b = ff(b, c, d, a, g(x, i, 15), 22, 1236535329);

    a = gg(a, b, c, d, g(x, i, 1), 5, -165796510);
    d = gg(d, a, b, c, g(x, i, 6), 9, -1069501632);
    c = gg(c, d, a, b, g(x, i, 11), 14, 643717713);
    b = gg(b, c, d, a, g(x, i, 0), 20, -373897302);
    a = gg(a, b, c, d, g(x, i, 5), 5, -701558691);
    d = gg(d, a, b, c, g(x, i, 10), 9, 38016083);
    c = gg(c, d, a, b, g(x, i, 15), 14, -660478335);
    b = gg(b, c, d, a, g(x, i, 4), 20, -405537848);
    a = gg(a, b, c, d, g(x, i, 9), 5, 568446438);
    d = gg(d, a, b, c, g(x, i, 14), 9, -1019803690);
    c = gg(c, d, a, b, g(x, i, 3), 14, -187363961);
    b = gg(b, c, d, a, g(x, i, 8), 20, 1163531501);
    a = gg(a, b, c, d, g(x, i, 13), 5, -1444681467);
    d = gg(d, a, b, c, g(x, i, 2), 9, -51403784);
    c = gg(c, d, a, b, g(x, i, 7), 14, 1735328473);
    b = gg(b, c, d, a, g(x, i, 12), 20, -1926607734);

    a = hh(a, b, c, d, g(x, i, 5), 4, -378558);
    d = hh(d, a, b, c, g(x, i, 8), 11, -2022574463);
    c = hh(c, d, a, b, g(x, i, 11), 16, 1839030562);
    b = hh(b, c, d, a, g(x, i, 14), 23, -35309556);
    a = hh(a, b, c, d, g(x, i, 1), 4, -1530992060);
    d = hh(d, a, b, c, g(x, i, 4), 11, 1272893353);
    c = hh(c, d, a, b, g(x, i, 7), 16, -155497632);
    b = hh(b, c, d, a, g(x, i, 10), 23, -1094730640);
    a = hh(a, b, c, d, g(x, i, 13), 4, 681279174);
    d = hh(d, a, b, c, g(x, i, 0), 11, -358537222);
    c = hh(c, d, a, b, g(x, i, 3), 16, -722521979);
    b = hh(b, c, d, a, g(x, i, 6), 23, 76029189);
    a = hh(a, b, c, d, g(x, i, 9), 4, -640364487);
    d = hh(d, a, b, c, g(x, i, 12), 11, -421815835);
    c = hh(c, d, a, b, g(x, i, 15), 16, 530742520);
    b = hh(b, c, d, a, g(x, i, 2), 23, -995338651);

    a = ii(a, b, c, d, g(x, i, 0), 6, -198630844);
    d = ii(d, a, b, c, g(x, i, 7), 10, 1126891415);
    c = ii(c, d, a, b, g(x, i, 14), 15, -1416354905);
    b = ii(b, c, d, a, g(x, i, 5), 21, -57434055);
    a = ii(a, b, c, d, g(x, i, 12), 6, 1700485571);
    d = ii(d, a, b, c, g(x, i, 3), 10, -1894986606);
    c = ii(c, d, a, b, g(x, i, 10), 15, -1051523);
    b = ii(b, c, d, a, g(x, i, 1), 21, -2054922799);
    a = ii(a, b, c, d, g(x, i, 8), 6, 1873313359);
    d = ii(d, a, b, c, g(x, i, 15), 10, -30611744);
    c = ii(c, d, a, b, g(x, i, 6), 15, -1560198380);
    b = ii(b, c, d, a, g(x, i, 13), 21, 1309151649);
    a = ii(a, b, c, d, g(x, i, 4), 6, -145523070);
    d = ii(d, a, b, c, g(x, i, 11), 10, -1120210379);
    c = ii(c, d, a, b, g(x, i, 2), 15, 718787259);
    b = ii(b, c, d, a, g(x, i, 9), 21, -343485551);

    a = add(a, oa); b = add(b, ob); c = add(c, oc); d = add(d, od);
  }
  return wordsToBytes([a, b, c, d]);

  // 마지막 블록의 인덱스 접근 시 패딩 워드는 undefined → 0 처리
  function g(words, base, k) {
    return words[base + k] | 0;
  }
}

function bytesToWords(bytes) {
  const len = bytes.length;
  // 패딩: 0x80, 0x00.. , 64비트 길이(리틀엔디언 하위 32비트만 실사용)
  const withPad = ((len + 8) >> 6) + 1; // 블록 수(64B 단위)
  const words = new Array(withPad * 16).fill(0);
  for (let i = 0; i < len; i++) {
    words[i >> 2] |= bytes[i] << ((i % 4) * 8);
  }
  words[len >> 2] |= 0x80 << ((len % 4) * 8);
  words[withPad * 16 - 2] = len * 8;
  return words;
}

function wordsToBytes(words) {
  const out = new Uint8Array(16);
  for (let i = 0; i < 16; i++) {
    out[i] = (words[i >> 2] >>> ((i % 4) * 8)) & 0xff;
  }
  return out;
}

function add(x, y) {
  const lsw = (x & 0xffff) + (y & 0xffff);
  const msw = (x >> 16) + (y >> 16) + (lsw >> 16);
  return (msw << 16) | (lsw & 0xffff);
}
function rol(n, c) {
  return (n << c) | (n >>> (32 - c));
}
function cmn(q, a, b, x, s, t) {
  return add(rol(add(add(a, q), add(x, t)), s), b);
}
function ff(a, b, c, d, x, s, t) {
  return cmn((b & c) | (~b & d), a, b, x, s, t);
}
function gg(a, b, c, d, x, s, t) {
  return cmn((b & d) | (c & ~d), a, b, x, s, t);
}
function hh(a, b, c, d, x, s, t) {
  return cmn(b ^ c ^ d, a, b, x, s, t);
}
function ii(a, b, c, d, x, s, t) {
  return cmn(c ^ (b | ~d), a, b, x, s, t);
}
