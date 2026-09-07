# stacks/python/packages/yarch-python/src/yarch_python/xerror/__init__.py
"""契约内核③：BizError——message「默认文案：细节」追加规则（error-codes.md 实现规则-1）。"""

from yarch_python import errcode


class BizError(Exception):
    def __init__(self, code: int, detail: str = "", *, message: str | None = None):
        self.code = code
        self.detail = detail
        self._explicit_message = message
        super().__init__(self.message)

    @property
    def message(self) -> str:
        if self._explicit_message is not None:
            return self._explicit_message
        base = errcode.message_of(self.code)
        return f"{base}：{self.detail}" if self.detail else base

    @property
    def http_status(self) -> int:
        return errcode.http_of(self.code)
