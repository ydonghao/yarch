"""业务码登记处（3xxx-8xxx；在本仓 docs 登记后方可使用——error-codes.md 实现规则-3）。"""

from yarch_python import errcode

# 示例登记（可替换为你的业务码）：
errcode.register(3001, "USER_EXISTS", "用户已存在", 409)
