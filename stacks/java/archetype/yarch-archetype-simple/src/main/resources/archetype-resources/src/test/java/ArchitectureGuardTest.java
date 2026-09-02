package ${package};

import io.github.yuandonghao.yarch.test.arch.YarchArchRules;
import org.junit.jupiter.api.Test;

/** 分层纪律机检（J7）：经典单向——controller 不碰 dao，service 不反向依赖 controller */
class ArchitectureGuardTest {

    @Test
    void simpleLayeringRulesHold() {
        YarchArchRules.checkSimple(Application.class);
    }
}
