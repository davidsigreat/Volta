package io.github.davidsigreat.volta.controls;

/**
 * A fraction of the battery, -1 to 1, with no voltage compensation. Peak volts and soft limits
 * still apply. For matching old setPower() code and quick tests.
 * <pre>
 *   motor.set(new DutyCycleOut(0.5));
 * </pre>
 */
public class DutyCycleOut extends ControlRequest {
    /** Power, -1 to 1. */
    public double output;

    /** Power, -1 to 1. */
    public DutyCycleOut(double output) {
        this.output = output;
    }

    /** Sets output and returns this request, for {@code motor.set(request.withOutput(x))}. */
    public DutyCycleOut withOutput(double output) {
        this.output = output;
        return this;
    }

    @Override
    public ControlMode getControlMode() {
        return ControlMode.DUTY_CYCLE;
    }

    @Override
    public double getValue() {
        return output;
    }
}
