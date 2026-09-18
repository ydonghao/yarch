"""契约内核：验证码框架（captcha.md v1.0）——Provider SPI 三档 + 框架核心。

形态 = 进程内组件（CP2）：渠道差异（图形/OTP/第三方）是框架内多态，不构成服务边界。
框架核心管 challenge 生命周期（生成/存储/TTL/一次性原子消费三）、场景路由（四）、
错误语义（2005 三态合一五-2）；Provider 只管生成挑战与答案比对/远程校验（二）。
"""

import secrets
import threading
import time
import uuid
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Protocol

from fastapi import APIRouter, Request

from yarch_python import errcode
from yarch_python.captcha._png import render_png_base64
from yarch_python.redix import Keys
from yarch_python.web import ok
from yarch_python.xerror import BizError

# 二-2【强制】：去混淆 32 字符集（跨栈 conformance 断言对象）
ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
DEFAULT_LEN = 4  # 二-2【强制】：4 位
DEFAULT_TTL_S = 120  # 三-2【推荐】：120s，Options.ttl_s 可配；OTP 档可放宽至 300s


class VerifyMode(Enum):
    """校验模式二分（二-1）。"""

    LOCAL = "LOCAL"  # 本地校验型：答案由框架核心托管，一次性原子消费（三）
    REMOTE = "REMOTE"  # 远程校验型：无服务端答案存储，一次性语义由上游保证


@dataclass
class IssueRequest:
    """分发请求（scene=路由场景可空；destination=发送目标，sms-otp 档必填）。"""

    scene: str | None = None
    destination: str | None = None


@dataclass
class IssuedChallenge:
    """分发产物：secret_answer 机密答案（LOCAL 进存储，REMOTE 恒 None）；payload 公开载体。"""

    secret_answer: str | None
    payload: dict[str, str]


class CaptchaProvider:
    """Provider SPI 基类（一-2/二）：保留字 id = image / sms-otp / turnstile（一-3）。"""

    id: str = ""
    mode: VerifyMode = VerifyMode.LOCAL

    def issue(self, request: IssueRequest) -> IssuedChallenge:
        raise NotImplementedError

    def matches(self, stored: str | None, attempted: str | None) -> bool:
        """默认比对口径（二-2）：attempted 去首尾空白 + 大小写不敏感；精确档（二-3）覆写。"""
        if not stored or attempted is None:
            return False
        return stored.lower() == attempted.strip().lower()


class ImageCaptchaProvider(CaptchaProvider):
    """image 档（二-2，LOCAL 默认档）：4 位去混淆字符集【强制】；渲染样式自由（CP9）。"""

    id = "image"
    mode = VerifyMode.LOCAL

    def issue(self, request: IssueRequest) -> IssuedChallenge:
        answer = "".join(secrets.choice(ALPHABET) for _ in range(DEFAULT_LEN))
        return IssuedChallenge(
            secret_answer=answer,
            payload={"imageBase64": render_png_base64(answer)},
        )

    # matches 用默认口径：trim + 大小写不敏感


class SmsSender(Protocol):
    """OTP 分发通道 SPI（二-3）：由宿主注入——yarch 不背通知渠道抽象（EP7 届时对齐）。"""

    def send(self, destination: str, content: str) -> None: ...


def mask_destination(destination: str) -> str:
    """目标脱敏回显（二-3）：≥7 位留前 3 后 4，其余全遮。"""
    if len(destination) < 7:
        return "****"
    return destination[:3] + "****" + destination[-4:]


class SmsOtpProvider(CaptchaProvider):
    """sms-otp 档（二-3，LOCAL）：6 位数字 OTP（安全随机源）；精确匹配（禁宽松比对）。"""

    id = "sms-otp"
    mode = VerifyMode.LOCAL

    def __init__(self, sender: SmsSender):
        self._sender = sender

    def issue(self, request: IssueRequest) -> IssuedChallenge:
        if not request.destination:
            raise BizError(errcode.Code.INVALID_ARGUMENT, detail="sms-otp 档须提供发送目标")
        otp = f"{secrets.randbelow(10**6):06d}"
        self._sender.send(request.destination, otp)
        return IssuedChallenge(
            secret_answer=otp,
            payload={"destination": mask_destination(request.destination)},
        )

    def matches(self, stored: str | None, attempted: str | None) -> bool:
        """精确匹配（二-3）：仅去首尾空白，无大小写折中。"""
        return bool(stored) and attempted is not None and stored == attempted.strip()


