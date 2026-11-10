package io.github.davidsigreat.volta;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SlewRateLimiterTest {

    @Test
    public void climbsAtMaxRateThenStops() {
        SlewRateLimiter limiter = new SlewRateLimiter(100, 0);
        assertEquals(2, limiter.calculate(10, 0.02), 1e-12);
        assertEquals(100, limiter.getRate(), 1e-9);
        for (int i = 0; i < 10; i++) {
            limiter.calculate(10, 0.02);
        }
        assertEquals(10, limiter.getValue(), 0);
        assertEquals(0, limiter.getRate(), 0);
    }

    @Test
    public void fallsAtMaxRate() {
        SlewRateLimiter limiter = new SlewRateLimiter(100, 10);
        assertEquals(8, limiter.calculate(0, 0.02), 1e-12);
        assertEquals(-100, limiter.getRate(), 1e-9);
    }

    @Test
    public void resetJumpsWithNoRate() {
        SlewRateLimiter limiter = new SlewRateLimiter(100, 0);
        limiter.calculate(10, 0.02);
        limiter.reset(50);
        assertEquals(50, limiter.getValue(), 0);
        assertEquals(0, limiter.getRate(), 0);
    }

    @Test
    public void zeroDtGivesNoRate() {
        SlewRateLimiter limiter = new SlewRateLimiter(100, 0);
        limiter.calculate(10, 0);
        assertEquals(0, limiter.getValue(), 0);
        assertEquals(0, limiter.getRate(), 0);
    }
}
