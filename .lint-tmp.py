import re, json, yaml
from pathlib import Path
from collections import defaultdict

ROOT = Path('.')
errors = []
warnings = []
infos = []

def add(sev, cat, msg, heal=False):
    item = {'sev': sev, 'cat': cat, 'msg': msg, 'heal': heal}
    if sev == 'ERROR':
        errors.append(item)
    elif sev == 'WARNING':
        warnings.append(item)
    else:
        infos.append(item)

def norm(p):
    return str(p).replace('\\', '/')

with open('factory.config.yaml') as f:
    fc = yaml.safe_load(f)

# CHECK 1
wiki_pages = []
for p in ROOT.glob('wiki/**/*.md'):
    rel = norm(p)
    if any(x in rel for x in ['wiki/log.md', 'wiki/index.md', 'wiki/query/', 'wiki/lint/']):
        continue
    wiki_pages.append(p)

index_text = (ROOT / 'wiki/index.md').read_text(encoding='utf-8')
linked_from_index = set(re.findall(r'\[\[([^\]]+)\]\]', index_text))
slug_map = {p.stem: p for p in wiki_pages}

orphans = []
for p in wiki_pages:
    slug = p.stem
    if slug not in linked_from_index:
        found = any(
            f'[[{slug}]]' in op.read_text(encoding='utf-8', errors='ignore')
            for op in wiki_pages if op != p
        )
        if not found:
            orphans.append(norm(p))
            add('WARNING', 'orphan', f'{norm(p)}: pagina non linkata da index né cross-ref')

for p in wiki_pages:
    text = p.read_text(encoding='utf-8', errors='ignore')
    for m in re.findall(r'\[\[([^\]]+)\]\]', text):
        if m.startswith('http') or '/' in m:
            continue
        if m == 'gaps' and (ROOT / 'wiki/gaps.md').exists():
            continue
        if m not in slug_map:
            add('ERROR', 'broken-wikilink', f'{norm(p)}: [[{m}]] non risolve', heal=True)

# CHECK 3
kanban_files = list(ROOT.glob('management/kanban/**/*.md'))
tsk_ids = defaultdict(list)
ep_count = us_count = tsk_count = 0
required_ep = {'id', 'title', 'status', 'priority', 'confidence'}
required_us = {'id', 'title', 'role', 'priority', 'status', 'wiki_page'}
required_tsk = {'id', 'sprint', 'layer', 'consumer', 'priority', 'estimate', 'status'}
valid_layers = {'be', 'fe', 'db', 'qa', 'infra'}
valid_consumers = {'agent', 'human'}

for p in kanban_files:
    rel = norm(p)
    if p.name == 'sprint.md':
        continue
    text = p.read_text(encoding='utf-8', errors='ignore')
    if not text.startswith('---'):
        if p.name.startswith(('EP-', 'US-', 'TSK-')):
            add('ERROR', 'missing-frontmatter', f'{rel}: frontmatter assente', heal=True)
        continue
    parts = text.split('---', 2)
    if len(parts) < 3:
        continue
    try:
        fm = yaml.safe_load(parts[1]) or {}
    except Exception as e:
        add('ERROR', 'invalid-yaml', f'{rel}: frontmatter YAML invalido: {e}')
        continue
    if not isinstance(fm, dict):
        continue

    name = p.stem
    if name.startswith('EP-') and p.parent.name.startswith('EP-'):
        ep_count += 1
        for field in required_ep:
            if field not in fm:
                add('ERROR', 'missing-frontmatter-field', f'{rel}: manca `{field}`', heal=True)
        ep_folder = re.match(r'(EP-\d+)', p.parent.name)
        if ep_folder and fm.get('id') and fm['id'] != ep_folder.group(1):
            add('ERROR', 'id-folder-mismatch', f'{rel}: id {fm.get("id")} != cartella {ep_folder.group(1)}')
    elif name.startswith('US-'):
        us_count += 1
        for field in required_us:
            if field not in fm:
                add('ERROR', 'missing-frontmatter-field', f'{rel}: manca `{field}`', heal=True)
        wp = fm.get('wiki_page', '')
        if wp and not (ROOT / wp).exists():
            add('ERROR', 'broken-wiki-page', f'{rel}: wiki_page `{wp}` inesistente')
        if 'team:' in parts[1]:
            add('WARNING', 'deprecated-field', f'{rel}: campo legacy `team:` presente')
    elif name.startswith('TSK-'):
        tsk_count += 1
        tid = fm.get('id', name)
        tsk_ids[tid].append(rel)
        for field in required_tsk:
            if field not in fm:
                add('ERROR', 'missing-frontmatter-field', f'{rel}: manca `{field}`', heal=True)
        if fm.get('layer') and fm['layer'] not in valid_layers:
            add('ERROR', 'invalid-layer', f'{rel}: layer={fm.get("layer")}')
        if fm.get('consumer') and fm['consumer'] not in valid_consumers:
            add('ERROR', 'invalid-consumer', f'{rel}: consumer={fm.get("consumer")}')
        if 'team:' in parts[1]:
            add('WARNING', 'deprecated-field', f'{rel}: campo legacy `team:` presente')

