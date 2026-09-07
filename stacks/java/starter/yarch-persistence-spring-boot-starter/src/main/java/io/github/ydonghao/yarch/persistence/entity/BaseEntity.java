package io.github.ydonghao.yarch.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import java.time.Instant;

/**
 * PG 规约「每表必备四列」的 Java 侧锚点（postgresql.md 二-1）： id identity 主键 / created_at / updated_at /
 * is_deleted。
 *
 * <p>三层布尔映射链（postgresql.md 五-2，显式声明不依赖隐式推断）： DB 列 {@code is_deleted} ↔ 语言属性 {@code deleted}（无前缀）↔
 * JSON {@code deleted}。
 */
public abstract class BaseEntity {

    /** PG identity 主键：插入不写 id，回填生成值 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /** G7：由 MetaObjectHandler 自动填充，应用代码不显式赋值 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    /** 逻辑删除标记（禁物理 delete，审计与追溯） */
    @TableLogic(value = "false", delval = "true")
    @TableField(value = "is_deleted")
    private Boolean deleted = Boolean.FALSE;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }
}
