package io.github.davidsigreat.volta.controls;

/**
 * Holds a velocity, stepping straight to it. The velocity is also the kV feedforward. For a
 * mechanism that's already near speed; use MotionMagicVelocityVoltage to ramp.
 * <pre>
 *   motor.set(new VelocityVoltage(3200));
 * </pre>
 */
public class VelocityVoltage extends ControlRequest {
    /** Target velocity, in velocity units. */
    public double velocity;
    /** Acceleration for the kA feedforward, in velocity units per second. Usually 0. */
    public double acceleration;

    /** Gain slot: 0, 1 or 2. 0 by default. */
    public int slot;

    /** Target velocity, in velocity units. */
    public VelocityVoltage(double velocity) {
        this.velocity = velocity;
    }

    /** Sets the target velocity and returns this request. */
    public VelocityVoltage withVelocity(double velocity) {
        this.velocity = velocity;
        return this;
    }

    /** Sets the kA feedforward acceleration and returns this request. */
    public VelocityVoltage withAcceleration(double acceleration) {
        this.acceleration = acceleration;
        return this;
    }

    @Override
    public ControlMode getControlMode() {
        return ControlMode.VELOCITY;
    }

    @Override
    public double getValue() {
        return velocity;
    }

    @Override
    public double getAcceleration() {
        return acceleration;
    }

    /** Picks the gain slot, 0, 1 or 2, and returns this request. */
    public VelocityVoltage withSlot(int slot) {
        this.slot = slot;
        return this;
    }

    @Override
    public int getSlot() {
        return slot;
    }
}