for tid, paths in tsk_ids.items():
    if len(paths) > 1:
        add('ERROR', 'id-duplicate', f'TSK {tid} duplicato in: {paths}')

# CHECK 4
for p in wiki_pages:
    text = p.read_text(encoding='utf-8', errors='ignore')
    if '## Storie collegate' in text:
        section = text.split('## Storie collegate')[-1][:800]
        for m in re.findall(r'US-\d+', section):
            if not list(ROOT.glob(f'management/kanban/**/{m}.md')):
                add('ERROR', 'broken-story-link', f'{norm(p)}: storia {m} inesistente')

# CHECK 4b
q_file = ROOT / 'management/questions.md'
qtext = q_file.read_text(encoding='utf-8') if q_file.exists() else ''
aperte = qtext.split('[APERTE]')[1].split('[RISOLTE]')[0] if '[APERTE]' in qtext else ''
risolte = qtext.split('[RISOLTE]')[1] if '[RISOLTE]' in qtext else ''
resolved_ids = set(re.findall(r'### (Q_\d+)', risolte))
for m in re.finditer(r'### (Q_\d+)', aperte):
    qid = m.group(1)
    block = aperte[m.start():]
    next_q = re.search(r'### Q_\d+', block[10:])
    block = block[: next_q.start() + 10] if next_q else block[:800]
    if '**Bloccante:**' not in block:
        add('WARNING', 'missing-blocking-level', f'management/questions.md {qid}: campo Bloccante assente')

for p in kanban_files:
    if not p.name.startswith('US-'):
        continue
    text = p.read_text(encoding='utf-8', errors='ignore')
    fm_match = re.match(r'---\n(.*?)\n---', text, re.DOTALL)
    if not fm_match:
        continue
    try:
        fm = yaml.safe_load(fm_match.group(1)) or {}
    except Exception:
        continue
    for field in ['blocked_by', 'pending_clarification']:
        val = fm.get(field, [])
        if not val:
            continue
        if isinstance(val, str):
            val = [val]
        for q in val:
            if q in resolved_ids:
                add('WARNING', 'stale-blocked-by', f'{norm(p)}: {field}={q} ma {q} è in [RISOLTE]')

# CHECK 4c
topology = fc.get('topology', '')
valid_topo = {'knowledge-only', 'plan-only', 'full-stack-agents', 'hybrid-be-agents', 'hybrid-fe-agents', 'custom'}
if topology not in valid_topo:
    add('ERROR', 'invalid-topology', f'factory.config.yaml: topology={topology}')
routing = fc.get('routing', {})
for layer in ['be', 'fe', 'db', 'qa', 'infra']:
    if routing.get(layer) == 'agent':
        agent_path = ROOT / f'.cursor/agents/{layer}-dev.md'
        if not agent_path.exists():
            add('ERROR', 'routing-missing-agent', f'routing.{layer}: agent ma manca {agent_path}')
for agent in ROOT.glob('.cursor/agents/*-dev.md'):
    layer = agent.stem.replace('-dev', '')
    if layer in routing and routing.get(layer) != 'agent':
        add('ERROR', 'orphan-dev-agent', f'{norm(agent)}: agent presente ma routing.{layer} != agent')
if topology in {'full-stack-agents', 'hybrid-be-agents', 'hybrid-fe-agents'} and not fc.get('code_path'):
    add('WARNING', 'dev-agents-without-code-path', 'topology con dev-agent ma code_path vuoto')

# CHECK 4d
vcs = fc.get('vcs', {})
mode = vcs.get('mode', '')
code_path = fc.get('code_path', '')
if mode == 'none' and code_path:
    add('ERROR', 'vcs-mode-mismatch', 'vcs.mode none ma code_path valorizzato')
if mode == 'monorepo' and code_path and (code_path.startswith('/') or '..' in code_path):
    add('ERROR', 'vcs-mode-mismatch', f'monorepo con code_path non relativo: {code_path}')
bs = vcs.get('branch_strategy')
if bs and bs not in {'shared', 'per-tsk', 'per-sprint'}:
    add('ERROR', 'invalid-branch-strategy', str(bs))
cc = vcs.get('commit_coupling')
if cc and cc not in {'pin', 'float'}:
    add('ERROR', 'invalid-commit-coupling', str(cc))
if cc == 'pin' and not (ROOT / '.factory-lock').exists():
    add('WARNING', 'missing-factory-lock', 'commit_coupling pin ma .factory-lock assente')

