/**
 * Next.js 16 configuration.
 *
 * Modalità SPA con static export (ADR-009 §Dev locale → bundle servito dal backend
 * Spring Boot in prod). Niente runtime SSR.
 *
 * API base URL:
 *  - Default same-origin (stringa vuota), con override opzionale via env
 *    `NEXT_PUBLIC_API_BASE_URL`.
 *  - Evita bundle hardcoded su localhost in static export.
 *
 * @type {import('next').NextConfig}
 */
const nextConfig = {
  output: 'export',
  reactStrictMode: true,
  trailingSlash: true,
  images: {
    // Richiesto per `output: 'export'` (loader Next disabilitato).
    unoptimized: true,
  },
  env: {
    NEXT_PUBLIC_API_BASE_URL: process.env.NEXT_PUBLIC_API_BASE_URL || '',
  },
};

module.exports = nextConfig;
