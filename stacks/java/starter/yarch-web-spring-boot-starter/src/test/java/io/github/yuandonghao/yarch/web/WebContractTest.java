package io.github.yuandonghao.yarch.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** web 契约断言：错误矩阵（异常→码/HTTP 固定映射）、信封形状、分页参数校验→1001、springdoc 可用。 与 contract/api 四件套 v1.0 一致性在此守护。 */
@SpringBootTest(classes = TestApp.class)
@AutoConfigureMockMvc
class WebContractTest {

    @Autowired MockMvc mvc;

    @Test
    void successEnvelopeShape() throws Exception {
        mvc.perform(
                        post("/api/v1/items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"a\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("成功"))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void invalidArgumentForBlankName() throws Exception {
        mvc.perform(
                        post("/api/v1/items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(
                        jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("参数校验失败：")));
    }

    @Test
    void malformedBodyIs1002() throws Exception {
        mvc.perform(
                        post("/api/v1/items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1002));
    }

    @Test
    void unknownPathIs404With1004() throws Exception {
        mvc.perform(get("/api/v1/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    void methodNotAllowedCollapsedTo404() throws Exception {
        mvc.perform(post("/api/v1/items/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    void unexpectedErrorIs1000WithFixedMessage() throws Exception {
        mvc.perform(get("/api/v1/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(1000))
                .andExpect(jsonPath("$.message").value("内部错误"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void businessNotFoundIs1004() throws Exception {
        mvc.perform(get("/api/v1/items/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1004))
                .andExpect(jsonPath("$.message").value("资源不存在：item 99999"));
    }

    @Test
    void pageParamViolationsAre1001() throws Exception {
        mvc.perform(get("/api/v1/items").param("pageSize", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
        mvc.perform(get("/api/v1/items").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void pageBeyondLastReturnsEmptyListWithRealTotal() throws Exception {
        // D6：page 超出总页数 → 200 + code=0 + 空 list + 真实 total（不报错不纠错）
        mvc.perform(get("/api/v1/items").param("page", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.list").isArray())
                .andExpect(jsonPath("$.data.list").isEmpty())
                .andExpect(jsonPath("$.data.total").isNumber());
    }

    @Test
    void springdocAvailableOnBoot4() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
