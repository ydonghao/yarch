from datetime import datetime

from pydantic import BaseModel, Field


class CreateUserRequest(BaseModel):
    username: str = Field(min_length=2, max_length=32)
    email: str | None = None


class UserResponse(BaseModel):
    id: str
    username: str
    email: str | None
    createdAt: datetime | None = None
