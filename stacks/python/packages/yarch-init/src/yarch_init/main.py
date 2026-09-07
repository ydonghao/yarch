"""yarch-init 工程生成器（cookiecutter/maven-archetype 模式的 python 对偶）：
模板（_template/，{{ var }} 占位声明式资产，不要求自身可运行）
+ 通用渲染引擎（本程序）+ archetype.json 变量声明。
工程正确性由「生成后冒烟」保证（CI：生成 → uv sync → ruff → pytest → uvicorn 探活）。"""
import argparse
import os
import re
import sys
from pathlib import Path

from jinja2 import Environment, StrictUndefined

SERVICE_RE = re.compile(r"^[a-z][a-z0-9-]{1,31}$")
GENERIC_WORDS = {"api", "app", "service", "server", "backend", "web", "admin", "main",
                 "common", "system", "demo", "user", "gateway"}  # registry.md 一-2 摘录
SKIP_FILES = {"archetype.json"}


def validate_service(name: str) -> None:
    if not SERVICE_RE.match(name):
        raise ValueError(f"服务名 {name!r} 须过 registry.md 一-1 校验：^[a-z][a-z0-9-]{{1,31}}$")
    if name in GENERIC_WORDS:
        raise ValueError(f"服务名 {name!r} 是裸通用词，禁止使用（contract/registry.md 一-2）")


def detect_yarch_path(start: str) -> str | None:
    """发版前 path 依赖：向上找 stacks/python/packages/yarch-python（对偶 golang replace 行）。"""
    d = Path(start).resolve()
    for _ in range(8):
        cand = d / "packages" / "yarch-python"
        if (cand / "pyproject.toml").exists():
            return str(cand)
        if d.parent == d:
            return None
        d = d.parent
    return None


def render(src: str, dst: str, variables: dict) -> int:
    env = Environment(undefined=StrictUndefined, keep_trailing_newline=True, autoescape=False)
    n = 0
    for path in sorted(Path(src).rglob("*")):
        if path.is_dir() or path.name in SKIP_FILES:
            continue
        if "__pycache__" in path.parts:  # 开发仓跑 pytest 会污染模板树（.pyc 二进制），跳过
            continue
        rel = path.relative_to(src)
        if rel.name.endswith(".jinja"):
            # 模板源后缀规约：含 jinja 语句块而自身须保持工具可解析的文件
            # （如 pyproject.toml——TOML 容不得 {% if %}，仓库内 ruff 会解析每个 pyproject.toml）
            # 源名加 .jinja，渲染产物剥后缀
            rel = rel.with_name(rel.name[: -len(".jinja")])
        target = Path(dst) / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        rendered = env.from_string(path.read_text(encoding="utf-8")).render(**variables)
        if "{{" in rendered or "{%" in rendered:
            raise RuntimeError(f"模板残留未渲染占位符：{rel}")
        target.write_text(rendered, encoding="utf-8")
        n += 1
    return n


def template_dir(cli_src: str | None) -> str:
    if cli_src:
        return cli_src
    import yarch_init

    if yarch_init.__file__ is None:  # pragma: no cover - 打包后必然有 __file__
        raise RuntimeError("无法定位包目录（yarch_init.__file__ 缺失）")
    p = Path(yarch_init.__file__).parent / "_template"
    if not p.is_dir():  # T14 移交：默认模板缺失须显式报错，不得静默渲染 0 个文件
        sys.exit(f"yarch-init: 内置模板目录缺失：{p}")
    return str(p)


def main() -> None:
    ap = argparse.ArgumentParser(prog="yarch-init")
    ap.add_argument("--service", required=True, help="服务名（registry 一-1 校验）")
    ap.add_argument("--out", required=True, help="输出目录（须不存在或为空）")
    ap.add_argument("--description", default="")
    ap.add_argument("--src", default=None, help="模板目录（默认用 wheel 内置 _template）")
    args = ap.parse_args()

    try:
        validate_service(args.service)
    except ValueError as e:
        sys.exit(f"yarch-init: {e}")

    out = Path(args.out)
    if out.exists() and any(out.iterdir()):
        sys.exit(f"yarch-init: 输出目录 {out} 非空")

    yarch_path = detect_yarch_path(os.getcwd())
    variables = {
        "service": args.service,
        "package": args.service.replace("-", "_"),
        "description": args.description or f"{args.service} service",
        "yarch_path": yarch_path,  # None → 版本依赖（发版后形态）
    }
    n = render(template_dir(args.src), str(out), variables)
    dep_hint = (
        f"path 依赖 → {yarch_path}（yarch-python 发版后删除 tool.uv.sources 该行改版本号）"
        if yarch_path
        else "版本依赖（yarch-python >= 0.1）"
    )
    print(f"""✅ 已生成 {out}（{n} 个文件）——依赖形态：{dep_hint}

下一步：
  1. cd {out} && cp .env.example .env（填共享 PG/Redis 地址；独立 database 自动建库）&& uv sync
  2. uv run uvicorn main:app --reload   # web 进程；uv run celery -A celery_app worker 另进程
  3. 服务名 {args.service!r}——去 yarch 仓 contract/registry.md 登记
  4. errors/errno.py 业务码段（3xxx+）在你的仓库 docs 登记后方可使用
""")


if __name__ == "__main__":
    main()
