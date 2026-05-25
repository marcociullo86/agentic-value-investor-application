"""Embedding sidecar — FastAPI service wrapping sentence-transformers.

Default model: Qwen/Qwen3-Embedding-0.6B (1024 dim, 32K ctx, MTEB 64.6)
Fallback:      Snowflake/snowflake-arctic-embed-l-v2.0 (1024 dim, 8K ctx)
[^src: design_&_architecture/decisions/ADR-018]
"""

import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sentence_transformers import SentenceTransformer

MODEL_NAME = os.getenv("EMBEDDING_MODEL", "Qwen/Qwen3-Embedding-0.6B")
MAX_BATCH = int(os.getenv("EMBEDDING_MAX_BATCH", "32"))

model: SentenceTransformer | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global model
    model = SentenceTransformer(MODEL_NAME)
    yield
    del model


app = FastAPI(title="Embedding Sidecar", lifespan=lifespan)


class EmbedRequest(BaseModel):
    texts: list[str] = Field(..., min_length=1, max_length=MAX_BATCH)


class EmbedResponse(BaseModel):
    embeddings: list[list[float]]


@app.post("/embed", response_model=EmbedResponse)
async def embed(req: EmbedRequest):
    if model is None:
        raise HTTPException(status_code=503, detail="Model not loaded")
    vectors = model.encode(req.texts, normalize_embeddings=True)
    return EmbedResponse(embeddings=vectors.tolist())


@app.get("/health")
async def health():
    if model is None:
        raise HTTPException(status_code=503, detail="Model not loaded")
    return {"status": "ok", "model": MODEL_NAME}
