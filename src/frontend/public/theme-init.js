/**
 * Anti-FOUC theme bootstrap (loaded from `self` — no inline script, TSK-222 CSP).
 */
(function () {
  try {
    var t = localStorage.getItem("theme");
    var d =
      t === "dark" ||
      (!t && window.matchMedia("(prefers-color-scheme: dark)").matches);
    if (d) document.documentElement.classList.add("dark");
  } catch (e) {
    /* ignore */
  }
})();
