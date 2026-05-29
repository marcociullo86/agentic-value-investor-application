FROM python:3.12-slim AS builder

WORKDIR /app
COPY requirements.txt .
# torch compilato per CUDA 12.6 (cu126): forward-compatible col driver NVIDIA
# 12.8 dell'host. Il wheel torch di default su PyPI punta a una CUDA più recente
# che richiede un driver più nuovo → errore "NVIDIA driver too old (12080)".
# Installato PRIMA dei requirements così sentence-transformers lo trova già
# soddisfatto e non scarica il wheel di default.
RUN pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cu126
RUN pip install --no-cache-dir -r requirements.txt

# Pre-download model at build time to avoid cold-start latency at runtime.
ARG EMBEDDING_MODEL=Qwen/Qwen3-Embedding-0.6B
RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('${EMBEDDING_MODEL}')"

FROM python:3.12-slim

WORKDIR /app
COPY --from=builder /usr/local/lib/python3.12/site-packages /usr/local/lib/python3.12/site-packages
COPY --from=builder /usr/local/bin/uvicorn /usr/local/bin/uvicorn
COPY --from=builder /root/.cache/huggingface /root/.cache/huggingface
COPY app.py .

ENV EMBEDDING_MODEL=Qwen/Qwen3-Embedding-0.6B
ENV EMBEDDING_MAX_BATCH=32

EXPOSE 8001
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8001"]
