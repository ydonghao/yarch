//! 同源断言：内建码表的唯一机器可读权威 = contract/dist/error-codes.json
//! （由 contract/api/error-codes.md 派生，contract-dist CI 拒双向漂移）。
//! dist 不在场（消费方独立测试环境）则跳过；仓内 CI 必跑（P1 契约机器可读出口口径，
//! 与 golang/java/python/web 四栈 conformance 同表）。

use serde::Deserialize;
use yarch_contract::errcode;

#[derive(Deserialize)]
struct DistRow {
    code: i32,
    key: String,
    message: String,
    http: u16,
}

#[derive(Deserialize)]
struct DistSuccess {
    code: i32,
    message: String,
}

#[derive(Deserialize)]
struct Dist {
    success: DistSuccess,
    codes: Vec<DistRow>,
}

fn load_dist() -> Option<Dist> {
    let raw = std::fs::read_to_string("../../../../contract/dist/error-codes.json").ok()?;
    Some(serde_json::from_str(&raw).expect("dist json 解析失败"))
}

#[test]
fn ok_matches_dist_success() {
    let Some(dist) = load_dist() else {
        println!("contract dist json 不在场，跳过同源断言");
        return;
    };
    assert_eq!(
        (errcode::OK.code, errcode::OK.message),
        (dist.success.code, dist.success.message.as_str())
    );
}

#[test]
fn builtin_table_matches_dist_codes() {
    let Some(dist) = load_dist() else {
        println!("contract dist json 不在场，跳过同源断言");
        return;
    };
    // 双向对表（不硬编码码数——加码只改 md + dist + 内建表三处，本测试零改）：
    // dist 每行都在内建表中且元数据全等；内建表除 OK 外无多余行
    let builtin_rows = errcode::BUILTIN.iter().filter(|c| c.code != 0).count();
    assert_eq!(
        dist.codes.len(),
        builtin_rows,
        "内建码表与 dist 行数不一致（漂移）"
    );
    for row in &dist.codes {
        let c = errcode::lookup(row.code);
        assert!(
            errcode::is_registered(row.code),
            "code {} 在内建表中缺失",
            row.code
        );
        assert_eq!(
            (c.code, c.key, c.message, c.http),
            (row.code, row.key.as_str(), row.message.as_str(), row.http),
            "code {} 元数据与 dist 不一致",
            row.code
        );
    }
}
