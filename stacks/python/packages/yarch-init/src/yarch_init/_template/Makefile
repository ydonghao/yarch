.PHONY: dev worker beat test lint fmt

dev:
	uv run uvicorn main:app --reload

worker:
	uv run celery -A celery_app worker -Q {{ service }}.default -c 2

beat:
	uv run celery -A celery_app beat

test:
	uv run pytest

lint:
	uv run ruff check . && uv run lint-imports

fmt:
	uv run ruff format .
