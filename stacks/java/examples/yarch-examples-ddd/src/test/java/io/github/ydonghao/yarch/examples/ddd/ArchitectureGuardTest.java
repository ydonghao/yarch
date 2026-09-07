package io.github.ydonghao.yarch.examples.ddd;

import io.github.ydonghao.yarch.test.arch.YarchArchRules;
import org.junit.jupiter.api.Test;

class ArchitectureGuardTest {

    @Test
    void dddLayeringRulesHold() {
        YarchArchRules.checkDdd(Application.class);
    }
}
