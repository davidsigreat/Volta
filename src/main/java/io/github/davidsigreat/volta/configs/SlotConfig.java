package io.github.davidsigreat.volta.configs;

/**
 * One set of PIDSVA+G gains, in volts, with the gravity type and kS sign that go with them.
 * <p>
 * A motor has three slots (slot0, slot1, slot2 in VoltageMotorConfig), and each closed loop
 * request picks one with {@code withSlot(n)}. Use them for a mechanism that behaves differently in
 * different situations: a lift carrying a game piece or empty, a flywheel spinning up or holding,
 * or a turret making big moves or tracking.
 * <p>
 * Units depend on what's controlled: for a flywheel in RPM, kV is volts per RPM; for an arm in
 * degrees, kP is volts per degree.
 * <p>
 * Fields are public and read every loop, so a slot in a static field of a Panels
 * {@code @Configurable} or FTC Dashboard {@code @Config} class can be tuned live.
 */
public class SlotConfig {
    /** Volts per unit of error. */
    public double kP;
    /** Volts per unit of error, per second it lasts. */
    public double kI;
    /** Volts per unit of error change per second. */
    public double kD;
    /** Volts to overcome friction, in the direction staticSign says. */
    public double kS;
    /** Volts per unit of velocity, to hold a speed. */
    public double kV;
    /** Volts per unit of acceleration, to change speed. */
    public double kA;
    /** Volts to hold against gravity, applied as gravityType says. */
    public double kG;
    /** How kG applies. ELEVATOR (constant) by default. */
    public GravityType gravityType = GravityType.ELEVATOR;
    /** Which way kS pushes. VELOCITY by default. */
    public StaticSign staticSign = StaticSign.VELOCITY;

    /** All gains 0. */
    public SlotConfig() {
    }

    /** Gains with no kG. */
    public SlotConfig(double kP, double kI, double kD, double kS, double kV, double kA) {
        this(kP, kI, kD, kS, kV, kA, 0);
    }

    /** Every gain, kG last. */
    public SlotConfig(double kP, double kI, double kD, double kS, double kV, double kA, double kG) {
        this.kP = kP;
        this.kI = kI;
        this.kD = kD;
        this.kS = kS;
        this.kV = kV;
        this.kA = kA;
        this.kG = kG;
    }

    /** Sets kP and returns this slot. */
    public SlotConfig withKP(double kP) {
        this.kP = kP;
        return this;
    }

    /** Sets kI and returns this slot. */
    public SlotConfig withKI(double kI) {
        this.kI = kI;
        return this;
    }

    /** Sets kD and returns this slot. */
    public SlotConfig withKD(double kD) {
        this.kD = kD;
        return this;
    }

    /** Sets kS and returns this slot. */
    public SlotConfig withKS(double kS) {
        this.kS = kS;
        return this;
    }

    /** Sets kV and returns this slot. */
    public SlotConfig withKV(double kV) {
        this.kV = kV;
        return this;
    }

    /** Sets kA and returns this slot. */
    public SlotConfig withKA(double kA) {
        this.kA = kA;
        return this;
    }

    /** Sets kG and returns this slot. */
    public SlotConfig withKG(double kG) {
        this.kG = kG;
        return this;
    }

    /** Sets gravityType and returns this slot. */
    public SlotConfig withGravityType(GravityType gravityType) {
        this.gravityType = gravityType;
        return this;
    }

    /** Sets staticSign and returns this slot. */
    public SlotConfig withStaticSign(StaticSign staticSign) {
        this.staticSign = staticSign;
        return this;
    }
}
