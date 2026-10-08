//! 仓储端口（依赖倒置：端口在 domain，实现在 infra）。

use async_trait::async_trait;

use super::entity::User;
use yarch_contract::page::{PageData, PageQuery};

#[async_trait]
pub trait UserRepository: Send + Sync {
    /// 分页列表（keyword 模糊过滤；D6 越界空页）
    async fn page(&self, q: &PageQuery, keyword: Option<&str>) -> Result<PageData<User>, String>;
    /// 按 id 查存活用户；None = 不存在或已逻辑删除
    async fn find(&self, id: i64) -> Result<Option<User>, String>;
    /// 创建；重名返回 errors::user_name_taken_message()
    async fn create(&self, name: &str) -> Result<User, String>;
    /// 逻辑删除（is_deleted = now()）；false = 不存在或已删
    async fn soft_delete(&self, id: i64) -> Result<bool, String>;
}
