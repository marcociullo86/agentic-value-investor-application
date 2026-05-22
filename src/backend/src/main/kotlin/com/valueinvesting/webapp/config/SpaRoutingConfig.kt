package com.valueinvesting.webapp.config

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

/**
 * Forwards SPA routes (Next.js export with trailingSlash: true) to the matching
 * pre-rendered index.html under file:/app/public/. Spring's welcome page handler
 * only maps "/" → /index.html; nested routes like "/login/" need explicit forwards
 * so the static-resource handler can locate "/login/index.html".
 *
 * Dynamic /analysis/{ticker}/ pages are pre-generated only for a finite set
 * (AAPL, MSFT, GOOGL + 5 more) at FE build time via `generateStaticParams`.
 * Per ticker arbitrari (es. TTD aggiunto a watchlist), forwardiamo a un template
 * generico (`/analysis/AAPL/index.html`): il bundle Next.js lato client legge
 * il ticker reale dall'URL via `useParams()` (vedi `AnalysisPageClient.tsx`) e
 * fetcha i dati corretti — perdiamo il benefit del pre-rendering ma evitiamo
 * 404/500 server-side. Tradeoff documentato sotto il gap `fe-static-export-tickers`.
 *
 * [^src: design_&_architecture/decisions/ADR-009-deployment-target.md §2]
 */
@Controller
class SpaRoutingConfig {

    @GetMapping("/login", "/login/")
    fun login(): String = "forward:/login/index.html"

    @GetMapping("/register", "/register/")
    fun register(): String = "forward:/register/index.html"

    @GetMapping("/watchlist", "/watchlist/")
    fun watchlist(): String = "forward:/watchlist/index.html"

    @GetMapping("/screener", "/screener/")
    fun screener(): String = "forward:/screener/index.html"

    @GetMapping("/moat", "/moat/")
    fun moat(): String = "forward:/moat/index.html"

    // No bare /analysis page — the SPA route is /analysis/{ticker}/.
    //
    // For any /analysis/{ticker}/ we serve the same template (AAPL pre-rendered
    // shell) regardless of ticker: the client (`AnalysisPageClient`) reads the
    // real ticker via `useParams()` from the browser URL. This makes the route
    // work for tickers that were not in `generateStaticParams` at build time.
    @GetMapping("/analysis/{ticker}", "/analysis/{ticker}/")
    fun analysisTicker(@PathVariable ticker: String): String =
        "forward:/analysis/AAPL/index.html"
}
