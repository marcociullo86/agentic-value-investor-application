package com.valueinvesting.webapp.config

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * Forward delle SPA route Next.js (con `trailingSlash: true`) all'`index.html`
 * pre-generato sotto `file:/app/public/`. Spring serve `/` -> `/index.html` via
 * welcome page handler, ma per rotte annidate come `/login/` o `/screener/`
 * il resource handler non risolve automaticamente la cartella -> index.html.
 *
 * Per `/analysis` non c'e' piu' `{ticker}` (ADR-013 usa query param
 * `/analysis?ticker=X`); una sola pagina statica e il client legge il ticker
 * via `useSearchParams()`.
 *
 * Nel deploy R1.1 con nginx davanti (TSK-061) il routing SPA sarebbe gestito
 * lato proxy; questo bean copre il deploy single-container (dev / staging
 * legacy).
 *
 * [^src: design_&_architecture/decisions/ADR-009-deployment-target.md §2]
 * [^src: design_&_architecture/decisions/ADR-013-fe-analysis-routing-static-export.md]
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

    @GetMapping("/analysis", "/analysis/")
    fun analysis(): String = "forward:/analysis/index.html"

    @GetMapping("/analysis/deep", "/analysis/deep/")
    fun analysisDeep(): String = "forward:/analysis/deep/index.html"

    @GetMapping("/top-picks", "/top-picks/")
    fun topPicks(): String = "forward:/top-picks/index.html"

    @GetMapping("/profile/mfa", "/profile/mfa/")
    fun profileMfa(): String = "forward:/profile/mfa/index.html"

    @GetMapping("/admin", "/admin/")
    fun admin(): String = "forward:/admin/index.html"
    @GetMapping("/403", "/403/")
    fun forbidden(): String = "forward:/403/index.html"
}
