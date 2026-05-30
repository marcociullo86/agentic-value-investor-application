#!/usr/bin/env python3
# =============================================================================
# generate-master-password.py — aggiorna la MASTER_PASSWORD (tabella
# `master_password`) usata per le operazioni admin distruttive (reset ticker
# dalla pagina di dettaglio Deep Analysis).
#
# ⚠️  ESEGUIRE SOLO QUANDO LO STACK È OPERATIVO E FUNZIONANTE.
#     Lo script si connette al Postgres del compose (porta 5432 esposta su
#     localhost). Avvialo dopo `start-agentic-value-investor` con i container up.
#
# Flusso:
#   1. Chiede la MASTER_PASSWORD ATTUALE e la verifica contro l'hash sul DB.
#      Se non è valida → NESSUN aggiornamento (exit 1).
#   2. Se valida → chiede la NUOVA password (con conferma) e la aggiorna sul DB
#      con hash BCrypt cost 12 (pgcrypto bf, compatibile con Spring login).
#
# Dipendenze: psycopg2  →  pip install psycopg2-binary
# Connessione: variabili libpq standard (PGHOST/PGPORT/PGDATABASE/PGUSER/
#   PGPASSWORD) oppure i default sotto (allineati a src/docker/.env).
# =============================================================================

import getpass
import os
import sys

try:
    import psycopg2
except ImportError:
    sys.exit("Manca psycopg2. Installa con:  pip install psycopg2-binary")

# Default allineati a src/docker/.env (Postgres esposto su localhost dal compose).
DB_CONFIG = {
    "host": os.environ.get("PGHOST", "localhost"),
    "port": os.environ.get("PGPORT", "5432"),
    "dbname": os.environ.get("PGDATABASE", "value_investing"),
    "user": os.environ.get("PGUSER", "postgres"),
    "password": os.environ.get("PGPASSWORD", "postgres"),
}

BCRYPT_COST = 12  # deve combaciare con SecurityConfig.BCRYPT_STRENGTH


def main() -> int:
    print(f"Connessione a {DB_CONFIG['host']}:{DB_CONFIG['port']}/{DB_CONFIG['dbname']} …")
    try:
        conn = psycopg2.connect(**DB_CONFIG)
    except psycopg2.OperationalError as exc:
        sys.exit(f"Connessione al DB fallita (lo stack è avviato?): {exc}")

    try:
        with conn, conn.cursor() as cur:
            cur.execute("SELECT id FROM master_password ORDER BY id LIMIT 1")
            row = cur.fetchone()
            if row is None:
                sys.exit("Nessuna riga in master_password: applica prima la migration V031.")
            master_id = row[0]

            # 1. Verifica password attuale.
            current = getpass.getpass("MASTER_PASSWORD attuale: ")
            cur.execute(
                "SELECT (password_hash = crypt(%s, password_hash)) FROM master_password WHERE id = %s",
                (current, master_id),
            )
            valid = cur.fetchone()[0]
            if not valid:
                sys.exit("MASTER_PASSWORD non valida — nessun aggiornamento eseguito.")

            # 2. Nuova password (con conferma).
            new1 = getpass.getpass("NUOVA MASTER_PASSWORD: ")
            new2 = getpass.getpass("Conferma NUOVA MASTER_PASSWORD: ")
            if not new1:
                sys.exit("La nuova password non può essere vuota.")
            if new1 != new2:
                sys.exit("Le due password non coincidono — nessun aggiornamento.")

            cur.execute(
                "UPDATE master_password "
                "SET password_hash = crypt(%s, gen_salt('bf', %s)), updated_at = now() "
                "WHERE id = %s",
                (new1, BCRYPT_COST, master_id),
            )
        print("✅ MASTER_PASSWORD aggiornata con successo.")
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
