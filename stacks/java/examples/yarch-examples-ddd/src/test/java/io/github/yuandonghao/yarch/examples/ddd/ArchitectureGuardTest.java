package io.github.yuandonghao.yarch.examples.ddd;

import io.github.yuandonghao.yarch.test.arch.YarchArchRules;
import org.junit.jupiter.api.Test;

class ArchitectureGuardTest {

    @Test
    void dddLayeringRulesHold() {
        YarchArchRules.checkDdd(Application.class);
    }
}
