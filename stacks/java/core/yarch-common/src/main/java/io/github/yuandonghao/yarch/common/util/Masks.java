package io.github.yuandonghao.yarch.common.util;

/** 敏感数据脱敏（安全规约：展示脱敏，139****1219 口径）——纯函数，DTO 出口处使用 */
public final class Masks {

    private Masks() {}

    /** 手机号：保留前 3 后 4 */
    public static String phone(String value) {
        return mask(value, 3, 4);
    }

    /** 邮箱：本地部保留首字符 + *** + @域名 */
    public static String email(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int at = value.indexOf('@');
        if (at <= 0) {
            return full(value);
        }
        return value.charAt(0) + "***" + value.substring(at);
    }

    /** 证件号：保留前 3 后 4 */
    public static String idCard(String value) {
        return mask(value, 3, 4);
    }

    /** 全掩码 */
    public static String full(String value) {
        return value == null ? "" : "*".repeat(Math.min(value.length(), 8));
    }

    private static String mask(String value, int keepHead, int keepTail) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= keepHead + keepTail) {
            return full(value);
        }
        return value.substring(0, keepHead) + "****" + value.substring(value.length() - keepTail);
    }
}
