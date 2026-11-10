package io.github.davidsigreat.volta.configs;

/**
 * Motion Magic limits, in the mechanism's units: inches per second and inches per second squared
 * for a lift, degrees per second and degrees per second squared for an arm.
 * <p>
 * Keep cruiseVelocity under what kV says the motors can reach on a low battery, about
 * (10 V - kS - kG) / kV. Otherwise the profile asks for more than the motors have and falls behind.
 */
public class MotionMagicConfig {
    /** The fastest the profile moves, in position units per second. */
    public double cruiseVelocity;
    /**
     * How fast the profile speeds up and slows down, in position units per second squared. Also
     * the default ramp rate for MotionMagicVelocityVoltage, in velocity units per second.
     */
    public double acceleration;

    /** Both limits 0, which makes MotionMagicVoltage jump straight to the goal. */
    public MotionMagicConfig() {
    }

    /** Cruise velocity and acceleration, in position units per second and per second squared. */
    public MotionMagicConfig(double cruiseVelocity, double acceleration) {
        this.cruiseVelocity = cruiseVelocity;
        this.acceleration = acceleration;
    }

    /** Sets cruiseVelocity and returns this config. */
    public MotionMagicConfig withCruiseVelocity(double cruiseVelocity) {
        this.cruiseVelocity = cruiseVelocity;
        return this;
    }

    /** Sets acceleration and returns this config. */
    public MotionMagicConfig withAcceleration(double acceleration) {
        this.acceleration = acceleration;
        return this;
    }
}
