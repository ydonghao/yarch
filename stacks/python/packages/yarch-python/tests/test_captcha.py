# stacks/python/packages/yarch-python/tests/test_captcha.py
"""验证码框架契约断言：captcha.md v1.0 跨栈一致性向量（附录 V 系）python 落地 + 端点/限流。

golang/java 同源向量对照：V1-V5（image 生命周期）、V6（原子消费）、V7（TTL）、
V8（sms-otp）、V9（turnstile REMOTE）、V10（场景路由）、V11（端点限流 1006）。
"""

import base64
import io
import threading

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient
from yarch_python import errcode, logx
from yarch_python.captcha import (
    ALPHABET,
    DEFAULT_LEN,
    DEFAULT_TTL_S,
    CaptchaService,
    IssueRequest,
    LocalLimiter,
    Options,
    TurnstileConfig,
    TurnstileProvider,
    create_router,
    mask_destination,
)
from yarch_python.redix import Keys
from yarch_python.web import setup
from yarch_python.xerror import BizError


class FakeStore:
    """并发安全内存存储（Consume 取出即删——V6 原子语义底座；Redis 行为级在 test_captcha_tc）。"""

    def __init__(self):
        self._lock = threading.Lock()
        self.data: dict[str, str] = {}
        self.ttls: dict[str, int] = {}

    def issue(self, key: str, code: str, ttl_s: int) -> None:
        with self._lock:
            self.data[key] = code
            self.ttls[key] = ttl_s

    def consume(self, key: str) -> str | None:
        with self._lock:
            return self.data.pop(key, None)


class FakeSender:
    def __init__(self):
        self.sent: list[tuple[str, str]] = []

    def send(self, destination: str, content: str) -> None:
        self.sent.append((destination, content))


def make_svc(store=None, options=None) -> CaptchaService:
    return CaptchaService(store if store is not None else FakeStore(), Keys("svc"), options)


def test_v1_v5_image_lifecycle():
    store = FakeStore()
    svc = make_svc(store)
    issued = svc.issue()
    assert issued.provider == "image" and issued.key
    key = f"svc:captcha:image:{issued.key}"
    answer = store.data[key]
    assert len(answer) == DEFAULT_LEN and all(c in ALPHABET for c in answer)  # 二-2【强制】

    assert svc.verify(None, issued.key, f"  {answer.lower()}  ")  # V5：trim + 大小写不敏感
    assert not svc.verify(None, issued.key, answer)  # V2：一次性——二次必败

    issued2 = svc.issue()
    answer2 = store.data[f"svc:captcha:image:{issued2.key}"]
    assert not svc.verify(None, issued2.key, "XXXX")  # V3：错答案失败
    assert not svc.verify(None, issued2.key, answer2)  # V3：错试后已消费（防重放枚举）

    assert not svc.verify(None, "00000000-0000-0000-0000-000000000000", "ABCD")  # V4


def test_v7_ttl_default_and_custom():
    store = FakeStore()
    svc = make_svc(store)
    issued = svc.issue()
    assert store.ttls[f"svc:captcha:image:{issued.key}"] == DEFAULT_TTL_S == 120  # 三-2【推荐】

    store2 = FakeStore()
    make_svc(store2, Options(ttl_s=300)).issue()
    assert next(iter(store2.ttls.values())) == 300


def test_v6_concurrent_exactly_one_wins():
    store = FakeStore()
    svc = make_svc(store)
    issued = svc.issue()
    answer = store.data[f"svc:captcha:image:{issued.key}"]

    results: list[bool] = []
    lock = threading.Lock()
    start = threading.Event()

    def worker():
        start.wait()
        r = svc.verify(None, issued.key, answer)
        with lock:
            results.append(r)

    threads = [threading.Thread(target=worker) for _ in range(8)]
    for t in threads:
        t.start()
    start.set()
    for t in threads:
        t.join()
    assert sum(results) == 1  # V6：并发校验恰一过（原子消费）


