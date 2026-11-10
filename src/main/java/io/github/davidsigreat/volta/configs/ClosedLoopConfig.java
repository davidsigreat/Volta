package io.github.davidsigreat.volta.configs;

/**
 * Closed loop settings shared by every slot: where an arm is level, the kS deadband, and limits on
 * the integral and derivative.
 */
public class ClosedLoopConfig {
    /**
     * With StaticSign.ERROR, no kS inside this error, so the output doesn't chatter at the target.
     * In position units.
     */
    public double staticDeadband = 0;
    /** ARM_COSINE gravity: the position where the arm is level. */
    public double armHorizontalPosition = 0;
    /** ARM_COSINE gravity: radians per unit of position. Math.PI / 180 for degrees. */
    public double positionToRadians = 1;
    /** Only integrates while the error is smaller than this, so a big move doesn't wind it up. */
    public double integralZone = Double.POSITIVE_INFINITY;
    /** The most volts the kI term can add. */
    public double maxIntegralVolts = 2;
    /** Smoothing on the error rate for kD, 0 to 1. 1 is no smoothing. Noisy sensors need less. */
    public double derivativeFilter = 1;

    /** Sets staticDeadband and returns this config. */
    public ClosedLoopConfig withStaticDeadband(double staticDeadband) {
        this.staticDeadband = staticDeadband;
        return this;
    }

    /**
     * Sets where the arm is level, for ARM_COSINE gravity, and returns this config.
     *
     * @param armHorizontalPosition the position where the arm is level
     * @param positionToRadians     radians per unit of position, Math.PI / 180 for degrees
     */
    public ClosedLoopConfig withArmHorizontal(double armHorizontalPosition, double positionToRadians) {
        this.armHorizontalPosition = armHorizontalPosition;
        this.positionToRadians = positionToRadians;
        return this;
    }

    /** Sets the integral zone and cap and returns this config. */
    public ClosedLoopConfig withIntegralLimits(double integralZone, double maxIntegralVolts) {
        this.integralZone = integralZone;
        this.maxIntegralVolts = maxIntegralVolts;
        return this;
    }

    /** Sets derivativeFilter and returns this config. */
    public ClosedLoopConfig withDerivativeFilter(double derivativeFilter) {
        this.derivativeFilter = derivativeFilter;
        return this;
    }
}
