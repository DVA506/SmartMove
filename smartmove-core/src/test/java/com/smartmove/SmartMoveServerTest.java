package com.smartmove;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.Test;

class SmartMoveServerTest {

    @Test
    void main_runsWithoutCrashing() {
        assertDoesNotThrow(() -> {
            SmartMoveServer.main(new String[] {});
        });
    }
}