def test_v8_sms_otp_with_scene_routing():
    store = FakeStore()
    sender = FakeSender()
    svc = make_svc(store, Options(scenes={"login": "sms-otp"}, sms_sender=sender))

    issued = svc.issue("login", "13800138000")
    assert issued.provider == "sms-otp"  # V10：scene 路由生效
    dest, otp = sender.sent[-1]
    assert dest == "13800138000"  # OTP 经宿主通道发送
    assert len(otp) == 6 and otp.isdigit()  # 二-3：6 位数字
    assert issued.payload["destination"] == "138****8000"  # 目标脱敏回显
    assert mask_destination("12345") == "****"

    assert svc.verify("login", issued.key, otp)
    issued2 = svc.issue("login", "13800138000")
    otp2 = sender.sent[-1][1]
    wrong = "000000" if otp2 != "000000" else "111111"
    assert not svc.verify("login", issued2.key, wrong)  # 精确比对
    assert not svc.verify("login", issued2.key, otp2)  # V3：错试后已消费

    with pytest.raises(BizError) as ei:  # 二-3：缺发送目标
        svc.issue("login", None)
    assert ei.value.code == errcode.Code.INVALID_ARGUMENT


def test_v9_turnstile_remote():
    store = FakeStore()

    def stub(verify_url, secret, response, remote_ip):
        return response == "good-token"

    provider = TurnstileProvider(
        TurnstileConfig(site_key="site-1", secret_key="sec-1"), caller=stub
    )
    challenge = provider.issue(IssueRequest())
    assert challenge.secret_answer is None  # REMOTE 档无服务端答案（二-1）
    assert challenge.payload == {"siteKey": "site-1"}
    assert not store.data  # V9：全程零存储（三-5）

    assert provider.verify_remote("good-token", "10.0.0.1")
    assert not provider.verify_remote("bad-token", None)
    assert not provider.verify_remote("", None) and not provider.verify_remote("  ", None)


def test_v10_scene_routing():
    svc = make_svc(options=Options(scenes={"login": "sms-otp"}, sms_sender=FakeSender()))
    assert svc.route("register").id == "image"  # 未配置场景走默认
    assert svc.route(None).id == "image"

    svc2 = make_svc(options=Options(default_provider="sms-otp", sms_sender=FakeSender()))
    assert svc2.route("anything").id == "sms-otp"  # 默认档覆盖

    with pytest.raises(ValueError):  # fail fast：引用未注册 Provider
        make_svc(options=Options(scenes={"login": "nope"}))
    with pytest.raises(ValueError):
        make_svc(options=Options(default_provider="nope"))


def test_challenge_non_image_scene_is_1001():
    svc = make_svc(options=Options(scenes={"login": "sms-otp"}, sms_sender=FakeSender()))
    with pytest.raises(BizError) as ei:  # 六-1：端点只服务 image 档
        svc.challenge("login")
    assert ei.value.code == errcode.Code.INVALID_ARGUMENT


def test_captcha_invalid_code_pinned():
    assert errcode.Code.CAPTCHA_INVALID == 2005  # 五-2 三态合一
    assert errcode.http_of(2005) == 400


def make_app(limit=(10, 60), limiter=None) -> FastAPI:
    logx.setup("ysaas-scan", "local", sink=io.StringIO())
    store = FakeStore()
    sender = FakeSender()
    svc = make_svc(store, Options(scenes={"login": "sms-otp"}, sms_sender=sender, turnstile=None))
    app = FastAPI()
    setup(app, service="ysaas-scan", env="local")
    app.include_router(create_router(svc, limiter=limiter, limit=limit))
    return app


def test_endpoint_fields_and_png_bare_base64():
    client = TestClient(make_app())
    r = client.get("/api/v1/captcha")
    assert r.status_code == 200
    body = r.json()
    assert body["code"] == 0
    assert body["data"]["provider"] == "image"  # 六-1：provider 增量字段
    assert body["data"]["key"]
    raw = base64.b64decode(body["data"]["imageBase64"])  # 裸 base64（二-2：无 data: 前缀）
    assert raw.startswith(b"\x89PNG")
    assert not body["data"]["imageBase64"].startswith("data:")


def test_endpoint_non_image_scene_400_1001():
    client = TestClient(make_app())
    r = client.get("/api/v1/captcha", params={"scene": "login"})
    assert r.status_code == 400
    assert r.json()["code"] == errcode.Code.INVALID_ARGUMENT


def test_v11_endpoint_rate_limited():
    client = TestClient(make_app(limit=(3, 60)))
    for _ in range(3):
        assert client.get("/api/v1/captcha").status_code == 200
    r = client.get("/api/v1/captcha")
    assert r.status_code == 429  # 五-1：超限 429 + 1006
    assert r.json()["code"] == errcode.Code.RATE_LIMITED


def test_local_limiter_window_semantics():
    lim = LocalLimiter()
    assert all(lim.hit("k", 60, 3) for _ in range(3))
    assert not lim.hit("k", 60, 3)
    assert lim.hit("other", 60, 3)  # 独立 key 互不影响
