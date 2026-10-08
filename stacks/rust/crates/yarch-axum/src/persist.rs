//! sqlx 装配（spec 五-1/五-2）：分页下推（D6）+ 逻辑删除纪律。
//! 运行时查询口径（query_as_with，非 query! 宏）——业务工程零 DATABASE_URL 编译前提；
//! query! 宏 + .sqlx 离线快照档随发版批评估。
//!
//! 纪律：select/base/order_by 只允许**调用方常量字符串**（对偶 golang Scope），
//! 用户输入只经 PgArguments 绑定，杜绝 SQL 注入面。

use sqlx::postgres::{PgArguments, PgPool};
use sqlx::Arguments;
use yarch_contract::page::{PageData, PageQuery};

/// 逻辑删除过滤片段（四栈口径：is_deleted timestamptz，NULL = 存活）。
pub const NOT_DELETED: &str = "is_deleted IS NULL";

/// 分页查询装配：count（过滤后总数）+ LIMIT/OFFSET 下推当前页。
/// D6 天然成立：page 超出总页数时 OFFSET 越界返回空 list、total 仍为真实总数。
///
/// - `select`：列清单（如 "id, name, created_at"）
/// - `base`：FROM + WHERE 片段（如 "FROM users WHERE is_deleted IS NULL AND name ILIKE $1"）
/// - `order_by`：排序列（如 "id"）；keyset 通道由调用方走 `WHERE id > $n` 模式
///   + `PageData::with_next_cursor`
///
/// LIMIT/OFFSET 追加为末两个占位符（编号 = args.len()+1 / +2，PgArguments 可 Clone）。
pub async fn page_of<T>(
    pool: &PgPool,
    select: &str,
    base: &str,
    order_by: &str,
    mut args: PgArguments,
    q: &PageQuery,
) -> sqlx::Result<PageData<T>>
where
    T: Send + Unpin + for<'r> sqlx::FromRow<'r, sqlx::postgres::PgRow>,
{
    let total: i64 = sqlx::query_scalar_with(&format!("SELECT count(*) {base}"), args.clone())
        .fetch_one(pool)
        .await?;

    let limit_no = args.len() + 1;
    let offset_no = args.len() + 2;
    let list_sql =
        format!("SELECT {select} {base} ORDER BY {order_by} LIMIT ${limit_no} OFFSET ${offset_no}");
    // i64 绑定在 PG 编码上不可失败（ Arguments::add 的 Result 仅 exotic 类型可 Err）
    args.add(q.limit()).expect("i64 LIMIT 绑定不可失败");
    args.add(q.offset()).expect("i64 OFFSET 绑定不可失败");
    let list: Vec<T> = sqlx::query_as_with(&list_sql, args).fetch_all(pool).await?;
    Ok(PageData::new(list, total, q.page, q.page_size))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn not_deleted_fragment_is_constant() {
        assert_eq!(NOT_DELETED, "is_deleted IS NULL");
    }
}
