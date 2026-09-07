"""repo port（typing.Protocol）：adapter 在 infrastructure。"""

from typing import Protocol

from domain.entity.user import User


class UserRepository(Protocol):
    def create(self, username: str, email: str | None) -> User: ...

    def get(self, user_id: str) -> User | None: ...

    def list_page(self, page: int, page_size: int) -> tuple[list[User], int]: ...
