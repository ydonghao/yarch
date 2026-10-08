//! 业务错误码（3xxx 段，业务仓登记后方可使用；装配期 fail-fast）。

use yarch_contract::errcode;

const USER_NAME_TAKEN_KEY: &str = "USER_NAME_TAKEN";
const USER_NAME_TAKEN_MSG: &str = "用户名已存在";

/// 3001 的默认文案（仓储层以字符串回传业务错误，api 层映射回码）
pub fn user_name_taken_message() -> String {
    USER_NAME_TAKEN_MSG.to_string()
}

pub fn register_all() {
    errcode::register(3001, USER_NAME_TAKEN_KEY, USER_NAME_TAKEN_MSG, 409)
        .expect("errno 3001 登记失败（冲突=段位已占用）");
}
