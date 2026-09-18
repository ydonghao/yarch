//! {{project-name}}：yarch rust 服务入口（axum + DDD 七包）。
//! 装配序：PG 连接 + 迁移 → 仓储装配 → setup 中间件栈 → serve。

use std::sync::Arc;
use std::time::Duration;

use axum::routing::get;
use axum::Router;

use {{crate_name}}::api::{self, AppState};
use {{crate_name}}::errors;
use {{crate_name}}::infra::user_repo::UserRepoSqlx;
use yarch_axum::store::{InMemoryIdempotencyStore, InMemoryRateLimiter};
use yarch_axum::{setup, IdemConfig, Options, RateConfig};

#[tokio::main]
async fn main() {
    dotenvy::dotenv().ok();
    errors::register_all();
    let service = std::env::var("YARCH_SERVICE").unwrap_or_else(|_| "{{project-name}}".to_string());
    let env = std::env::var("YARCH_ENV").unwrap_or_else(|_| "local".to_string());
    let database_url =
        std::env::var("DATABASE_URL").expect("DATABASE_URL 必须设置（参照 .env.example）");

    let pool = sqlx::postgres::PgPoolOptions::new()
        .max_connections(10)
        .connect(&database_url)
        .await
        .expect("PG 连接失败");
    if std::env::var("YARCH_SKIP_MIGRATIONS").as_deref() != Ok("true") {
        sqlx::migrate!("./migrations").run(&pool).await.expect("迁移失败");
    }

    let state = AppState {
        users: Arc::new(UserRepoSqlx::new(pool)),
    };
    let router = Router::new()
        .route("/healthz", get(api::healthz))
        .route("/api/v1/echo", axum::routing::post(api::echo))
        .route("/api/v1/users", get(api::users::list).post(api::users::create))
        .route(
            "/api/v1/users/{id}",
            get(api::users::get_one).delete(api::users::remove),
        )
        .fallback(api::not_found)
        .with_state(state);

    let app = setup(
        router,
        Options {
            service,
            env,
            idempotency: Some(IdemConfig { store: Arc::new(InMemoryIdempotencyStore::new()) }),
            rate: Some(RateConfig {
                limiter: Arc::new(InMemoryRateLimiter::new()),
                limit: 60,
                window: Duration::from_secs(60),
            }),
        },
    );
    let listener = tokio::net::TcpListener::bind("0.0.0.0:8080").await.expect("端口绑定失败");
    axum::serve(listener, app).await.expect("服务异常退出");
}
