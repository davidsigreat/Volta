package io.github.davidsigreat.volta;

/**
 * Motion Magic limits, in the mechanism's units: inches and inches
 * per second for a lift, degrees and degrees per second for an arm.
 * <p>
 * Fields are public and not final, so storing one in a static field of a Panels @Configurable or FTC Dashboard @Config class makes them editable live from
 * Panels. Keep cruiseVelocity under what kV says the motors can reach on a low battery (about
 * (10 V - kS) / kV), or the profile asks for more than the motors have and falls behind.
 */
public class MotionMagicConfig {
    /** The fastest the profile moves, in position units per second. */
    public double cruiseVelocity;
    /** How fast the profile speeds up and slows down, in position units per second per second. */
    public double acceleration;

    /** Both limits 0, which makes setMotionMagic jump straight to the goal. */
    public MotionMagicConfig() {
    }

    /** Cruise velocity and acceleration, in position units per second and per second squared. */
    public MotionMagicConfig(double cruiseVelocity, double acceleration) {
        this.cruiseVelocity = cruiseVelocity;
        this.acceleration = acceleration;
    }
}
