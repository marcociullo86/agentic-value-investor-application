/**
 * Minimal static file server for Playwright static-export tests (TSK-269).
 *
 * Serves the Next.js `out/` directory with:
 *  - SPA-style routing: bare path → trailing-slash redirect → index.html
 *  - POST/PUT/DELETE requests: 204 (no-op; all API calls are mocked via
 *    page.route() in the spec, but the server must not 501-error them)
 *  - CORS headers: allows cross-origin requests from Playwright worker
 *
 * Usage (from src/frontend/):
 *   node scripts/static-test-server.js [port]
 */

const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = parseInt(process.argv[2] || '4000', 10);
const ROOT = path.join(__dirname, '..', 'out');

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js':   'application/javascript',
  '.css':  'text/css',
  '.json': 'application/json',
  '.png':  'image/png',
  '.svg':  'image/svg+xml',
  '.ico':  'image/x-icon',
  '.woff': 'font/woff',
  '.woff2':'font/woff2',
  '.ttf':  'font/ttf',
  '.txt':  'text/plain',
  '.map':  'application/json',
  '.webmanifest': 'application/manifest+json',
};

function corsHeaders() {
  return {
    'Access-Control-Allow-Origin':  '*',
    'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS,HEAD',
    'Access-Control-Allow-Headers': 'Content-Type,Authorization,X-CSRF-Token',
  };
}

const server = http.createServer((req, res) => {
  // OPTIONS preflight and non-GET/HEAD: return 204 immediately.
  // Playwright intercepts all /api/* calls via page.route() before they
  // reach this server; this fallback prevents 501 from Python's http.server
  // from leaking through if a mock is missed.
  if (req.method === 'OPTIONS') {
    res.writeHead(204, corsHeaders());
    res.end();
    return;
  }

  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.writeHead(204, { ...corsHeaders(), 'Content-Length': '0' });
    res.end();
    return;
  }

  const urlPath = req.url.split('?')[0];

  // Redirect bare directory paths to trailing-slash version.
  if (!urlPath.includes('.') && !urlPath.endsWith('/')) {
    const target = `${urlPath}/${req.url.slice(urlPath.length)}`;
    res.writeHead(301, { ...corsHeaders(), Location: target });
    res.end();
    return;
  }

  // Resolve to filesystem path.
  let filePath = path.join(ROOT, urlPath);

  // Serve index.html for directory requests.
  if (urlPath.endsWith('/')) {
    filePath = path.join(filePath, 'index.html');
  }

  // Serve file if it exists.
  if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
    const ext = path.extname(filePath);
    const mime = MIME[ext] || 'application/octet-stream';
    const data = fs.readFileSync(filePath);
    res.writeHead(200, { ...corsHeaders(), 'Content-Type': mime, 'Content-Length': data.length });
    res.end(req.method === 'HEAD' ? undefined : data);
    return;
  }

  // 404 fallback.
  res.writeHead(404, { ...corsHeaders(), 'Content-Type': 'text/plain' });
  res.end('Not Found');
});

server.listen(PORT, '127.0.0.1', () => {
  process.stdout.write(`[static-test-server] listening on http://localhost:${PORT}\n`);
});

process.on('SIGTERM', () => server.close());
process.on('SIGINT',  () => server.close());
