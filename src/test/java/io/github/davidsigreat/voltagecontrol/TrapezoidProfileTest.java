package io.github.davidsigreat.voltagecontrol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TrapezoidProfileTest {
    private static final double DT = 0.02;

    // Steps until finished, returning the number of steps. Tracks the peak speed in peak[0].
    private static int run(TrapezoidProfile.State setpoint, TrapezoidProfile.State goal,
                           double maxVelocity, double maxAcceleration, double[] peak) {
        int steps = 0;
        while (!TrapezoidProfile.isFinished(setpoint, goal) && steps < 10000) {
            TrapezoidProfile.step(DT, setpoint, goal, maxVelocity, maxAcceleration, setpoint);
            peak[0] = Math.max(peak[0], Math.abs(setpoint.velocity));
            steps++;
        }
        return steps;
    }

    @Test
    public void longMoveCruisesAndEndsExactlyOnGoal() {
        // 0 -> 30 at 40/s cruise and 80/s^2: 0.5 s up, 0.25 s cruise, 0.5 s down = 1.25 s.
        TrapezoidProfile.State setpoint = new TrapezoidProfile.State(0, 0);
        TrapezoidProfile.State goal = new TrapezoidProfile.State(30, 0);
        double[] peak = {0};
        int steps = run(setpoint, goal, 40, 80, peak);
        assertEquals(1.25, steps * DT, 2 * DT);
        assertEquals(40, peak[0], 1e-9);
        assertEquals(30, setpoint.position, 0);
        assertEquals(0, setpoint.velocity, 0);
    }

    @Test
    public void shortMoveMakesATriangle() {
        // 0 -> 5 never reaches 40/s: peak is sqrt(5 * 80) = 20/s, done in 0.5 s.
        TrapezoidProfile.State setpoint = new TrapezoidProfile.State(0, 0);
        TrapezoidProfile.State goal = new TrapezoidProfile.State(5, 0);
        double[] peak = {0};
        int steps = run(setpoint, goal, 40, 80, peak);
        assertEquals(0.5, steps * DT, 2 * DT);
        assertTrue("peak " + peak[0], peak[0] < 20 + 1e-9 && peak[0] > 18);
        assertEquals(5, setpoint.position, 0);
    }

    @Test
    public void negativeMoveMirrorsPositive() {
        TrapezoidProfile.State setpoint = new TrapezoidProfile.State(0, 0);
        TrapezoidProfile.State goal = new TrapezoidProfile.State(-30, 0);
        double[] peak = {0};
        int steps = run(setpoint, goal, 40, 80, peak);
        assertEquals(1.25, steps * DT, 2 * DT);
        assertEquals(-30, setpoint.position, 0);
    }

    @Test
    public void goalChangeMidMoveNeverExceedsAcceleration() {
        TrapezoidProfile.State setpoint = new TrapezoidProfile.State(0, 0);
        TrapezoidProfile.State goal = new TrapezoidProfile.State(30, 0);
        double lastVelocity = 0;
        double worstAcceleration = 0;
        for (int i = 0; i < 300; i++) {
            if (i == 20) {
                goal.position = -10;
            }
            TrapezoidProfile.step(DT, setpoint, goal, 40, 80, setpoint);
            worstAcceleration = Math.max(worstAcceleration, Math.abs(setpoint.velocity - lastVelocity) / DT);
            lastVelocity = setpoint.velocity;
        }
        assertEquals(-10, setpoint.position, 0);
        assertTrue("worst acceleration " + worstAcceleration, worstAcceleration <= 80 + 1e-6);
    }

    @Test
    public void noLimitsJumpsStraightToGoal() {
        TrapezoidProfile.State setpoint = new TrapezoidProfile.State(0, 0);
        TrapezoidProfile.State goal = new TrapezoidProfile.State(12, 0);
        TrapezoidProfile.step(DT, setpoint, goal, 0, 0, setpoint);
        assertEquals(12, setpoint.position, 0);
    }
}
