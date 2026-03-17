package com.smartmove.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class SmartMoveOperationExceptionTest {

    @Test
    void constructorSetsMessageAndCause() {
        Throwable cause = new RuntimeException("root cause");

        SmartMoveOperationException ex =
                new SmartMoveOperationException("operation failed", cause);

        assertEquals("operation failed", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void isRuntimeException() {
        SmartMoveOperationException ex =
                new SmartMoveOperationException("msg", new Exception("cause"));

        assertTrue(ex instanceof RuntimeException);
    }
}