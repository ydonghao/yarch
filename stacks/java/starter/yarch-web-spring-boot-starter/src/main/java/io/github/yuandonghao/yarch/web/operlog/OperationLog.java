package io.github.yuandonghao.yarch.web.operlog;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 操作日志（G2）：切面记录动作/方法/耗时/成败，ndjson 输出 + 存储 SPI（入库归业务工程）。 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OperationLog {

    /** 动作标识，如 "order.place" */
    String action();
}