# CHECK 4e - skip if no manifest
manifest_path = ROOT / 'raw/.extraction-manifest.json'

# CHECK 4f
kp = fc.get('kanban_publish', {})
if kp:
    provider = kp.get('provider', 'none')
    if provider not in {'none', 'github', 'gitlab', 'jira', 'linear', 'custom'}:
        add('ERROR', 'invalid-publish-provider', f'provider={provider}')
    if kp.get('mode', 'push-only') != 'push-only':
        add('ERROR', 'invalid-publish-mode', str(kp.get('mode')))
sched = fc.get('scheduler', {})
if sched:
    en = sched.get('enabled')
    if en not in {True, False}:
        add('ERROR', 'invalid-scheduler-enabled', str(en))
    cpc = sched.get('code_path_conflict')
    if cpc and cpc not in {'strict', 'warn', 'off'}:
        add('ERROR', 'invalid-conflict-mode', str(cpc))
    ecp = sched.get('empty_code_path_policy')
    if ecp and ecp not in {'serial', 'parallel'}:
        add('ERROR', 'invalid-empty-policy', str(ecp))
    mp = sched.get('max_parallel')
    if mp is not None and (not isinstance(mp, int) or mp < 1):
        add('WARNING', 'invalid-max-parallel', str(mp))
    gt = sched.get('parallel_gate_threshold')
    if gt is not None and (not isinstance(gt, int) or gt < 1 or (mp and gt > mp)):
        add('WARNING', 'invalid-gate-threshold', str(gt))

if kp.get('provider') == 'none':
    for p in kanban_files:
        text = p.read_text(encoding='utf-8', errors='ignore')
        fm_match = re.match(r'---\n(.*?)\n---', text, re.DOTALL)
        if not fm_match:
            continue
        fm = yaml.safe_load(fm_match.group(1)) or {}
        if fm.get('external_id'):
            add('WARNING', 'orphan-external-id', f'{norm(p)}: external_id con provider none')

# CHECK 4g
dep_graph = defaultdict(set)
all_ids = {}
for p in kanban_files:
    name = p.stem
    if re.match(r'^(EP|US|TSK)-\d+', name):
        all_ids[name] = p
    text = p.read_text(encoding='utf-8', errors='ignore')
    fm_match = re.match(r'---\n(.*?)\n---', text, re.DOTALL)
    if not fm_match:
        continue
    try:
        fm = yaml.safe_load(fm_match.group(1)) or {}
    except Exception:
        continue
    deps = fm.get('depends_on') or []
    if isinstance(deps, str):
        deps = [deps]
    host_prefix = name.split('-')[0]
    for d in deps:
        if not d:
            continue
        if d.split('-')[0] != host_prefix:
            add('ERROR', 'invalid-depends-on-type', f'{norm(p)}: depends_on {d} cross-tipo')
        if d == name:
            add('ERROR', 'self-depends-on', f'{norm(p)}: auto-riferimento')
        if d not in all_ids:
            add('WARNING', 'orphan-depends-on', f'{norm(p)}: depends_on {d} inesistente')
        dep_graph[name].add(d)
    if name.startswith('TSK-'):
        body_deps = re.findall(r'## Dependencies\s*\n((?:- TSK-\d+\s*\n)+)', text)
        if body_deps:
            body_tsk = set(re.findall(r'TSK-\d+', body_deps[0]))
            fm_set = set(deps)
            for bt in body_tsk - fm_set:
                add('WARNING', 'dependencies-drift', f'{norm(p)}: body ha {bt} non in depends_on frontmatter')
        blocked = fm.get('blocked_by') or []
        if isinstance(blocked, str):
            blocked = [blocked]
        for q in blocked:
            if re.match(r'Q_\d+', str(q)) and q in resolved_ids:
                add('WARNING', 'stale-blocked-by-tsk', f'{norm(p)}: blocked_by {q} risolta')

nodes = set(all_ids.keys())
in_deg = {n: 0 for n in nodes}
adj = defaultdict(set)
for n, ds in dep_graph.items():
    for d in ds:
        if d in nodes:
            adj[d].add(n)
            in_deg[n] += 1
queue = [n for n in nodes if in_deg[n] == 0]
processed = 0
while queue:
    n = queue.pop(0)
    processed += 1
    for m in adj[n]:
        in_deg[m] -= 1
        if in_deg[m] == 0:
            queue.append(m)
cycle_nodes = [n for n in nodes if in_deg[n] > 0 and (n in dep_graph or any(n in ds for ds in dep_graph.values()))]
if cycle_nodes:
    add('ERROR', 'depends-on-cycle', f'ciclo depends_on: {cycle_nodes[:15]}')

