//! sqlx 行对象（FromRow 派生归 infra；domain 实体不沾 sqlx——对偶 python
//! infrastructure/database/models.py 与 domain/entity.py 分层）。

use crate::domain::entity::User;

#[derive(Debug, sqlx::FromRow)]
pub struct UserRow {
    pub id: i64,
    pub name: String,
    pub created_at: chrono::DateTime<chrono::Utc>,
}

impl From<UserRow> for User {
    fn from(r: UserRow) -> Self {
        Self { id: r.id, name: r.name, created_at: r.created_at }
    }
}
