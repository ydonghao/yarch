package io.github.yuandonghao.yarch.examples.simple;

import io.github.yuandonghao.yarch.test.arch.YarchArchRules;
import org.junit.jupiter.api.Test;

class ArchitectureGuardTest {

    @Test
    void simpleLayeringRulesHold() {
        YarchArchRules.checkSimple(Application.class);
    }
}
