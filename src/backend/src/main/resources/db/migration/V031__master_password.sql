-- V031: master_password — credenziale unica per operazioni admin distruttive
-- (reset/invalidazione filing+cache di un ticker dalla pagina di dettaglio).
--
-- Hash BCrypt (cost 12, stesso algoritmo della password di login — ADR-006)
-- generato server-side via pgcrypto `crypt(... gen_salt('bf', 12))`. Il formato
-- $2a$ prodotto da pgcrypto è verificabile da Spring BCryptPasswordEncoder.matches.
--
-- Seed iniziale: password "agenticvalueinvestor". DEVE essere cambiata dopo il
-- primo avvio tramite lo script root `generate-master-password.py`.
-- [^src: src/backend/src/main/kotlin/com/valueinvesting/webapp/security/SecurityConfig.kt §BCRYPT_STRENGTH=12]

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE master_password (
    id            BIGSERIAL    PRIMARY KEY,
    password_hash VARCHAR(100) NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO master_password (password_hash)
VALUES (crypt('agenticvalueinvestor', gen_salt('bf', 12)));
