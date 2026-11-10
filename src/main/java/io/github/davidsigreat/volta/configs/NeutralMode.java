package io.github.davidsigreat.volta.configs;

/** What the motors do with no output. */
public enum NeutralMode {
    /** Spin freely. For flywheels and anything that should coast down. */
    COAST,
    /** Short the motor leads so the motor resists turning. For lifts, arms and turrets. */
    BRAKE
}
