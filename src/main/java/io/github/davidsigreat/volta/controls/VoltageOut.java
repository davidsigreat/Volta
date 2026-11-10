package io.github.davidsigreat.volta.controls;

/**
 * Open loop volts, the same on any battery charge. For intakes, driving by stick, and finding
 * kS and kV.
 * <pre>
 *   motor.set(new VoltageOut(6));
 * </pre>
 */
public class VoltageOut extends ControlRequest {
    /** Volts. */
    public double output;

    /** Volts. */
    public VoltageOut(double output) {
        this.output = output;
    }

    /** Sets output and returns this request, for {@code motor.set(request.withOutput(x))}. */
    public VoltageOut withOutput(double output) {
        this.output = output;
        return this;
    }

    @Override
    public ControlMode getControlMode() {
        return ControlMode.VOLTAGE;
    }

    @Override
    public double getValue() {
        return output;
    }
}
