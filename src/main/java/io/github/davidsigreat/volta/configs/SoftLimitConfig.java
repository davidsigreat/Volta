package io.github.davidsigreat.volta.configs;

/**
 * Position limits. At or past one, output that would push further is cut, but the mechanism can
 * always drive back. In position units. Infinite (off) by default.
 */
public class SoftLimitConfig {
    /** At or past this position, forward output is cut. */
    public double forward = Double.POSITIVE_INFINITY;
    /** At or past this position, reverse output is cut. */
    public double reverse = Double.NEGATIVE_INFINITY;

    /** Sets both limits and returns this config. */
    public SoftLimitConfig withLimits(double reverse, double forward) {
        this.reverse = reverse;
        this.forward = forward;
        return this;
    }
}
