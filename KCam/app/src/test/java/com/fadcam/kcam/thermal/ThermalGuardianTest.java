package com.fadcam.kcam.thermal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ThermalGuardianTest {
    @Test
    public void decide() {
        assertEquals(0, ThermalGuardian.decide(35f, 0, 43, 48));
        assertEquals(1, ThermalGuardian.decide(43f, 0, 43, 48));
        assertEquals(2, ThermalGuardian.decide(48.5f, 0, 43, 48));
        assertEquals(1, ThermalGuardian.decide(30f, 3, 43, 48)); // system says severe
        assertEquals(2, ThermalGuardian.decide(30f, 4, 43, 48)); // system says critical
        assertEquals(0, ThermalGuardian.decide(Float.NaN, -1, 43, 48));
    }
}
