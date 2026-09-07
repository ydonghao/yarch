package io.github.ydonghao.yarch.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 角色要求（任意命中即通过）；无 token/无效 → 2001/2002，角色不足 → 2003 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequireRoles {

    String[] value();
}
