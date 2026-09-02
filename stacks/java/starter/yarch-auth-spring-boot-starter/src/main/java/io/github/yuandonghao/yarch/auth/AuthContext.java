package io.github.yuandonghao.yarch.auth;

/** 当前请求主体（拦截器进出置清理；ThreadLocal 须随请求清理防串号） */
public final class AuthContext {

    private static final ThreadLocal<String> SUBJECT = new ThreadLocal<>();

    private AuthContext() {}

    static void set(String subject) {
        SUBJECT.set(subject);
    }

    static void clear() {
        SUBJECT.remove();
    }

    public static String currentSubject() {
        return SUBJECT.get();
    }
}
