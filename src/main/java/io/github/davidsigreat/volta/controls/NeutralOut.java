package io.github.davidsigreat.volta.controls;

/**
 * No output, coasting or braking as the motor configuration's neutralMode says.
 * <pre>
 *   motor.set(new NeutralOut());
 * </pre>
 */
public class NeutralOut extends ControlRequest {

    /** No output, coasting or braking as the motor configuration's neutralMode says. */
    public NeutralOut() {
    }

    @Override
    public ControlMode getControlMode() {
        return ControlMode.NEUTRAL;
    }

    @Override
    public double getValue() {
        return 0;
    }
}
