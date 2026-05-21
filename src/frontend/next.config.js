/**
 * Next.js 16 configuration.
 *
 * Modalità SPA con static export (ADR-009 §Dev locale → bundle servito dal backend
 * Spring Boot in prod). Niente runtime SSR.
 *
 * Decisione proxy backend (TSK-030):
 *  - Opzione B scelta: NEXT_PUBLIC_API_BASE_URL → axios chiama direttamente
 *    http://localhost:8080. Motivazione:
 *     1. `output: 'export'` di Next.js 16 NON supporta `rewrites()` runtime.
 *     2. CORS già configurato lato backend (TSK-031 CorsConfig).
 *     3. Stessa code path dev / prod (niente split di comportamento).
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
    NEXT_PUBLIC_API_BASE_URL:
      process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080',
  },
};

module.exports = nextConfig;
