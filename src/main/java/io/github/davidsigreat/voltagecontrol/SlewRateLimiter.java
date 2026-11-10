package io.github.davidsigreat.voltagecontrol;

// Moves a value toward a target no faster than maxRate per second. For ramping a setpoint, so a
// controller gets a target it can follow, and kA gets the ramp's rate to act on.
//
// Plain Java, so it can be tested without a robot.
public class SlewRateLimiter {
    // Units per second. Public so it can be changed live.
    public double maxRate;

    private double value;
    private double rate = 0;

    public SlewRateLimiter(double maxRate, double startValue) {
        this.maxRate = maxRate;
        this.value = startValue;
    }

    // Steps toward target and returns the new value. dt is the seconds since the last call.
    public double calculate(double target, double dt) {
        double maxChange = maxRate * dt;
        double change = Math.max(-maxChange, Math.min(maxChange, target - value));
        value += change;
        rate = dt > 0 ? change * (1 / dt) : 0;
        return value;
    }

    // Jumps straight to a value, with no rate. For starting a ramp from where the mechanism really is.
    public void reset(double newValue) {
        value = newValue;
        rate = 0;
    }

    public double getValue() {
        return value;
    }

    // How fast the value moved on the last step, per second. 0 once it reaches the target.
    public double getRate() {
        return rate;
    }
}
