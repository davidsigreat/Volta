package io.github.davidsigreat.volta.controls;

/**
 * What a VoltageMotor should do, passed to {@code motor.set(request)}.
 * <p>
 * Requests are small mutable objects. Make one per mechanism and change it with its
 * {@code with...} methods each loop, so nothing is allocated in the loop:
 * <pre>
 *   private final MotionMagicVoltage liftRequest = new MotionMagicVoltage(0);
 *
 *   lift.set(liftRequest.withPosition(28));
 * </pre>
 * The motor copies the values when set() is called, so changing a request afterwards does nothing
 * until it's set again.
 */
public abstract class ControlRequest {

    ControlRequest() {
    }

    /** Which control this asks for. */
    public abstract ControlMode getControlMode();

    /**
     * The request's main value: power for duty cycle, volts for voltage, velocity for velocity
     * controls, position for position controls, and 0 for neutral.
     */
    public abstract double getValue();

    /** Feedforward velocity for position controls, in velocity units. 0 by default. */
    public double getFeedforwardVelocity() {
        return 0;
    }

    /**
     * Acceleration in velocity units per second. Feedforward for VelocityVoltage, and the ramp
     * rate for MotionMagicVelocityVoltage. 0 by default.
     */
    public double getAcceleration() {
        return 0;
    }

    /** Which gain slot a closed loop request uses: 0, 1 or 2. Always 0 for open loop and neutral. */
    public int getSlot() {
        return 0;
    }
}
