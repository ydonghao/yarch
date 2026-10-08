//! 分页契约（契约内核）：页码通道（D6：越界返回空页）+ keyset 游标字段。
//!
//! 语义唯一权威 = contract/api/rest-response.md 分页负载 + rest-conventions.md 分页。
//! 页码通道由 sqlx 装配（yarch-axum persist）下推；keyset 通道为调用方 SQL 模式
//! （`WHERE id > $last LIMIT n`），nextCursor 由调用方生成。

use serde::{Deserialize, Serialize};

/// 分页查询参数。1-based；pageSize 1~100（rest-conventions D6）。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct PageQuery {
    pub page: i32,
    #[serde(rename = "pageSize")]
    pub page_size: i32,
}

impl PageQuery {
    /// 构造并校验（非法返回错误描述，供上层拼 1001 文案）。
    pub fn new(page: i32, page_size: i32) -> Result<Self, String> {
        if page < 1 {
            return Err("page 须为正整数".to_string());
        }
        if !(1..=100).contains(&page_size) {
            return Err("pageSize 须在 1~100".to_string());
        }
        Ok(Self { page, page_size })
    }

    /// 从查询参数解析：缺省 page=1、pageSize=20；非整数/越界返回错误描述。
    pub fn from_params(page: Option<&str>, page_size: Option<&str>) -> Result<Self, String> {
        let page = match page {
            None => 1,
            Some(raw) => raw
                .trim()
                .parse::<i32>()
                .map_err(|_| "page 须为整数".to_string())?,
        };
        let page_size = match page_size {
            None => 20,
            Some(raw) => raw
                .trim()
                .parse::<i32>()
                .map_err(|_| "pageSize 须为整数".to_string())?,
        };
        Self::new(page, page_size)
    }

    /// OFFSET（页码通道下推用）
    pub fn offset(&self) -> i64 {
        (self.page as i64 - 1) * self.page_size as i64
    }

    /// LIMIT
    pub fn limit(&self) -> i64 {
        self.page_size as i64
    }
}

/// 分页负载（rest-response.md：list 可空数组不得为 null；total 过滤后总数）。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct PageData<T> {
    pub list: Vec<T>,
    pub total: i64,
    pub page: i32,
    #[serde(rename = "pageSize")]
    pub page_size: i32,
    #[serde(rename = "nextCursor", skip_serializing_if = "Option::is_none")]
    pub next_cursor: Option<String>,
}

impl<T> PageData<T> {
    pub fn new(list: Vec<T>, total: i64, page: i32, page_size: i32) -> Self {
        Self {
            list,
            total,
            page,
            page_size,
            next_cursor: None,
        }
    }

    /// keyset 通道：设置下一页游标（空串按 None 处理——缺失或空串都表示没有下一页）
    pub fn with_next_cursor(mut self, cursor: impl Into<String>) -> Self {
        let cursor = cursor.into();
        self.next_cursor = if cursor.is_empty() {
            None
        } else {
            Some(cursor)
        };
        self
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn new_validates_d6_rules() {
        assert!(PageQuery::new(1, 20).is_ok());
        assert_eq!(PageQuery::new(0, 20).unwrap_err(), "page 须为正整数");
        assert_eq!(PageQuery::new(1, 0).unwrap_err(), "pageSize 须在 1~100");
        assert_eq!(PageQuery::new(1, 101).unwrap_err(), "pageSize 须在 1~100");
    }

    #[test]
    fn from_params_defaults_and_parses() {
        let q = PageQuery::from_params(None, None).unwrap();
        assert_eq!((q.page, q.page_size), (1, 20));
        let q = PageQuery::from_params(Some("3"), Some("10")).unwrap();
        assert_eq!(
            (q.page, q.page_size, q.offset(), q.limit()),
            (3, 10, 20, 10)
        );
        assert!(PageQuery::from_params(Some("x"), None).is_err());
        assert!(PageQuery::from_params(None, Some("999")).is_err());
    }

    #[test]
    fn page_data_serializes_camel_case_and_empty_list() {
        let pd = PageData::new(Vec::<i32>::new(), 5, 2, 2);
        let json = serde_json::to_string(&pd).unwrap();
        assert_eq!(json, r#"{"list":[],"total":5,"page":2,"pageSize":2}"#);
        let pd = PageData::new(vec![1, 2], 5, 1, 2).with_next_cursor("abc");
        let json = serde_json::to_string(&pd).unwrap();
        assert_eq!(
            json,
            r#"{"list":[1,2],"total":5,"page":1,"pageSize":2,"nextCursor":"abc"}"#
        );
        // 空串游标 = 无下一页（不序列化）
        let pd = PageData::new(vec![1], 1, 1, 1).with_next_cursor("");
        assert!(!serde_json::to_string(&pd).unwrap().contains("nextCursor"));
    }
}
