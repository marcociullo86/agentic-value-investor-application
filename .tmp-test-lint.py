#!/usr/bin/env python3
"""v2.14 lint smoke for test wave."""
import re
from pathlib import Path
import yaml

ROOT = Path(__file__).resolve().parent
ERRORS, WARNINGS = [], []

def err(c, m):
    ERRORS.append((c, m))

def warn(c, m):
    WARNINGS.append((c, m))

wiki = [p for p in ROOT.glob("wiki/**/*.md") if "/lint/" not in p.as_posix() and p.name not in ("log.md", "index.md")]
slugs = {p.stem for p in wiki}

for wf in wiki:
    body = re.sub(r"```[\s\S]*?```", "", wf.read_text(encoding="utf-8", errors="replace"))
    for m in re.findall(r"\[\[([^\]|#]+)\]\]", body):
        b = m.split("/")[-1]
        if b not in slugs and not m.startswith("http"):
            if "<" not in m and m not in ("…", "wiki-page", "name"):
                err("broken-wikilink", f"{wf.relative_to(ROOT)}: [[{m}]]")

cfg = yaml.safe_load((ROOT / "factory.config.yaml").read_text())
if cfg.get("pattern_version") != "2.14":
    err("version", "pattern_version != 2.14")
for f in [
    ".claude/skills/caveman-protocol.md",
    ".claude/skills/graphify-extraction-protocol.md",
    ".claude/agents/graphify-sync.md",
]:
    if not (ROOT / f).exists():
        err("v214-missing", f)

comp = cfg.get("compression", {})
inv = comp.get("output", {}).get("invariants", {})
for k in ("to_user", "to_artifact", "propagate_resolution"):
    if str(inv.get(k)).lower() not in ("off", "false"):
        err("invariant", k)

print(f"ERRORS={len(ERRORS)} WARNINGS={len(WARNINGS)}")
for c, m in ERRORS[:15]:
    print(f"E {c}: {m}")
for c, m in WARNINGS[:5]:
    print(f"W {c}: {m}")
print("VERDICT", "PASS" if not ERRORS else "FAIL")
