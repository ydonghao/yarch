# stacks/python/packages/yarch-init/tests/test_template.py
from pathlib import Path

from yarch_init.main import render

REQUIRED = [
    "main.py", "celery_app.py", "pyproject.toml", ".env.example", ".python-version",
    ".gitignore", "Makefile", "Dockerfile", "README.md",
    "api/router.py", "api/handler/users.py", "api/model/user.py",
    "application/app.py", "application/settings.py",
    "domain/entity/user.py", "domain/repository/user.py",
    "infrastructure/database/models.py", "infrastructure/database/user_repo.py",
    "infrastructure/database/migrations/env.py",
    "infrastructure/database/migrations/script.py.mako",
    "infrastructure/database/migrations/versions/0001_init.py",
    "crossdomain/README.md", "conf/README.md", "pkg/README.md",
    "errors/errno.py", "tasks/users_task.py", "tests/test_smoke.py", "tests/__init__.py",
]


def template_root() -> Path:
    import yarch_init
    return Path(yarch_init.__file__).parent / "_template"


def test_template_renders_all_required_files(tmp_path):
    n = render(str(template_root()), str(tmp_path),
               dict(service="ysaas-scan", package="ysaas_scan", description="d",
                    yarch_path="/tmp/yarch-python"))
    assert n >= len(REQUIRED)
    for rel in REQUIRED:
        assert (tmp_path / rel).exists(), rel
    main_py = (tmp_path / "main.py").read_text()
    assert "ysaas_scan" not in main_py  # main.py 不应掺包名（顶层入口）
    pyproject = (tmp_path / "pyproject.toml").read_text()
    assert 'path = "/tmp/yarch-python"' in pyproject
    assert "ysaas-scan" in (tmp_path / ".env.example").read_text()
    env_example = (tmp_path / ".env.example").read_text()
    assert "PGHOST/ysaas_scan" in env_example  # 库名下划线形（ensure_database 拒 hyphen）
    settings_py = (tmp_path / "application/settings.py").read_text()
    assert "localhost/ysaas_scan" in settings_py
    assert 'env_file=".env"' in settings_py  # 探活依赖 settings 真读 .env（进程 env 仍优先）
    assert 'extra="ignore"' in settings_py  # .env 混放第三方变量不炸启动（forbid 会炸）
