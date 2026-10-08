//! 领域实体（零框架：不依赖 axum/sqlx/tokio；serde 序列化不在禁列——信封回包需要）。

use serde::Serialize;

#[derive(Debug, Clone, PartialEq, Serialize)]
pub struct User {
    pub id: i64,
    pub name: String,
    pub created_at: chrono::DateTime<chrono::Utc>,
}
