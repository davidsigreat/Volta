package io.github.davidsigreat.volta;

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
 * Plain Java, so it can be tested without a robot. One controller per mechanism, since it keeps state.
 */
public class PIDSVAController {

    /**
     * Which way kS pushes.
     */
    public enum StaticSign {
        /**
         * The direction of the target velocity. For velocity control and
         * profiled motion, where friction opposes the commanded motion.
         */
        VELOCITY,
        /**
         * The direction of the error. For holding a position with no
         * profile, so kS helps the last bit of error break static friction.
         */
        ERROR
    }

    /**
     * How kG applies.
     */
    public enum GravityType {
        /**
         * The same everywhere: a lift or slide that gravity pulls equally at any height.
         */
        ELEVATOR,
        /**
         * kG times the cosine of the arm's angle from horizontal: full when level, none when straight
         * up or down. Set armHorizontalPosition and positionToRadians to match the arm.
         */
        ARM_COSINE
    }

    private final PIDSVAGains gains;

    // Settings. Public so they can be set once after construction.

    /** Which way kS pushes. VELOCITY by default. */
    public StaticSign staticSign = StaticSign.VELOCITY;
    /**
     * With StaticSign.ERROR, no kS inside this error, so the output doesn't chatter at the target.
     */
    public double staticDeadband = 0;
    /**
     * Only integrates while the error is smaller than this, so a big move doesn't wind it up.
     */
    public double integralZone = Double.POSITIVE_INFINITY;
    /**
     * The most volts the kI term can add, so it can't wind up past what's useful.
     */
    public double maxIntegralVolts = 2;
    /**
     * Smoothing on the error rate for kD, 0 to 1. 1 is no smoothing. Noisy sensors need less.
     */
    public double derivativeFilter = 1;
    /** How kG applies. ELEVATOR (constant) by default. */
    public GravityType gravityType = GravityType.ELEVATOR;
    /**
     * ARM_COSINE only: the position where the arm is level, and what one unit of position is in
     * radians (Math.PI / 180 for degrees, 2 * Math.PI for revolutions).
     */
    public double armHorizontalPosition = 0;
    /** ARM_COSINE only: radians per unit of position. */
    public double positionToRadians = 1;

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

    /** A controller that reads these gains every time it calculates. */
    public PIDSVAController(PIDSVAGains gains) {
        this.gains = gains;
    }

    /** The gains this controller reads. */
    public PIDSVAGains getGains() {
        return gains;
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

    /**
     * Position control: velocity and acceleration are the profile's, for the feedforward.
     */
    public double calculatePosition(double targetPosition, double measuredPosition,
                                    double targetVelocity, double targetAcceleration, double dt) {
        return calculate(targetPosition, measuredPosition, targetVelocity, targetAcceleration,
                measuredPosition, dt);
    }

    /**
     * The general form: feedback on (target - measured), feedforward on the given velocity and
     * acceleration, and gravity at the measured position. dt is the seconds since the last call.
     */
    public double calculate(double target, double measured, double velocity, double acceleration,
                            double position, double dt) {
        double error = target - measured;

        double staticDirection;
        if (staticSign == StaticSign.VELOCITY) {
            staticDirection = Math.signum(velocity);
        } else {
            staticDirection = Math.abs(error) > staticDeadband ? Math.signum(error) : 0;
        }
        gravityVolts = gravity(position);
        feedforwardVolts = gains.kS * staticDirection + gains.kV * velocity + gains.kA * acceleration
                + gravityVolts;

        proportionalVolts = gains.kP * error;

        if (dt > 0) {
            if (Math.abs(error) < integralZone) {
                integral += error * dt;
            }
            if (hasLastError) {
                double inverseDt = 1 / dt;
                errorRate += derivativeFilter * ((error - lastError) * inverseDt - errorRate);
            }
        }
        // Keep the integral where kI can't add more than maxIntegralVolts, so it can't wind up.
        if (gains.kI != 0) {
            double maxIntegral = Math.abs(maxIntegralVolts / gains.kI);
            integral = Math.max(-maxIntegral, Math.min(maxIntegral, integral));
        }
        integralVolts = gains.kI * integral;
        derivativeVolts = gains.kD * errorRate;

        lastError = error;
        hasLastError = true;
        return feedforwardVolts + proportionalVolts + integralVolts + derivativeVolts;
    }

    /**
     * Volts to hold against gravity at a position.
     */
    public double gravity(double position) {
        if (gravityType == GravityType.ARM_COSINE) {
            return gains.kG * Math.cos((position - armHorizontalPosition) * positionToRadians);
        }
        return gains.kG;
    }

    /**
     * Just the feedforward, for running open loop on a known motion.
     */
    public double feedforward(double velocity, double acceleration, double position) {
        return gains.kS * Math.signum(velocity) + gains.kV * velocity + gains.kA * acceleration
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

    /**
     * Includes gravity.
     */
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
