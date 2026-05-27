#!/usr/bin/env python3
"""
setup.py — OpenAI Assistants adapter setup for Agentic Factory llm-wiki++ v2.13

Crea gli Assistants via OpenAI API in base a:
- .openai/assistants/*.json (definizioni)
- factory.config.yaml (topology, opt-in features)

Salva gli Assistant IDs in .openai/config.yaml per uso successivo.

Requisiti:
- pip install openai pyyaml
- export OPENAI_API_KEY=sk-...
"""

import json
import os
import sys
from pathlib import Path

try:
    import yaml
    from openai import OpenAI
except ImportError:
    print("Installa dipendenze: pip install openai pyyaml", file=sys.stderr)
    sys.exit(1)

REPO_ROOT = Path(__file__).resolve().parent.parent
OPENAI_DIR = REPO_ROOT / ".openai"
ASSISTANTS_DIR = OPENAI_DIR / "assistants"
SKILLS_DIR = OPENAI_DIR / "skills"
CONFIG_PATH = OPENAI_DIR / "config.yaml"
FACTORY_CONFIG_PATH = REPO_ROOT / "factory.config.yaml"


def load_factory_config() -> dict:
    if not FACTORY_CONFIG_PATH.exists():
        print(f"ERROR: {FACTORY_CONFIG_PATH} non trovato. Sei al root della factory?", file=sys.stderr)
        sys.exit(1)
    with FACTORY_CONFIG_PATH.open() as f:
        return yaml.safe_load(f)


def filter_assistants_by_topology(all_assistants: list[Path], factory_config: dict) -> list[Path]:
    """Filtra gli Assistants in base a topology + opt-in features."""
    topology = factory_config.get("topology", "knowledge-only")
    routing = factory_config.get("routing", {})
    kanban_publish = factory_config.get("kanban_publish", {})
    code_quality = factory_config.get("code_quality", {})

    # Core sempre presenti
    core_assistants = {
        "orchestrator", "sync-docs", "wiki-keeper",
        "product-manager", "lead-architect", "tpm",
        "wiki-query", "wiki-lint",
    }

    # Dev-agent (condizionali su routing)
    dev_assistants = set()
    for layer in ["be", "fe", "db", "qa"]:
        if routing.get(layer) == "agent":
            dev_assistants.add(f"{layer}-dev")

    # Publisher
    publisher = kanban_publish.get("provider", "none")
    if publisher == "github":
        core_assistants.add("github-publisher")

    # CQRL
    if code_quality.get("enabled", False):
        core_assistants.add("code-reviewer")

    # Sync condizionali — semplificato qui; in v2.14 leggere wiki_feed_source dal config
    # core_assistants.add("figma-sync")  # se opt-in
    # core_assistants.add("repo-sync")   # se opt-in

    active = core_assistants | dev_assistants

    return [p for p in all_assistants if p.stem in active]


def create_assistant(client: OpenAI, definition: dict) -> str:
    """Crea un Assistant via API e ritorna l'ID."""
    print(f"  Creating Assistant: {definition['name']}...")
    response = client.beta.assistants.create(
        name=definition["name"],
        description=definition.get("description", ""),
        instructions=definition["instructions"],
        model=definition.get("model", "gpt-4o"),
        tools=definition.get("tools", []),
    )
    print(f"    ✓ ID: {response.id}")
    return response.id


def main():
    if "OPENAI_API_KEY" not in os.environ:
        print("ERROR: OPENAI_API_KEY non settata. export OPENAI_API_KEY=sk-...", file=sys.stderr)
        sys.exit(1)

    factory_config = load_factory_config()
    print(f"Factory: {factory_config.get('topology', 'unknown')}")
    print(f"Pattern: v{factory_config.get('pattern_version', '?')}")

    # Discover assistants
    all_assistants = sorted(ASSISTANTS_DIR.glob("*.json"))
    if not all_assistants:
        print(f"ERROR: nessun Assistant definito in {ASSISTANTS_DIR}", file=sys.stderr)
        sys.exit(1)

    # Filtra per topology
    active = filter_assistants_by_topology(all_assistants, factory_config)
    print(f"\nAssistants da creare: {len(active)} su {len(all_assistants)} totali")
    for p in active:
        print(f"  - {p.stem}")

    # Gate user
    confirm = input("\nProcedo con la creazione via OpenAI API? [y/N] ").strip().lower()
    if confirm != "y":
        print("Aborted.")
        sys.exit(0)

    client = OpenAI()

    # Carica config esistente (per non duplicare)
    config = {}
    if CONFIG_PATH.exists():
        with CONFIG_PATH.open() as f:
            config = yaml.safe_load(f) or {}
    config.setdefault("assistants", {})

    # Crea Assistants
    print("\nCreating Assistants...")
    for p in active:
        if p.stem in config["assistants"]:
            print(f"  - {p.stem}: già esistente (ID {config['assistants'][p.stem]}). Skip.")
            continue

        with p.open() as f:
            definition = json.load(f)

        assistant_id = create_assistant(client, definition)
        config["assistants"][p.stem] = assistant_id

    # Salva config
    with CONFIG_PATH.open("w") as f:
        yaml.safe_dump(config, f, default_flow_style=False)

    print(f"\n✓ Setup completato. Config salvata in {CONFIG_PATH}")
    print("\nProssimi step:")
    print("  1. (Opzionale) python sync.py upload  — popola vector store con wiki/, management/, raw/")
    print("  2. python run.py orchestrator --command run  — invoca Orchestrator")
    print("\nNota: run.py NON è scaffoldato in v2.13. Customizzalo per il tuo workflow.")


if __name__ == "__main__":
    main()
