package io.github.davidsigreat.volta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.github.davidsigreat.volta.configs.GravityType;
import io.github.davidsigreat.volta.configs.SlotConfig;
import io.github.davidsigreat.volta.configs.StaticSign;

// Each test runs the controller against a simulated mechanism whose physics follow the same
// feedforward model: volts = kS * sign(v) + kV * v + kA * a (+ kG).
public class PIDSVAControllerTest {
    private static final double DT = 0.02;

    @Test
    public void feedforwardTermsAddUp() {
        PIDSVAController controller = new PIDSVAController(new SlotConfig(0, 0, 0, 0.5, 0.1, 0.01, 1.0));
        // kS * 1 + kV * 20 + kA * 100 + kG = 0.5 + 2 + 1 + 1
        assertEquals(4.5, controller.feedforward(20, 100, 0), 1e-12);
        assertEquals(-0.5 - 2 - 1 + 1, controller.feedforward(-20, -100, 0), 1e-12);
    }

    @Test
    public void flywheelRampsToTargetAndHolds() {
        double kS = 0.454, kV = 0.00216, kA = 0.00212, battery = 12.8;
        PIDSVAController controller = new PIDSVAController(new SlotConfig(0.00681, 0, 0.0000288, kS, kV, kA));
        controller.getClosedLoop().derivativeFilter = 0.3;
        SlewRateLimiter ramp = new SlewRateLimiter(2702, 0);
        double rpm = 0;
        for (int i = 0; i < 150; i++) {
            double setpoint = ramp.calculate(3000, DT);
            double volts = Math.max(controller.calculateVelocity(setpoint, rpm, ramp.getRate(), DT), 0);
            double applied = Math.min(volts, battery);
            rpm += DT * (applied - kS - kV * rpm) / kA;
        }
        assertEquals(3000, rpm, 2);
    }

    @Test
    public void integralRemovesSteadyErrorOnAWeakerWheel() {
        double kS = 0.454, kV = 0.00216, kA = 0.00212;
        PIDSVAController pOnly = new PIDSVAController(new SlotConfig(0.00681, 0, 0, kS, kV, kA));
        PIDSVAController withI = new PIDSVAController(new SlotConfig(0.00681, 0.01, 0, kS, kV, kA));
        double a = 3000, b = 3000;
        for (int i = 0; i < 500; i++) {
            // The real wheel needs 8% more volts per RPM than the gains say.
            a += DT * (pOnly.calculateVelocity(3000, a, 0, DT) - kS - 1.08 * kV * a) / kA;
            b += DT * (withI.calculateVelocity(3000, b, 0, DT) - kS - 1.08 * kV * b) / kA;
        }
        assertTrue("P only should settle low, was " + a, a < 2960);
        assertEquals(3000, b, 1);
        assertTrue(Math.abs(withI.getIntegralVolts()) <= withI.getClosedLoop().maxIntegralVolts);
    }

    @Test
    public void integralIsCappedAtMaxIntegralVolts() {
        PIDSVAController controller = new PIDSVAController(new SlotConfig(0, 1, 0, 0, 0, 0));
        controller.getClosedLoop().maxIntegralVolts = 2;
        for (int i = 0; i < 1000; i++) {
            controller.calculatePosition(100, 0, 0, 0, DT);
        }
        assertEquals(2, controller.getIntegralVolts(), 1e-9);
    }

    @Test
    public void motionMagicLiftReachesGoalWithoutOvershootAndHoldsWithKG() {
        double kS = 0.3, kV = 0.2, kA = 0.02, kG = 1.1;
        PIDSVAController controller = new PIDSVAController(new SlotConfig(1.5, 0, 0.02, kS, kV, kA, kG));
        TrapezoidProfile.State setpoint = new TrapezoidProfile.State(0, 0);
        TrapezoidProfile.State goal = new TrapezoidProfile.State(30, 0);
        double position = 0, velocity = 0, overshoot = 0, volts = 0;
        for (int i = 0; i < 150; i++) {
            TrapezoidProfile.step(DT, setpoint, goal, 40, 80, setpoint);
            volts = controller.calculatePosition(setpoint.position, position, setpoint.velocity,
                    setpoint.acceleration, DT);
            volts = Math.max(-12, Math.min(12, volts));
            double acceleration = (volts - kS * Math.signum(velocity) - kV * velocity - kG) / kA;
            velocity += acceleration * DT;
            position += velocity * DT;
            overshoot = Math.max(overshoot, position - 30);
        }
        assertEquals(30, position, 0.05);
        assertTrue("overshoot " + overshoot, overshoot < 0.05);
        assertEquals(kG, volts, 0.05);
    }

    @Test
    public void armCosineGravity() {
        PIDSVAController arm = new PIDSVAController(new SlotConfig(0, 0, 0, 0, 0, 0, 1.0));
        arm.getSlot().gravityType = GravityType.ARM_COSINE;
        arm.getClosedLoop().positionToRadians = Math.PI / 180;
        assertEquals(1.0, arm.gravity(0), 1e-12);
        assertEquals(0.5, arm.gravity(60), 1e-12);
        assertEquals(0.0, arm.gravity(90), 1e-12);
        assertEquals(-1.0, arm.gravity(180), 1e-12);

        arm.getClosedLoop().armHorizontalPosition = 30;
        assertEquals(1.0, arm.gravity(30), 1e-12);
    }

    @Test
    public void errorSignStaticPushesTowardTargetOutsideDeadband() {
        PIDSVAController hold = new PIDSVAController(new SlotConfig(0.1, 0, 0, 0.5, 0, 0));
        hold.getSlot().staticSign = StaticSign.ERROR;
        hold.getClosedLoop().staticDeadband = 0.5;
        assertEquals(1.5, hold.calculatePosition(10, 0, 0, 0, DT), 1e-12);
        assertEquals(-1.5, hold.calculatePosition(-10, 0, 0, 0, DT), 1e-12);
        assertEquals(0.02, hold.calculatePosition(0.2, 0, 0, 0, DT), 1e-12);
    }

    @Test
    public void resetClearsHistory() {
        PIDSVAController controller = new PIDSVAController(new SlotConfig(0, 1, 1, 0, 0, 0));
        controller.calculatePosition(10, 0, 0, 0, DT);
        controller.calculatePosition(20, 0, 0, 0, DT);
        controller.reset();
        controller.calculatePosition(5, 0, 0, 0, DT);
        // No derivative on the first call after a reset, and the integral starts again from 0.
        assertEquals(0, controller.getDerivativeVolts(), 0);
        assertEquals(5 * DT, controller.getIntegralVolts(), 1e-12);
    }
}
