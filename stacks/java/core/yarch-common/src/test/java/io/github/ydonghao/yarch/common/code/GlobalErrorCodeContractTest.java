package io.github.ydonghao.yarch.common.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 错误码契约守护：13 码全表唯一权威 = contract/dist/error-codes.json（由 error-codes.md 派生， CI 拒双向漂移）——四栈读同一份 json
 * 断言，不再各养手抄表（P1 契约机器可读出口）。 dist 文件不在场（消费方独立测试环境）则跳过表断言，CI 仓内必跑。
 */
class GlobalErrorCodeContractTest {

    private static final Path DIST = Path.of("../../../../contract/dist/error-codes.json");

    /** code, identifier, defaultMessage, http —— 运行期从 dist json 装载 */
    private static List<Object[]> loadContractTable() throws Exception {
        assumeTrue(Files.exists(DIST), "contract dist json 不在场，跳过同源断言");
        JsonNode codes = new ObjectMapper().readTree(Files.readString(DIST)).path("codes");
        List<Object[]> table = new ArrayList<>();
        for (JsonNode c : codes) {
            table.add(
                    new Object[] {
                        c.path("code").asInt(),
                        c.path("key").asText(),
                        c.path("message").asText(),
                        c.path("http").asInt()
                    });
        }
        return table;
    }

    @Test
    void enumMatchesContractTable() throws Exception {
        List<Object[]> contractTable = loadContractTable();
        assertEquals(13, contractTable.size(), "dist 码表条数异常");
        assertEquals(contractTable.size(), GlobalErrorCode.values().length, "码表条数与契约不一致");
        for (Object[] row : contractTable) {
            int code = (int) row[0];
            GlobalErrorCode e =
                    GlobalErrorCode.ofCode(code)
                            .orElseThrow(() -> new AssertionError("契约码 " + code + " 在枚举中缺失"));
            assertEquals(row[1], e.identifier(), code + " 标识符漂移");
            assertEquals(row[2], e.defaultMessage(), code + " 默认文案漂移");
            assertEquals(row[3], e.httpStatus(), code + " HTTP 映射漂移");
        }
    }

    @Test
    void yarchOnlyOwnsReservedSegments() {
        for (GlobalErrorCode e : GlobalErrorCode.values()) {
            int c = e.code();
            boolean inYarchSegment = c == 0 || (c >= 1000 && c <= 2999);
            assert inYarchSegment : "yarch 只拥有 0/1xxx/2xxx，越界码 " + c;
        }
    }
}
