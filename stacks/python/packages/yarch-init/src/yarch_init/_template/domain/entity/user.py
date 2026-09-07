"""领域实体：零框架依赖（不 import sqlalchemy/fastapi）。"""

from datetime import datetime

from pydantic import BaseModel


class User(BaseModel):
    id: str
    username: str
    email: str | None = None
    created_at: datetime | None = None
