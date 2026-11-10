package io.github.davidsigreat.voltagecontrol;

// Gains for PIDSVAController, in volts, like a Phoenix 6 slot config. Units depend on what's
// controlled: for a flywheel in RPM, kV is volts per RPM; for an arm in degrees, kP is volts per degree.
//   kP: volts per unit of error
//   kI: volts per unit of error, per second it lasts
//   kD: volts per unit of error, per unit of error change per second
//   kS: volts to overcome friction, applied in the direction of motion
//   kV: volts per unit of velocity, to hold a speed
//   kA: volts per unit of acceleration, to change speed
//   kG: volts to hold against gravity. Constant for an elevator, times the cosine of the angle for an
//       arm (see PIDSVAController.GravityType)
//
// Fields are public and not final, so storing one in a static field of a Panels @Configurable or FTC Dashboard @Config class makes every gain editable live
// from Panels: the controller reads them each time it calculates.
public class PIDSVAGains {
    public double kP;
    public double kI;
    public double kD;
    public double kS;
    public double kV;
    public double kA;
    public double kG;

    public PIDSVAGains() {
    }

    public PIDSVAGains(double kP, double kI, double kD, double kS, double kV, double kA) {
        this(kP, kI, kD, kS, kV, kA, 0);
    }

    public PIDSVAGains(double kP, double kI, double kD, double kS, double kV, double kA, double kG) {
        this.kP = kP;
        this.kI = kI;
        this.kD = kD;
        this.kS = kS;
        this.kV = kV;
        this.kA = kA;
        this.kG = kG;
    }
}