# CQRL / factory v2.13
if fc.get('pattern_version') != '2.13':
    add('WARNING', 'pattern-version', f"pattern_version={fc.get('pattern_version')}, atteso 2.13")
if fc.get('code_quality', {}).get('enabled'):
    if not (ROOT / '.cursor/agents/code-reviewer.md').exists():
        add('ERROR', 'cqrl-agent-missing', 'code_quality enabled ma code-reviewer.md assente')
    if not (ROOT / '.cursor/skills/code-review-protocol/SKILL.md').exists():
        add('ERROR', 'cqrl-skill-missing', 'code-review-protocol skill assente')

# Citation audit
cite_broken = []
cite_section = []
for p in list(ROOT.glob('wiki/**/*.md')) + list(ROOT.glob('management/kanban/**/*.md')) + list(ROOT.glob('design_&_architecture/**/*.md')):
    rel = norm(p)
    if '/lint/' in rel or p.name in ('log.md', 'index.md', 'sprint.md'):
        continue
    text = p.read_text(encoding='utf-8', errors='ignore')
    for m in re.findall(r'\[\^src: ([^\]]+)\]', text):
        path_part = m.split(' §', 1)[0].strip()
        fp = ROOT / path_part
        if not fp.exists():
            cite_broken.append(f'{rel}: path `{path_part}` inesistente')
        elif ' §' in m:
            sec = m.split(' §', 1)[1].strip()
            if fp.suffix == '.md':
                content = fp.read_text(encoding='utf-8', errors='ignore')
                headers = re.findall(r'^(#{1,6})\s+(.+)$', content, re.MULTILINE)
                header_texts = [h[1].strip() for h in headers]
                if sec and sec not in header_texts:
                    # fuzzy: check if any header contains sec or vice versa
                    if not any(sec.lower() in h.lower() or h.lower() in sec.lower() for h in header_texts):
                        cite_section.append(f'{rel}: §{sec} non trovato in {path_part}')

cite_broken = list(dict.fromkeys(cite_broken))
cite_section = list(dict.fromkeys(cite_section))

# EP-019 focus
ep19_tsk = list(ROOT.glob('management/kanban/EP-019-cqrl-bonifica-generale/**/TSK-*.md'))
rs_counts = defaultdict(int)
ep19_issues = []
for p in ep19_tsk:
    fm = yaml.safe_load(re.match(r'---\n(.*?)\n---', p.read_text(encoding='utf-8'), re.DOTALL).group(1))
    rs = fm.get('review_status')
    rs_counts[rs or '(absent)'] += 1
    st = fm.get('status')
    if p.stem.startswith('TSK-24') or p.stem.startswith('TSK-25') or p.stem.startswith('TSK-26'):
        # wave A/B orchestrators - review_status optional until run
        pass
    elif st == 'done' and fm.get('consumer') == 'agent' and rs is None:
        ep19_issues.append(f'{p.stem}: done senza review_status')

# Global review_status on done TSK
done_no_review = []
for p in ROOT.glob('management/kanban/**/TSK-*.md'):
    fm_match = re.match(r'---\n(.*?)\n---', p.read_text(encoding='utf-8', errors='ignore'), re.DOTALL)
    if not fm_match:
        continue
    fm = yaml.safe_load(fm_match.group(1)) or {}
    if fm.get('status') == 'done' and fm.get('consumer') == 'agent' and 'review_status' not in fm:
        # exclude EP-019 orchestrator TSKs 240-265
        tid = int(fm.get('id', 'TSK-0').replace('TSK-', ''))
        if tid < 240 or tid > 265:
            done_no_review.append(fm.get('id'))

print('=== SUMMARY ===')
print(f'ERROR: {len(errors)}')
print(f'WARNING: {len(warnings)}')
print(f'INFO: {len(infos)}')
print(f'EP: {ep_count}, US: {us_count}, TSK: {tsk_count}')
print(f'Wiki pages: {len(wiki_pages)}, orphans: {len(orphans)}')
print(f'Citation path broken: {len(cite_broken)}')
print(f'Citation section mismatch: {len(cite_section)}')
print(f'Done agent TSK without review_status (excl EP-019 wave): {len(done_no_review)}')
print()
for e in errors[:25]:
    print(f"[ERROR][{e['cat']}] {e['msg']}")
print('---')
for w in warnings[:25]:
    print(f"[WARNING][{w['cat']}] {w['msg']}")
print('--- EP-019 review_status ---')
for k, v in sorted(rs_counts.items(), key=lambda x: str(x[0])):
    print(f'  {k}: {v}')
print('--- citation broken sample ---')
for c in cite_broken[:8]:
    print(c)
print('--- citation section sample ---')
for c in cite_section[:8]:
    print(c)
print('--- done no review sample ---')
print(','.join(done_no_review[:15]), '... total', len(done_no_review))
