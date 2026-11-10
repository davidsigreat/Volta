package io.github.davidsigreat.volta.configs;

/** Neutral mode, voltage limits and write skipping. */
public class MotorOutputConfig {
    /** What NeutralOut does, and how the motors behave at zero output while running. BRAKE by default. */
    public NeutralMode neutralMode = NeutralMode.BRAKE;
    /** The most forward volts the motors get. */
    public double peakForwardVolts = Double.POSITIVE_INFINITY;
    /**
     * The most reverse volts the motors get, as a negative number. 0 for a flywheel that should
     * coast down, not be driven backwards.
     */
    public double peakReverseVolts = Double.NEGATIVE_INFINITY;
    /**
     * Power changes smaller than this aren't sent, since each write costs loop time. Stopping and
     * full power always go through.
     */
    public double powerDeadband = 0.002;

    /** Sets neutralMode and returns this config. */
    public MotorOutputConfig withNeutralMode(NeutralMode neutralMode) {
        this.neutralMode = neutralMode;
        return this;
    }

    /** Sets both peak voltages and returns this config. Reverse is negative. */
    public MotorOutputConfig withPeakVolts(double peakForwardVolts, double peakReverseVolts) {
        this.peakForwardVolts = peakForwardVolts;
        this.peakReverseVolts = peakReverseVolts;
        return this;
    }

    /** Sets powerDeadband and returns this config. */
    public MotorOutputConfig withPowerDeadband(double powerDeadband) {
        this.powerDeadband = powerDeadband;
        return this;
    }
}
