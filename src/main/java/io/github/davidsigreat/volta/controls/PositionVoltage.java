package io.github.davidsigreat.volta.controls;

/**
 * Holds a position, stepping straight to it. For small moves and holding still; use
 * MotionMagicVoltage for big moves.
 * <p>
 * When tracking a moving target, like a turret following a goal while the robot drives, set the
 * target's velocity too. It goes into the kV feedforward, so the mechanism keeps up instead of
 * lagging behind.
 * <pre>
 *   motor.set(new PositionVoltage(90));
 *   motor.set(turretRequest.withPosition(angle).withVelocity(angleRate));
 * </pre>
 */
public class PositionVoltage extends ControlRequest {
    /** Target position, in position units. */
    public double position;
    /** Feedforward velocity, in velocity units. 0 to just hold. */
    public double velocity;

    /** Gain slot: 0, 1 or 2. 0 by default. */
    public int slot;

    /** Target position, in position units. */
    public PositionVoltage(double position) {
        this.position = position;
    }

    /** Sets the target position and returns this request. */
    public PositionVoltage withPosition(double position) {
        this.position = position;
        return this;
    }

    /** Sets the feedforward velocity and returns this request. */
    public PositionVoltage withVelocity(double velocity) {
        this.velocity = velocity;
        return this;
    }

    @Override
    public ControlMode getControlMode() {
        return ControlMode.POSITION;
    }

    @Override
    public double getValue() {
        return position;
    }

    @Override
    public double getFeedforwardVelocity() {
        return velocity;
    }

    /** Picks the gain slot, 0, 1 or 2, and returns this request. */
    public PositionVoltage withSlot(int slot) {
        this.slot = slot;
        return this;
    }

    @Override
    public int getSlot() {
        return slot;
    }
}
