package io.github.ydonghao.yarch.persistence;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import io.github.ydonghao.yarch.persistence.entity.AuditMetaObjectHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** PG 持久化契约件装配：分页下推（G5，limit/offset 进数据库）+ 全表更新/删除阻断（防线）。 MySQL 档是独立 starter（J4：一技术一模）。 */
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class YarchPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor yarchMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditMetaObjectHandler yarchAuditMetaObjectHandler() {
        return new AuditMetaObjectHandler();
    }
}
