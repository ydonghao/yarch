from fastapi import APIRouter, Request
from yarch_python import web
from yarch_python.xerror import BizError

from api.model.user import CreateUserRequest, UserResponse

router = APIRouter(prefix="/api/v1")


@router.post("/users", status_code=201)
def create_user(body: CreateUserRequest, request: Request):
    repo = request.app.state.user_repo
    user = repo.create(body.username, body.email)
    return web.ok(
        UserResponse(
            id=user.id, username=user.username, email=user.email, createdAt=user.created_at
        ).model_dump(),
        status_code=201,
    )


@router.get("/users")
def list_users(request: Request, page: int = 1, pageSize: int = 20):
    if page < 1 or pageSize < 1 or pageSize > 100:
        raise BizError(1001, detail="page 须为正整数，pageSize 须在 1~100")
    repo = request.app.state.user_repo
    items, total = repo.list_page(page, pageSize)
    # 注意：查询参数名 page 不可与 from web import page 同名混用（会遮蔽函数→运行期 TypeError），
    # 故本文件统一走 web.ok / web.page 模块属性调用
    return web.page([u.model_dump() for u in items], total, page, pageSize)


@router.get("/users/{user_id}")
def get_user(user_id: str, request: Request):
    user = request.app.state.user_repo.get(user_id)
    if user is None:
        raise BizError(1004, detail=f"user {user_id}")
    return web.ok(
        UserResponse(
            id=user.id, username=user.username, email=user.email, createdAt=user.created_at
        ).model_dump()
    )
