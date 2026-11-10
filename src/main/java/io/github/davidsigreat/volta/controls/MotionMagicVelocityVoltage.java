package io.github.davidsigreat.volta.controls;

/**
 * Ramps to a velocity at a set acceleration, starting from the measured speed, then holds it. kA
 * gets the ramp rate. For flywheels and shooters.
 * <pre>
 *   motor.set(new MotionMagicVelocityVoltage(3200).withAcceleration(6000));
 * </pre>
 */
public class MotionMagicVelocityVoltage extends ControlRequest {
    /** Target velocity, in velocity units. */
    public double velocity;
    /**
     * Ramp rate, in velocity units per second. 0 (the default) uses the motor configuration's
     * motionMagic.acceleration.
     */
    public double acceleration;

    /** Gain slot: 0, 1 or 2. 0 by default. */
    public int slot;

    /** Target velocity, in velocity units. */
    public MotionMagicVelocityVoltage(double velocity) {
        this.velocity = velocity;
    }

    /** Sets the target velocity and returns this request. */
    public MotionMagicVelocityVoltage withVelocity(double velocity) {
        this.velocity = velocity;
        return this;
    }

    /** Sets the ramp rate and returns this request. 0 uses the configuration's. */
    public MotionMagicVelocityVoltage withAcceleration(double acceleration) {
        this.acceleration = acceleration;
        return this;
    }

    @Override
    public ControlMode getControlMode() {
        return ControlMode.MOTION_MAGIC_VELOCITY;
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
    public MotionMagicVelocityVoltage withSlot(int slot) {
        this.slot = slot;
        return this;
    }

    @Override
    public int getSlot() {
        return slot;
    }
}
