package io.github.davidsigreat.volta.controls;

/**
 * Moves to a position along a trapezoid profile, then holds it. The cruise velocity and
 * acceleration come from the motor configuration's motionMagic. The position can change
 * mid-move, and the profile carries on from where it is.
 * <pre>
 *   motor.set(new MotionMagicVoltage(28));
 * </pre>
 */
public class MotionMagicVoltage extends ControlRequest {
    /** Target position, in position units. */
    public double position;

    /** Gain slot: 0, 1 or 2. 0 by default. */
    public int slot;

    /** Target position, in position units. */
    public MotionMagicVoltage(double position) {
        this.position = position;
    }

    /** Sets position and returns this request, for {@code motor.set(request.withPosition(x))}. */
    public MotionMagicVoltage withPosition(double position) {
        this.position = position;
        return this;
    }

    @Override
    public ControlMode getControlMode() {
        return ControlMode.MOTION_MAGIC;
    }

    @Override
    public double getValue() {
        return position;
    }

    /** Picks the gain slot, 0, 1 or 2, and returns this request. */
    public MotionMagicVoltage withSlot(int slot) {
        this.slot = slot;
        return this;
    }

    @Override
    public int getSlot() {
        return slot;
    }
}
