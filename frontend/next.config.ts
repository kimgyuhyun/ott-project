import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    const origin = process.env.BACKEND_ORIGIN || "http://localhost:8090";
    return [
      { source: "/api/:path*", destination: `${origin}/api/:path*` },
    ];
  },
};

export default nextConfig;
