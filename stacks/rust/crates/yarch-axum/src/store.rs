//! 幂等/限流存储端口（spec 五-3：Redis 经 yarch-axum 封装；此处先立端口与进程内实现，
//! Redis 实现触发式——首个需要跨实例幂等/限流的服务立项时）。
//! 语义对齐 python redix.IdempotencyStore / FixedWindowLimiter。

use std::collections::HashMap;
use std::sync::Mutex;
use std::time::{Duration, Instant};

/// 幂等 acquire 三态 + mismatch（对齐 python redix 口径）。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AcquireState {
    /// 首次占位成功，执行业务
    Acquired,
    /// 同 digest 已有 done，可回放
    Replay,
    /// 同 key 异 digest，拒绝（1007）
    Mismatch,
    /// 同 digest 占位中（并发请求）
    Pending,
}

pub trait IdempotencyStore: Send + Sync + 'static {
    fn acquire(&self, key: &str, digest: &str) -> AcquireState;
    fn load(&self, key: &str) -> Option<(u16, String)>;
    fn store_response(&self, key: &str, status: u16, body: String);
    fn release(&self, key: &str);
}

/// 进程内幂等存储（测试与单实例档；无 TTL——key 生命周期随进程）。
#[derive(Default)]
pub struct InMemoryIdempotencyStore {
    entries: Mutex<HashMap<String, Entry>>,
}

#[derive(Clone)]
struct Entry {
    digest: String,
    status: Option<u16>,
    body: Option<String>,
}

impl InMemoryIdempotencyStore {
    pub fn new() -> Self {
        Self::default()
    }
}

impl IdempotencyStore for InMemoryIdempotencyStore {
    fn acquire(&self, key: &str, digest: &str) -> AcquireState {
        let mut entries = self.entries.lock().expect("idempotency store poisoned");
        match entries.get(key) {
            None => {
                entries.insert(
                    key.to_string(),
                    Entry {
                        digest: digest.to_string(),
                        status: None,
                        body: None,
                    },
                );
                AcquireState::Acquired
            }
            Some(e) if e.digest != digest => AcquireState::Mismatch,
            Some(e) if e.status.is_some() => AcquireState::Replay,
            Some(_) => AcquireState::Pending,
        }
    }

    fn load(&self, key: &str) -> Option<(u16, String)> {
        self.entries
            .lock()
            .expect("idempotency store poisoned")
            .get(key)
            .and_then(|e| e.status.map(|s| (s, e.body.clone().unwrap_or_default())))
    }

    fn store_response(&self, key: &str, status: u16, body: String) {
        if let Some(e) = self
            .entries
            .lock()
            .expect("idempotency store poisoned")
            .get_mut(key)
        {
            e.status = Some(status);
            e.body = Some(body);
        }
    }

    fn release(&self, key: &str) {
        self.entries
            .lock()
            .expect("idempotency store poisoned")
            .remove(key);
    }
}

pub trait RateLimiter: Send + Sync + 'static {
    /// 固定窗口计数：命中且未超限返回 true。
    fn hit(&self, key: &str, window: Duration, limit: u64) -> bool;
}

/// 进程内固定窗口限流（窗口起点 = 首次命中时刻）。
#[derive(Default)]
pub struct InMemoryRateLimiter {
    windows: Mutex<HashMap<String, (Instant, u64)>>,
}

impl InMemoryRateLimiter {
    pub fn new() -> Self {
        Self::default()
    }
}

impl RateLimiter for InMemoryRateLimiter {
    fn hit(&self, key: &str, window: Duration, limit: u64) -> bool {
        let mut windows = self.windows.lock().expect("rate limiter poisoned");
        let now = Instant::now();
        let entry = windows.entry(key.to_string()).or_insert((now, 0));
        if now.duration_since(entry.0) >= window {
            *entry = (now, 1);
            return true;
        }
        entry.1 += 1;
        entry.1 <= limit
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn idempotency_acquire_replay_mismatch_pending() {
        let store = InMemoryIdempotencyStore::new();
        assert_eq!(store.acquire("k", "d1"), AcquireState::Acquired);
        assert_eq!(store.acquire("k", "d1"), AcquireState::Pending); // 未 done
        assert_eq!(store.acquire("k", "d2"), AcquireState::Mismatch);
        store.store_response("k", 201, "{\"code\":0}".to_string());
        assert_eq!(store.acquire("k", "d1"), AcquireState::Replay);
        assert_eq!(store.load("k"), Some((201, "{\"code\":0}".to_string())));
        store.release("k");
        assert_eq!(store.load("k"), None);
    }

    #[test]
    fn fixed_window_limits_burst() {
        let limiter = InMemoryRateLimiter::new();
        for i in 0..3 {
            assert!(
                limiter.hit("rl", Duration::from_secs(60), 3),
                "第 {} 次应放行",
                i + 1
            );
        }
        assert!(
            !limiter.hit("rl", Duration::from_secs(60), 3),
            "第 4 次应拒绝"
        );
    }

    #[test]
    fn fixed_window_resets_after_window() {
        let limiter = InMemoryRateLimiter::new();
        let tiny = Duration::from_millis(5);
        assert!(limiter.hit("k", tiny, 1));
        assert!(!limiter.hit("k", tiny, 1));
        std::thread::sleep(Duration::from_millis(10));
        assert!(limiter.hit("k", tiny, 1), "窗口过后应重置");
    }
}
