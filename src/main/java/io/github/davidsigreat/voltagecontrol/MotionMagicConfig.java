package io.github.davidsigreat.voltagecontrol;

// Motion Magic limits, like Phoenix MotionMagicConfigs, in the mechanism's units: inches and inches
// per second for a lift, degrees and degrees per second for an arm.
//
// Fields are public and not final, so storing one in a static field of a Panels @Configurable or FTC Dashboard @Config class makes them editable live from
// Panels. Keep cruiseVelocity under what kV says the motors can reach on a low battery (about
// (10 V - kS) / kV), or the profile asks for more than the motors have and falls behind.
public class MotionMagicConfig {
    public double cruiseVelocity;
    public double acceleration;

    public MotionMagicConfig() {
    }

    public MotionMagicConfig(double cruiseVelocity, double acceleration) {
        this.cruiseVelocity = cruiseVelocity;
        this.acceleration = acceleration;
    }
}
