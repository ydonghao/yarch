# {{ service }}

{{ description }}——由 `yarch-init` 生成（yarch-python DDD 模板）。

## 起跑（共享实例隔离模式）
    cd {{ service }} && cp .env.example .env && vi .env
    uv sync
    uv run uvicorn main:app --reload          # web：自动建库 + Alembic 升级 → :8000/docs
    uv run celery -A celery_app worker -Q {{ service }}.default   # worker（另终端）

## 纪律
- 服务名 `{{ service }}` 去 yarch 仓 contract/registry.md 登记；
- 业务码 3xxx+ 在本仓 docs 登记后方可使用（errors/errno.py）；
- 升级平台件：`uv add "yarch-python@X.Y.Z"`{{ '\n' }}{%- if yarch_path %}（当前为开发期 path 依赖：`{{ yarch_path }}`，yarch-python 正式发版后删除 pyproject 的 `[tool.uv.sources]` 段改版本号）{%- endif %}
