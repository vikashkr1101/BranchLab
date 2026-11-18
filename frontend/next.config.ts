import type { NextConfig } from "next";

const apiOrigin = process.env.BRANCHLAB_API_ORIGIN ?? "http://127.0.0.1:8080";

const config: NextConfig = {
  devIndicators: false,
  async rewrites() {
    return [{ source: "/api/v1/:path*", destination: `${apiOrigin}/api/v1/:path*` }];
  },
};

export default config;
