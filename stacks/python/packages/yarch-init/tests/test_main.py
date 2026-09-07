# yarch-init 渲染引擎最小 fixture 冒烟（真实 _template 在 Task 15，此处不依赖）。
import os

import pytest
from jinja2 import UndefinedError
from yarch_init.main import detect_yarch_path, render, validate_service

FIXTURE = {
    "archetype.json": '{"description": "fixture"}',
    "pyproject.toml": 'name = "{{ service }}"\npkg = "{{ package }}"\n',
    "README.md": "# {{ service }}\n{{ description }}\n",
}


@pytest.fixture
def fixture_template(tmp_path):
    d = tmp_path / "_tpl"
    d.mkdir()
    for name, content in FIXTURE.items():
        (d / name).write_text(content)
    return str(d)


def test_validate_service_rules():
    validate_service("ysaas-scan")
    for bad in ["API", "a", "-abc", "user", "api", "common", "a" * 40, "under_score"]:
        with pytest.raises(ValueError):
            validate_service(bad)


def test_render_variables_and_skip_archetype(tmp_path, fixture_template):
    out = tmp_path / "out"
    n = render(
        fixture_template,
        str(out),
        {"service": "ysaas-scan", "package": "ysaas_scan", "description": "d"},
    )
    assert n == 2
    assert (out / "pyproject.toml").read_text() == 'name = "ysaas-scan"\npkg = "ysaas_scan"\n'
    assert not (out / "archetype.json").exists()


def test_render_rejects_leftover_placeholder(tmp_path):
    d = tmp_path / "_tpl"
    d.mkdir()
    (d / "x.txt").write_text("{{ nope }}")
    # StrictUndefined 先炸未定义变量；即便绕过，残留扫描兜底 RuntimeError
    with pytest.raises((UndefinedError, RuntimeError)):
        render(str(d), str(tmp_path / "o"), {"service": "s"})


def test_detect_yarch_path_from_repo():
    here = os.path.dirname(__file__)
    p = detect_yarch_path(here)
    assert p is not None and p.endswith("packages/yarch-python")
