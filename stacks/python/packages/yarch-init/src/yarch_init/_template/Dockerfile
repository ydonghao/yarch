FROM ghcr.io/astral-sh/uv:python3.12-bookworm-slim AS builder
WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev
COPY . .

FROM python:3.12-slim-bookworm
WORKDIR /app
COPY --from=builder /app /app
ENV PATH="/app/.venv/bin:$PATH"
EXPOSE 8000
# web 默认；worker 部署覆写 command：
#   docker run … uv run celery -A celery_app worker -Q {{ service }}.default
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
