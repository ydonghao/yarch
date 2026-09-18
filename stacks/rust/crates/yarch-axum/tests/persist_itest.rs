//! persist 集成测试：真 PG（YARCH_PG_URL 门控，未设跳过——CI 冒烟不设即跳）。
//! 共享实例纪律：专用 yarch_itest 库/角色（隔离于业务库）；每测试独立表名，测试自建自清。

use sqlx::postgres::{PgArguments, PgPool};
use sqlx::Arguments;
use yarch_axum::persist::{page_of, NOT_DELETED};
use yarch_contract::page::{PageData, PageQuery};

#[derive(Debug, sqlx::FromRow, PartialEq)]
struct Row {
    id: i64,
    name: String,
}

async fn pool() -> Option<PgPool> {
    let url = std::env::var("YARCH_PG_URL").ok()?;
    Some(
        sqlx::postgres::PgPoolOptions::new()
            .max_connections(2)
            .connect(&url)
            .await
            .expect("YARCH_PG_URL 连接失败"),
    )
}

async fn setup_table(pool: &PgPool, table: &str, rows: i64) {
    sqlx::query(&format!("DROP TABLE IF EXISTS {table}"))
        .execute(pool)
        .await
        .unwrap();
    sqlx::query(&format!(
        "CREATE TABLE {table} (id BIGSERIAL PRIMARY KEY, name TEXT NOT NULL, is_deleted TIMESTAMPTZ)"
    ))
    .execute(pool)
    .await
    .unwrap();
    for i in 1..=rows {
        sqlx::query(&format!("INSERT INTO {table} (name) VALUES ($1)"))
            .bind(format!("u{i}"))
            .execute(pool)
            .await
            .unwrap();
    }
}

fn empty_args() -> PgArguments {
    PgArguments::default()
}

#[tokio::test]
async fn page_of_paginates_and_counts() {
    let Some(pool) = pool().await else {
        println!("YARCH_PG_URL 未设置，跳过 persist 集成测试");
        return;
    };
    setup_table(&pool, "page_a", 5).await;
    let q = PageQuery::new(2, 2).unwrap();
    let pd: PageData<Row> = page_of::<Row>(
        &pool,
        "id, name",
        &format!("FROM page_a WHERE {NOT_DELETED}"),
        "id",
        empty_args(),
        &q,
    )
    .await
    .unwrap();
    assert_eq!((pd.total, pd.page, pd.page_size), (5, 2, 2));
    assert_eq!(
        pd.list,
        vec![
            Row {
                id: 3,
                name: "u3".into()
            },
            Row {
                id: 4,
                name: "u4".into()
            }
        ]
    );
}

#[tokio::test]
async fn page_of_d6_out_of_range_returns_empty_list_with_real_total() {
    let Some(pool) = pool().await else {
        println!("YARCH_PG_URL 未设置，跳过 persist 集成测试");
        return;
    };
    setup_table(&pool, "page_b", 3).await;
    let q = PageQuery::new(99, 20).unwrap();
    let pd: PageData<Row> = page_of::<Row>(
        &pool,
        "id, name",
        &format!("FROM page_b WHERE {NOT_DELETED}"),
        "id",
        empty_args(),
        &q,
    )
    .await
    .unwrap();
    assert!(pd.list.is_empty(), "越界页必须空 list");
    assert_eq!(pd.total, 3, "total 仍为真实总数（D6）");
}

#[tokio::test]
async fn page_of_respects_logical_delete_and_bound_args() {
    let Some(pool) = pool().await else {
        println!("YARCH_PG_URL 未设置，跳过 persist 集成测试");
        return;
    };
    setup_table(&pool, "page_c", 4).await;
    sqlx::query("UPDATE page_c SET is_deleted = now() WHERE id = 2")
        .execute(&pool)
        .await
        .unwrap();
    let mut args = PgArguments::default();
    args.add("u%".to_string()).expect("text 绑定不可失败");
    let q = PageQuery::new(1, 10).unwrap();
    let pd: PageData<Row> = page_of::<Row>(
        &pool,
        "id, name",
        &format!("FROM page_c WHERE {NOT_DELETED} AND name LIKE $1"),
        "id",
        args,
        &q,
    )
    .await
    .unwrap();
    assert_eq!(pd.total, 3, "逻辑删除行不计入（is_deleted IS NULL）");
    assert_eq!(pd.list.len(), 3);
    assert!(pd.list.iter().all(|r| r.id != 2));
}
