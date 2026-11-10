package io.github.davidsigreat.volta.configs;

/** How encoder ticks become position and velocity, in the units you choose. */
public class FeedbackConfig {
    /**
     * Encoder ticks to position units: position = ticks * positionPerTick. Inches per tick for a
     * lift, degrees per tick for an arm.
     */
    public double positionPerTick = 1;
    /**
     * Encoder ticks per second to velocity units: velocity = ticks per second *
     * velocityPerTickPerSecond. NaN (the default) uses positionPerTick, so velocity is position
     * units per second. For a flywheel in RPM, use 60 / ticksPerRev.
     */
    public double velocityPerTickPerSecond = Double.NaN;

    /** Sets positionPerTick and returns this config. */
    public FeedbackConfig withPositionPerTick(double positionPerTick) {
        this.positionPerTick = positionPerTick;
        return this;
    }

    /** Sets velocityPerTickPerSecond and returns this config. */
    public FeedbackConfig withVelocityPerTickPerSecond(double velocityPerTickPerSecond) {
        this.velocityPerTickPerSecond = velocityPerTickPerSecond;
        return this;
    }
}
