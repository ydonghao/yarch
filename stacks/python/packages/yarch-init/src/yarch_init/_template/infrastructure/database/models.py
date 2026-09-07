from __future__ import annotations

from typing import Any

from sqlalchemy import Text, text
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column
from yarch_python.persist import AuditMixin, Base, SoftDeleteMixin


class UserRow(Base, AuditMixin, SoftDeleteMixin):
    __tablename__ = "users"

    id: Mapped[Any] = mapped_column(
        UUID(as_uuid=True), primary_key=True, server_default=text("gen_random_uuid()")
    )
    username: Mapped[str] = mapped_column(Text, nullable=False, unique=True)
    email: Mapped[str | None] = mapped_column(Text)