@dataclass
class TurnstileConfig:
    """turnstile 档接入配置（二-4）：secret_key 非空时该档才装配。"""

    site_key: str = ""
    secret_key: str = ""
    verify_url: str = "https://challenges.cloudflare.com/turnstile/v0/siteverify"


class SiteVerifyCaller(Protocol):
    """siteverify 调用器（测试打桩位）。"""

    def __call__(
        self, verify_url: str, secret: str, response: str, remote_ip: str | None
    ) -> bool: ...


def _siteverify(verify_url: str, secret: str, response: str, remote_ip: str | None) -> bool:
    """默认调用器：POST siteverify（form 编码），解析 success；上游不可达 fail closed。"""
    import httpx

    data = {"secret": secret, "response": response}
    if remote_ip:
        data["remoteip"] = remote_ip
    try:
        r = httpx.post(verify_url, data=data, timeout=5.0)
        return bool(r.json().get("success", False))
    except Exception:
        return False


class TurnstileProvider(CaptchaProvider):
    """turnstile 档（二-4，REMOTE）：siteKey 透出 + siteverify 外委；token 不落存储（三-5）。"""

    id = "turnstile"
    mode = VerifyMode.REMOTE

    def __init__(self, config: TurnstileConfig, caller: SiteVerifyCaller | None = None):
        self._config = config
        self._caller = caller or _siteverify

    def issue(self, request: IssueRequest) -> IssuedChallenge:
        # REMOTE 档：无服务端答案（二-1），分发即接入配置透出（前端嵌 widget 自取 token）
        return IssuedChallenge(secret_answer=None, payload={"siteKey": self._config.site_key})

    def verify_remote(self, token: str | None, remote_ip: str | None = None) -> bool:
        if not token or not token.strip():
            return False
        return bool(
            self._caller(self._config.verify_url, self._config.secret_key, token, remote_ip)
        )


@dataclass
class Options:
    """装配配置（三-2/四-2/二-3/二-4）。租户级覆盖是宿主应用层扩展位（四-3）。"""

    default_provider: str = "image"  # 四-2：未配置场景一律走此档
    scenes: dict[str, str] = field(default_factory=dict)  # scene → provider 覆盖映射
    ttl_s: int = DEFAULT_TTL_S  # 三-2【推荐】
    sms_sender: SmsSender | None = None  # 非 None 注册 sms-otp 档
    turnstile: TurnstileConfig | None = None  # 非 None 注册 turnstile 档


@dataclass
class Issued:
    """编程式分发产物（六-4：sms-otp/turnstile 档首发走编程式，HTTP 端点为触发档）。"""

    provider: str
    key: str | None
    payload: dict[str, str]


@dataclass
class Challenge:
    """REST 端点形状（六-1）：provider/key/imageBase64 恒在（image 档，存量字段形态不动 CP10）。"""

    provider: str
    key: str
    image_base64: str


class CaptchaStore:
    """Redis 行为件（一次性 token 语义）；full key 由 Service 经 Keys 生成（三-5）。"""

    def __init__(self, redis: Any):
        self._redis = redis

    def issue(self, key: str, code: str, ttl_s: int) -> None:
        self._redis.set(key, code, ex=ttl_s)

    def consume(self, key: str) -> str | None:
        """GETDEL 原子取出删除（三-4【强制】）——两步 get→delete 是并发重放缺陷。"""
        return self._redis.getdel(key)


