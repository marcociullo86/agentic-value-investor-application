/**
 * Placeholder file — il client tipizzato VERO è generato da:
 *   npm run generate:api
 *
 * Lo script invoca `openapi-typescript` su
 * `design_&_architecture/api/openapi.yaml` e scrive `schema.ts` (gitignored).
 *
 * Motivazione scelta `openapi-typescript` vs `orval`:
 *  - openapi-typescript emette SOLO tipi TS puri (no runtime, no fetcher).
 *    Vogliamo riusare l'axios instance custom (`lib/api/client.ts`) con
 *    interceptor auth e X-Data-Stale; orval avrebbe imposto fetcher dedicato.
 *  - Footprint zero a runtime, perfetto per static export (ADR-009).
 *  - Allineato a frontend-components.md §API client che cita esplicitamente
 *    `openapi-typescript o orval`.
 *
 * Una volta eseguito `npm run generate:api`, importa così:
 *
 *   import type { paths, components } from '@/lib/api/generated/schema';
 *   type SearchResponse = components['schemas']['SearchResponse'];
 */
export {};
