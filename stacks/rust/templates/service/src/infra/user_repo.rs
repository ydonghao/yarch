//! 仓储实现：sqlx PG（运行时查询；page_of 下推 + NOT_DELETED 纪律）。

use async_trait::async_trait;

use super::user_row::UserRow;
use crate::domain::entity::User;
use crate::domain::repository::UserRepository;
use crate::errors;
use yarch_axum::persist::{page_of, NOT_DELETED};
use yarch_contract::page::{PageData, PageQuery};

pub struct UserRepoSqlx {
    pool: sqlx::postgres::PgPool,
}

impl UserRepoSqlx {
    pub fn new(pool: sqlx::postgres::PgPool) -> Self {
        Self { pool }
    }
}

#[async_trait]
impl UserRepository for UserRepoSqlx {
    async fn page(&self, q: &PageQuery, keyword: Option<&str>) -> Result<PageData<User>, String> {
        let (base, args) = match keyword {
            Some(kw) if !kw.trim().is_empty() => (
                format!("FROM users WHERE {NOT_DELETED} AND name ILIKE $1"),
                {
                    let mut a = sqlx::postgres::PgArguments::default();
                    a.add(format!("%{}%", kw.trim()))
                        .expect("text 绑定不可失败");
                    a
                },
            ),
            _ => (format!("FROM users WHERE {NOT_DELETED}"), Default::default()),
        };
        let pd: PageData<UserRow> = page_of(&self.pool, "id, name, created_at", &base, "id", args, q)
            .await
            .map_err(|e| format!("page users: {e}"))?;
        Ok(PageData::new(
            pd.list.into_iter().map(Into::into).collect(),
            pd.total,
            pd.page,
            pd.page_size,
        ))
    }

    async fn find(&self, id: i64) -> Result<Option<User>, String> {
        let row: Option<UserRow> =
            sqlx::query_as(&format!("SELECT id, name, created_at FROM users WHERE id = $1 AND {NOT_DELETED}"))
                .bind(id)
                .fetch_optional(&self.pool)
                .await
                .map_err(|e| format!("find user: {e}"))?;
        Ok(row.map(Into::into))
    }

    async fn create(&self, name: &str) -> Result<User, String> {
        let exists: Option<i64> = sqlx::query_scalar(&format!(
            "SELECT id FROM users WHERE name = $1 AND {NOT_DELETED}"
        ))
        .bind(name)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| format!("check user name: {e}"))?;
        if exists.is_some() {
            return Err(errors::user_name_taken_message());
        }
        let row: UserRow = sqlx::query_as("INSERT INTO users (name) VALUES ($1) RETURNING id, name, created_at")
            .bind(name)
            .fetch_one(&self.pool)
            .await
            .map_err(|e| format!("create user: {e}"))?;
        Ok(row.into())
    }

    async fn soft_delete(&self, id: i64) -> Result<bool, String> {
        let result = sqlx::query(&format!(
            "UPDATE users SET is_deleted = now() WHERE id = $1 AND {NOT_DELETED}"
        ))
        .bind(id)
        .execute(&self.pool)
        .await
        .map_err(|e| format!("soft delete user: {e}"))?;
        Ok(result.rows_affected() > 0)
    }
}
