package io.github.yuandonghao.yarch.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.yuandonghao.yarch.common.web.PageData;
import io.github.yuandonghao.yarch.persistence.support.PageDatas;
import io.github.yuandonghao.yarch.test.ContractAsserts;
import io.github.yuandonghao.yarch.test.containers.PgTestDb;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** PG 规约 ORM 条文（postgresql.md 五 G1-G10）的行为级验收：真实容器、真实 SQL */
@SpringBootTest(classes = PersistenceTestApp.class)
class PersistenceContractTest {

    /** DynamicPropertySource 先于 @BeforeAll 执行：容器必须在静态初始化器中就绪 */
    static final PgTestDb pg = PgTestDb.dockerAvailable() ? PgTestDb.start() : null;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (pg != null) {
            pg.register(registry);
        }
    }

    @BeforeAll
    static void requireDocker() {
        assumeTrue(pg != null, "本机无 Docker，跳过 PG 契约测试");
    }

    @Autowired PersistenceTestApp.TestUserMapper mapper;

    @Test
    void insertAssignsIdentityIdAndAuditColumns() {
        PersistenceTestApp.TestUserPO user = new PersistenceTestApp.TestUserPO("alice");
        mapper.insert(user);

        assertNotNull(user.getId(), "identity 主键应回填");
        assertNotNull(user.getCreatedAt(), "created_at 审计填充（G7）");
        assertNotNull(user.getUpdatedAt(), "updated_at 审计填充（G7）");
        assertEquals(Boolean.FALSE, user.getDeleted());
    }

    @Test
    void partialUpdateKeepsOtherColumnsAndBumpsUpdatedAt() throws InterruptedException {
        PersistenceTestApp.TestUserPO user = new PersistenceTestApp.TestUserPO("bob");
        mapper.insert(user);
        Instant created = user.getCreatedAt();

        Thread.sleep(10); // 保证时间推进
        PersistenceTestApp.TestUserPO patch = new PersistenceTestApp.TestUserPO();
        patch.setId(user.getId());
        patch.setName("bobby");
        int changed = mapper.updateById(patch); // G8：部分更新（null 列不参与 set）

        assertEquals(1, changed);
        PersistenceTestApp.TestUserPO reloaded = mapper.selectById(user.getId());
        assertEquals("bobby", reloaded.getName());
        assertEquals(created, reloaded.getCreatedAt(), "未涉及列不得被覆盖");
        assertTrue(reloaded.getUpdatedAt().isAfter(created), "updated_at 应自动推进");
    }

    @Test
    void logicDeleteHidesRowButKeepsItInTable() throws Exception {
        PersistenceTestApp.TestUserPO user = new PersistenceTestApp.TestUserPO("carol");
        mapper.insert(user);
        mapper.deleteById(user.getId());

        assertNull(mapper.selectById(user.getId()), "逻辑删除后常规查询不可见");

        try (Connection conn =
                        DriverManager.getConnection(
                                pg.jdbcUrl(), PgTestDb.USER, PgTestDb.PASSWORD);
                var ps =
                        conn.prepareStatement(
                                "select count(*) from test_users where id = ? and is_deleted ="
                                        + " true")) {
            ps.setLong(1, user.getId());
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next() && rs.getLong(1) == 1, "行必须仍在表中（禁物理 delete）");
            }
        }
    }

    @Test
    void paginationPushesDownAndBeyondLastPageReturnsEmptyListWithRealTotal() {
        for (int i = 0; i < 25; i++) {
            mapper.insert(new PersistenceTestApp.TestUserPO("u" + i));
        }
        long total = mapper.selectCount(Wrappers.emptyWrapper());
        assertTrue(total >= 25);

        PageData<PersistenceTestApp.TestUserPO> page1 =
                PageDatas.of(mapper.selectPage(PageDatas.mpPage(1, 20), Wrappers.emptyWrapper()));
        ContractAsserts.assertPageDataShape(page1);
        assertEquals(20, page1.getList().size());

        // D6：越界页 200 + 空 list + 真实 total（不报错不纠错）
        PageData<PersistenceTestApp.TestUserPO> beyond =
                PageDatas.of(
                        mapper.selectPage(PageDatas.mpPage(9_999, 20), Wrappers.emptyWrapper()));
        assertTrue(beyond.getList().isEmpty());
        assertEquals(total, beyond.getTotal());
    }

    @Test
    void keysetCursorReturnsOnlyNextWindow() {
        for (int i = 0; i < 5; i++) {
            mapper.insert(new PersistenceTestApp.TestUserPO("k" + i));
        }
        List<Long> firstWindow =
                mapper
                        .selectList(
                                Wrappers.<PersistenceTestApp.TestUserPO>query()
                                        .orderByAsc("id")
                                        .last("limit 2"))
                        .stream()
                        .map(PersistenceTestApp.TestUserPO::getId)
                        .toList();
        assertEquals(2, firstWindow.size());

        Long cursor = firstWindow.get(1);
        PageData<PersistenceTestApp.TestUserPO> next =
                PageDatas.ofKeyset(
                        mapper.selectList(
                                Wrappers.<PersistenceTestApp.TestUserPO>query()
                                        .gt("id", cursor)
                                        .orderByAsc("id")
                                        .last("limit 2")),
                        2,
                        po -> String.valueOf(po.getId()));

        assertTrue(next.getList().size() <= 2);
        assertTrue(next.getList().stream().allMatch(po -> po.getId() > cursor), "游标窗口必须全部大于游标");
        assertNotNull(next.getNextCursor());
    }

    @Test
    void partialIndexOnEmailUniqueSemantics() throws Exception {
        // 唯一约束与逻辑删除共存（postgresql.md 二-2）：迁移里建的是 uk_..._live 部分唯一索引
        try (Connection conn =
                        DriverManager.getConnection(
                                pg.jdbcUrl(), PgTestDb.USER, PgTestDb.PASSWORD);
                var ps =
                        conn.prepareStatement(
                                "select count(*) from pg_indexes where indexname ="
                                        + " 'uk_test_users_name_live'")) {
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next() && rs.getLong(1) == 1, "部分唯一索引必须存在（迁移交付）");
            }
        }
    }
}
