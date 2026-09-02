package ${package};

import io.github.yuandonghao.yarch.test.arch.YarchArchRules;
import org.junit.jupiter.api.Test;

/** 分层依赖机检（J7）：DDD 依赖倒置——api/application 不碰 infrastructure，domain 零框架依赖 */
class ArchitectureGuardTest {

    @Test
    void dddLayeringRulesHold() {
        YarchArchRules.checkDdd(Application.class);
    }
}
