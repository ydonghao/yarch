package io.github.ydonghao.yarch.examples.simple;

import io.github.ydonghao.yarch.test.arch.YarchArchRules;
import org.junit.jupiter.api.Test;

class ArchitectureGuardTest {

    @Test
    void simpleLayeringRulesHold() {
        YarchArchRules.checkSimple(Application.class);
    }
}
