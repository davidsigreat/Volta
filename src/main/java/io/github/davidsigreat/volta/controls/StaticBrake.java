package io.github.davidsigreat.volta.controls;

/**
 * No output, with the motor leads shorted so it resists turning.
 * <pre>
 *   motor.set(new StaticBrake());
 * </pre>
 */
public class StaticBrake extends ControlRequest {

    /** No output, with the motor leads shorted so it resists turning. */
    public StaticBrake() {
    }

    @Override
    public ControlMode getControlMode() {
        return ControlMode.BRAKE;
    }

    @Override
    public double getValue() {
        return 0;
    }
}
