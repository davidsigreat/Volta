package io.github.davidsigreat.volta.controls;

/**
 * No output, spinning freely. For letting a flywheel spin down.
 * <pre>
 *   motor.set(new CoastOut());
 * </pre>
 */
public class CoastOut extends ControlRequest {

    /** No output, spinning freely. */
    public CoastOut() {
    }

    @Override
    public ControlMode getControlMode() {
        return ControlMode.COAST;
    }

    @Override
    public double getValue() {
        return 0;
    }
}
