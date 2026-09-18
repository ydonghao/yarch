//! 领域实体（零框架：不依赖 axum/sqlx/tokio）。

#[derive(Debug, Clone, PartialEq)]
pub struct User {
    pub id: i64,
    pub name: String,
    pub created_at: chrono::DateTime<chrono::Utc>,
}
