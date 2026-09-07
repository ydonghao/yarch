package io.github.ydonghao.yarch.common.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AssertsTest {

    @Test
    void failuresThrowBusinessExceptionWithErrorCode() {
        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> Asserts.notNull(null, GlobalErrorCode.NOT_FOUND, "user 1"));
        assertEquals(GlobalErrorCode.NOT_FOUND, ex.getErrorCode());

        assertThrows(
                BusinessException.class,
                () -> Asserts.notBlank(" ", GlobalErrorCode.INVALID_ARGUMENT, "email"));
        assertThrows(
                BusinessException.class,
                () -> Asserts.isTrue(1 > 2, GlobalErrorCode.CONFLICT, "stock"));
    }

    @Test
    void passingConditionsDoNothing() {
        Asserts.notNull(new Object(), GlobalErrorCode.NOT_FOUND, "x");
        Asserts.notBlank("ok", GlobalErrorCode.INVALID_ARGUMENT, "x");
        Asserts.isTrue(true, GlobalErrorCode.CONFLICT, "x");
    }
}