class CaptchaService:
    """框架核心（一-2）：生命周期/场景路由/错误语义归此；生成与比对归 Provider。

    装配 fail fast：image 恒注册；sms-otp/turnstile 按 Options 条件注册；
    default_provider 与 scenes 引用未注册 Provider 即构造失败。
    """

    def __init__(self, store: CaptchaStore, keys: Keys, options: Options | None = None):
        self._store = store
        self._keys = keys
        opts = options or Options()
        self._providers: dict[str, CaptchaProvider] = {"image": ImageCaptchaProvider()}
        if opts.sms_sender is not None:
            self._providers["sms-otp"] = SmsOtpProvider(opts.sms_sender)
        if opts.turnstile is not None:
            self._providers["turnstile"] = TurnstileProvider(opts.turnstile)
        if opts.default_provider not in self._providers:
            raise ValueError(f"默认 Provider 未注册：{opts.default_provider}")
        for scene, provider_id in opts.scenes.items():
            if provider_id not in self._providers:
                raise ValueError(f"场景 {scene} 引用未注册 Provider：{provider_id}")
        self._default = opts.default_provider
        self._scenes = dict(opts.scenes)
        self._ttl_s = opts.ttl_s if opts.ttl_s and opts.ttl_s > 0 else DEFAULT_TTL_S

    def route(self, scene: str | None) -> CaptchaProvider:
        """场景路由（四-2）：未配置场景走默认 Provider（不报错）。"""
        provider_id = self._scenes.get(scene or "", self._default)
        return self._providers[provider_id]

    def issue(self, scene: str | None = None, destination: str | None = None) -> Issued:
        """分发。LOCAL 档生成安全随机 key（三-1 UUID4=128bit）带 TTL 托管答案；REMOTE 零存储。"""
        provider = self.route(scene)
        challenge = provider.issue(IssueRequest(scene=scene, destination=destination))
        key: str | None = None
        if provider.mode is VerifyMode.LOCAL:
            key = str(uuid.uuid4())
            self._store.issue(
                self._store_key(provider.id, key), challenge.secret_answer or "", self._ttl_s
            )
        return Issued(provider=provider.id, key=key, payload=challenge.payload)

    def verify(self, scene: str | None, key: str, answer: str | None) -> bool:
        """校验（六-2：内联受保护业务流）。LOCAL = 原子消费后比对（三-3 对/错均消费）；
        REMOTE = 外委校验。false 时业务报 2005 CAPTCHA_INVALID（五-2 三态合一禁细分）。"""
        provider = self.route(scene)
        if provider.mode is VerifyMode.REMOTE:
            remote = getattr(provider, "verify_remote", None)
            return bool(remote(answer)) if remote else False
        stored = self._store.consume(self._store_key(provider.id, key))
        return provider.matches(stored, answer)

    def challenge(self, scene: str | None = None) -> Challenge:
        """REST 端点便捷面（六-1）：只服务 image 档，路由到他档 = 1001（先查档位再分发）。"""
        if self.route(scene).id != "image":
            raise BizError(
                errcode.Code.INVALID_ARGUMENT, detail=f"场景 {scene} 非 image 档，不经图形分发端点"
            )
        issued = self.issue(scene, None)
        return Challenge(
            provider=issued.provider,
            key=issued.key or "",
            image_base64=issued.payload["imageBase64"],
        )

    def _store_key(self, provider_id: str, key: str) -> str:
        """存储 key（三-5）：{服务名}:captcha:{provider}:{key}——首段=服务名租户边界。"""
        return self._keys.of("captcha", provider_id, key)


class LocalLimiter:
    """进程内固定窗口限流（五-1 默认档，对偶 java @RateLimited 进程内档）；
    跨实例档传 redix.FixedWindowLimiter（同 hit(key, window_s, limit) 鸭子接口）。"""

    def __init__(self) -> None:
        self._hits: dict[str, tuple[int, float]] = {}
        self._lock = threading.Lock()

    def hit(self, key: str, window_s: int, limit: int) -> bool:
        now = time.monotonic()
        with self._lock:
            count, window_start = self._hits.get(key, (0, now))
            if now - window_start >= window_s:
                count, window_start = 0, now
            if count >= limit:
                self._hits[key] = (count, window_start)
                return False
            self._hits[key] = (count + 1, window_start)
            return True


def create_router(
    service: CaptchaService,
    *,
    limiter: Any | None = None,
    limit: tuple[int, int] = (10, 60),
) -> APIRouter:
    """GET /api/v1/captcha 端点件（六-1）+ 默认限流（五-1：per-IP 固定窗口 60s/10 次【参考】档）。

    limiter 缺省用进程内 LocalLimiter；传 redix.FixedWindowLimiter 即跨实例档。
    非 image 档场景 = 1001 信封（BizError 由 web.setup 异常处理器渲染）。
    """
    window_limiter = limiter if limiter is not None else LocalLimiter()
    max_hits, window_s = limit
    router = APIRouter()

    @router.get("/api/v1/captcha")
    def captcha_endpoint(request: Request, scene: str | None = None):
        ip = request.client.host if request.client else "unknown"
        if not window_limiter.hit(f"captcha:{ip}", window_s, max_hits):
            raise BizError(errcode.Code.RATE_LIMITED)
        challenge = service.challenge(scene)
        return ok(
            {
                "provider": challenge.provider,
                "key": challenge.key,
                "imageBase64": challenge.image_base64,
            }
        )

    return router
