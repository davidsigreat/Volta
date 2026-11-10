package io.github.davidsigreat.volta.controls;

/** Which control a {@link ControlRequest} asks for. */
public enum ControlMode {
    /** No output, using the configured neutral mode. */
    NEUTRAL,
    /** No output, spinning freely. */
    COAST,
    /** No output, motor leads shorted so it resists turning. */
    BRAKE,
    /** A fraction of the battery, -1 to 1. */
    DUTY_CYCLE,
    /** Open loop volts. */
    VOLTAGE,
    /** Closed loop velocity. */
    VELOCITY,
    /** Closed loop velocity, ramped at a set acceleration. */
    MOTION_MAGIC_VELOCITY,
    /** Closed loop position. */
    POSITION,
    /** Closed loop position along a trapezoid profile. */
    MOTION_MAGIC
}
