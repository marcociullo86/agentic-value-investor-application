"""Embedding sidecar — FastAPI service wrapping sentence-transformers.

Default model: Qwen/Qwen3-Embedding-0.6B (1024 dim, 32K ctx, MTEB 64.6)
Fallback:      Snowflake/snowflake-arctic-embed-l-v2.0 (1024 dim, 8K ctx)
[^src: design_&_architecture/decisions/ADR-018]
"""

import logging
import os
import time
from contextlib import asynccontextmanager

import torch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sentence_transformers import SentenceTransformer

logger = logging.getLogger("uvicorn.error")

MODEL_NAME = os.getenv("EMBEDDING_MODEL", "Qwen/Qwen3-Embedding-0.6B")
MAX_BATCH = int(os.getenv("EMBEDDING_MAX_BATCH", "32"))

model: SentenceTransformer | None = None
# Contatore richieste /embed — solo per dare un riferimento progressivo nei log.
_embed_calls: int = 0
# Device effettivo su cui gira il modello — esposto da /health per diagnosi.
# Usa CUDA se la GPU è raggiungibile (passata al container via CDI
# `--device nvidia.com/gpu=all`); altrimenti CPU. Override manuale con
# EMBEDDING_DEVICE (es. "cuda" / "cpu") se serve forzare.
device: str = "unknown"


def _resolve_device() -> str:
    forced = os.getenv("EMBEDDING_DEVICE")
    if forced:
        return forced
    return "cuda" if torch.cuda.is_available() else "cpu"


@asynccontextmanager
async def lifespan(app: FastAPI):
    global model, device
    device = _resolve_device()
    model = SentenceTransformer(MODEL_NAME, device=device)
    # Su GPU usiamo fp16: dimezza VRAM e calcolo. Indispensabile su schede con
    # poca memoria (es. T600 4GB), dove fp32 + batch grandi causano CUDA OOM nel
    # buffer di attention (O(batch * seq^2)). Disattivabile con EMBEDDING_FP16=false.
    use_fp16 = device == "cuda" and os.getenv("EMBEDDING_FP16", "true").lower() != "false"
    if use_fp16:
        model = model.half()
    logger.info(
        "Embedding model '%s' loaded on device=%s fp16=%s (cuda_available=%s)",
        MODEL_NAME, device, use_fp16, torch.cuda.is_available(),
    )
    yield
    del model


app = FastAPI(title="Embedding Sidecar", lifespan=lifespan)


class EmbedRequest(BaseModel):
    texts: list[str] = Field(..., min_length=1, max_length=MAX_BATCH)


class EmbedResponse(BaseModel):
    embeddings: list[list[float]]


@app.post("/embed", response_model=EmbedResponse)
async def embed(req: EmbedRequest):
    global _embed_calls
    if model is None:
        raise HTTPException(status_code=503, detail="Model not loaded")
    _embed_calls += 1
    call_no = _embed_calls
    n_texts = len(req.texts)
    total_chars = sum(len(t) for t in req.texts)
    logger.info(
        "embed #%d START: %d testi (%d char) su device=%s …",
        call_no, n_texts, total_chars, device,
    )
    t0 = time.perf_counter()
    vectors = model.encode(req.texts, normalize_embeddings=True)
    elapsed_ms = (time.perf_counter() - t0) * 1000.0
    logger.info(
        "embed #%d DONE: %d vettori in %.0f ms (%.1f ms/testo, device=%s)",
        call_no, n_texts, elapsed_ms, elapsed_ms / max(n_texts, 1), device,
    )
    return EmbedResponse(embeddings=vectors.tolist())


@app.get("/health")
async def health():
    if model is None:
        raise HTTPException(status_code=503, detail="Model not loaded")
    return {"status": "ok", "model": MODEL_NAME, "device": device}
