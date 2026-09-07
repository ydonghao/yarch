"""repo adapter：实现 domain 的 UserRepository Protocol。"""
from datetime import datetime

from sqlalchemy import select
from yarch_python.persist import not_deleted, page_of

from domain.entity.user import User


class SqlAlchemyUserRepository:
    def __init__(self, session_factory):
        self.session_factory = session_factory

    def create(self, username: str, email: str | None) -> User:
        from infrastructure.database.models import UserRow

        with self.session_factory() as s:
            row = UserRow(username=username, email=email)
            s.add(row)
            s.commit()
            return self._to_entity(row)

    def get(self, user_id: str) -> User | None:
        from infrastructure.database.models import UserRow

        with self.session_factory() as s:
            row = s.get(UserRow, user_id)
            if row is None or row.is_deleted:
                return None
            return self._to_entity(row)

    def list_page(self, page: int, page_size: int) -> tuple[list[User], int]:
        from infrastructure.database.models import UserRow

        with self.session_factory() as s:
            items, total = page_of(s, select(UserRow).where(not_deleted(UserRow)).order_by(
                UserRow.created_at.desc(), UserRow.id), page, page_size)
            return [self._to_entity(r) for r in items], total

    @staticmethod
    def _to_entity(row) -> User:
        return User(id=str(row.id), username=row.username, email=row.email,
                    created_at=row.created_at if isinstance(row.created_at, datetime) else None)
