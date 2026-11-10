package io.github.davidsigreat.volta;

import io.github.davidsigreat.volta.configs.ClosedLoopConfig;
import io.github.davidsigreat.volta.configs.GravityType;
import io.github.davidsigreat.volta.configs.SlotConfig;
import io.github.davidsigreat.volta.configs.StaticSign;

/**
 * Feedforward plus PID feedback, in volts:
 * <pre>
 *   volts = kS * sign + kV * velocity + kA * acceleration + kG * gravity   (feedforward)
 *         + kP * error + kI * integral + kD * errorRate                    (feedback)
 * </pre>
 * Send the result through BatteryVoltage.toPower(), so the gains hold on any battery charge. Most
 * mechanisms are easier through VoltageMotor, which does that and runs this every loop.
 * <p>
 * Velocity control (flywheel): calculateVelocity(targetRpm, measuredRpm, targetRpmPerSecond, dt).
 * Position control (turret, arm, slide): calculatePosition(targetPosition, measuredPosition,
 * targetVelocity, targetAcceleration, dt). The velocity and acceleration come from a motion profile,
 * or are 0 to just hold a position.
 * <p>
 * The gains come from a SlotConfig and the shared settings from a ClosedLoopConfig. Both are read
 * every calculation, so they can be tuned live or swapped with setSlot().
 * <p>
 * Plain Java, so it can be tested without a robot. One controller per mechanism, since it keeps state.
 */
public class PIDSVAController {

    private SlotConfig slot;
    private ClosedLoopConfig closedLoop;

    private double integral = 0;
    private double lastError = 0;
    private double errorRate = 0;
    private boolean hasLastError = false;

    // Each term of the last calculate(), in volts, for telemetry and tuning.
    private double feedforwardVolts = 0;
    private double gravityVolts = 0;
    private double proportionalVolts = 0;
    private double integralVolts = 0;
    private double derivativeVolts = 0;

    /** A controller using these gains, with default closed loop settings. */
    public PIDSVAController(SlotConfig slot) {
        this(slot, new ClosedLoopConfig());
    }

    /** A controller using these gains and closed loop settings. */
    public PIDSVAController(SlotConfig slot, ClosedLoopConfig closedLoop) {
        this.slot = slot;
        this.closedLoop = closedLoop;
    }

    /** The gains in use. */
    public SlotConfig getSlot() {
        return slot;
    }

    /** Switches to other gains. Doesn't reset; call reset() too if the switch should start fresh. */
    public void setSlot(SlotConfig slot) {
        this.slot = slot;
    }

    /** The closed loop settings in use. */
    public ClosedLoopConfig getClosedLoop() {
        return closedLoop;
    }

    /** Switches to other closed loop settings. */
    public void setClosedLoop(ClosedLoopConfig closedLoop) {
        this.closedLoop = closedLoop;
    }

    /**
     * Velocity control: the target velocity is both the setpoint and the kV feedforward. For an arm
     * with ARM_COSINE gravity, use the version that takes the measured position.
     */
    public double calculateVelocity(double targetVelocity, double measuredVelocity,
                                    double targetAcceleration, double dt) {
        return calculate(targetVelocity, measuredVelocity, targetVelocity, targetAcceleration, 0, dt);
    }

    /** Velocity control with ARM_COSINE gravity: the same, plus the measured position for kG. */
    public double calculateVelocity(double targetVelocity, double measuredVelocity, double targetAcceleration,
                                    double measuredPosition, double dt) {
        return calculate(targetVelocity, measuredVelocity, targetVelocity, targetAcceleration,
                measuredPosition, dt);
    }

    /** Position control: velocity and acceleration are the profile's, for the feedforward. */
    public double calculatePosition(double targetPosition, double measuredPosition,
                                    double targetVelocity, double targetAcceleration, double dt) {
        return calculate(targetPosition, measuredPosition, targetVelocity, targetAcceleration,
                measuredPosition, dt);
    }

    /**
     * The general form: feedback on (target - measured), feedforward on the given velocity and
     * acceleration, and gravity at the measured position. dt is the seconds since the last call.
     * Returns volts.
     */
    public double calculate(double target, double measured, double velocity, double acceleration,
                            double position, double dt) {
        SlotConfig gains = slot;
        ClosedLoopConfig settings = closedLoop;
        double error = target - measured;

        double staticDirection;
        if (gains.staticSign == StaticSign.VELOCITY) {
            staticDirection = Math.signum(velocity);
        } else {
            staticDirection = Math.abs(error) > settings.staticDeadband ? Math.signum(error) : 0;
        }
        gravityVolts = gravity(position);
        feedforwardVolts = gains.kS * staticDirection + gains.kV * velocity + gains.kA * acceleration
                + gravityVolts;

        proportionalVolts = gains.kP * error;

        if (dt > 0) {
            if (Math.abs(error) < settings.integralZone) {
                integral += error * dt;
            }
            if (hasLastError) {
                double inverseDt = 1 / dt;
                errorRate += settings.derivativeFilter * ((error - lastError) * inverseDt - errorRate);
            }
        }
        // Keep the integral where kI can't add more than maxIntegralVolts, so it can't wind up.
        if (gains.kI != 0) {
            double maxIntegral = Math.abs(settings.maxIntegralVolts / gains.kI);
            integral = Math.max(-maxIntegral, Math.min(maxIntegral, integral));
        }
        integralVolts = gains.kI * integral;
        derivativeVolts = gains.kD * errorRate;

        lastError = error;
        hasLastError = true;
        return feedforwardVolts + proportionalVolts + integralVolts + derivativeVolts;
    }

    /** Volts to hold against gravity at a position. */
    public double gravity(double position) {
        if (slot.gravityType == GravityType.ARM_COSINE) {
            return slot.kG * Math.cos((position - closedLoop.armHorizontalPosition) * closedLoop.positionToRadians);
        }
        return slot.kG;
    }

    /** Just the feedforward, for running open loop on a known motion. */
    public double feedforward(double velocity, double acceleration, double position) {
        return slot.kS * Math.signum(velocity) + slot.kV * velocity + slot.kA * acceleration
                + gravity(position);
    }

    /**
     * Clears the integral and error history. Call when the mechanism stops or the target jumps, so
     * old error doesn't carry into the next move.
     */
    public void reset() {
        integral = 0;
        lastError = 0;
        errorRate = 0;
        hasLastError = false;
    }

    /** The feedforward of the last calculate(), including gravity, in volts. */
    public double getFeedforwardVolts() {
        return feedforwardVolts;
    }

    /** The kG term of the last calculate(), in volts. */
    public double getGravityVolts() {
        return gravityVolts;
    }

    /** The kP term of the last calculate(), in volts. */
    public double getProportionalVolts() {
        return proportionalVolts;
    }

    /** The kI term of the last calculate(), in volts. */
    public double getIntegralVolts() {
        return integralVolts;
    }

    /** The kD term of the last calculate(), in volts. */
    public double getDerivativeVolts() {
        return derivativeVolts;
    }

    /** Target minus measured, from the last calculate(). */
    public double getLastError() {
        return lastError;
    }
}
