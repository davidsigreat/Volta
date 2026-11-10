package io.github.davidsigreat.volta;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;

/**
 * One mechanism's motors and the control that drives them. Pick a control with a set method, and
 * call update() once per loop to run it:
 * <pre>
 *   setDutyCycle(power)                              fraction of the battery
 *   setVoltage(volts)                                open loop volts
 *   setVelocity(velocity)                            hold a velocity
 *   setMotionMagicVelocity(velocity, acceleration)   ramp to a velocity
 *   setPosition(position)                            hold a position
 *   setMotionMagic(position)                         profiled move, limits from motionMagic
 *   neutral() / coast() / brake()                    no output
 * </pre>
 * Every motor passed in after the first follows it.
 * <p>
 * Set methods only choose the control, so call them as often as you like; update() does the work.
 * The first motor is the leader: its encoder is the one read, so it must be the one with an encoder
 * cable. Followers get the same power. Set each motor's direction before passing it in, so they all
 * turn the mechanism the same way.
 * <p>
 * Closed loop control runs here in the OpMode loop, in volts through BatteryVoltage, using
 * PIDSVAController. Turn on bulk caching (MANUAL, cleared each loop) so the encoder reads are free.
 */
public class VoltageMotor {

    /** The control a VoltageMotor is running, one per set method. */
    public enum Mode {
        NEUTRAL, DUTY_CYCLE, VOLTAGE, VELOCITY, MOTION_MAGIC_VELOCITY, POSITION, MOTION_MAGIC
    }

    private final DcMotorEx[] motors;
    private final BatteryVoltage battery;
    private final PIDSVAController controller;
    private long lastUpdateNanos = System.nanoTime();

    // Settings. Public so they can be set once after construction, or live each loop.

    /**
     * Encoder ticks to position units: position = ticks * positionPerTick. Inches per tick for a
     * lift, degrees per tick for an arm.
     */
    public double positionPerTick = 1;
    /**
     * Encoder ticks per second to velocity units: velocity = ticks per second *
     * velocityPerTickPerSecond. NaN (the default) uses positionPerTick, so velocity is position
     * units per second. For a flywheel in RPM, use 60 / ticksPerRev.
     */
    public double velocityPerTickPerSecond = Double.NaN;
    /**
     * What neutral() does: BRAKE or FLOAT.
     */
    public DcMotor.ZeroPowerBehavior neutralMode = DcMotor.ZeroPowerBehavior.BRAKE;
    /** The most forward volts the motors get. */
    public double peakForwardVolts = Double.POSITIVE_INFINITY;
    /**
     * The most reverse volts the motors get, as a negative number. 0 for a flywheel that should
     * coast down, not be driven backwards.
     */
    public double peakReverseVolts = Double.NEGATIVE_INFINITY;
    /** At or past this position, forward output is cut. In position units. */
    public double forwardSoftLimit = Double.POSITIVE_INFINITY;
    /** At or past this position, reverse output is cut. In position units. */
    public double reverseSoftLimit = Double.NEGATIVE_INFINITY;
    /**
     * Power changes smaller than this aren't sent, since each write costs loop time. Stopping and
     * full power always go through.
     */
    public double powerDeadband = 0.002;
    /**
     * The longest loop time the control will use, in seconds. The first loop's includes all of init.
     */
    public double maxLoopSeconds = 0.1;
    /**
     * Motion Magic limits. Point it at one in a Panels @Configurable or FTC Dashboard @Config class to tune it live.
     */
    public MotionMagicConfig motionMagic = new MotionMagicConfig();

    private Mode mode = Mode.NEUTRAL;
    private boolean modeChanged = true;
    private DcMotor.ZeroPowerBehavior neutralRequest = null;
    private DcMotor.ZeroPowerBehavior appliedZeroPower = null;
    private double demand = 0;
    private double target = 0;
    private double rampAcceleration = 0;

    private final SlewRateLimiter velocityRamp = new SlewRateLimiter(0, 0);
    private final TrapezoidProfile.State profileSetpoint = new TrapezoidProfile.State();
    private final TrapezoidProfile.State profileGoal = new TrapezoidProfile.State();

    private double positionOffset = 0;
    private double position = 0;
    private double velocity = 0;
    private double volts = 0;
    private double power = 0;

    /**
     * Makes a mechanism from its motors. Puts every motor in RUN_WITHOUT_ENCODER, since the loop
     * runs here; the encoder still reads.
     *
     * @param battery   the battery reading to compensate with; can be shared between mechanisms
     * @param gains     the gains, read every loop, so a Panels or Dashboard field can be tuned live
     * @param leader    the motor whose encoder is read
     * @param followers motors that get the same power as the leader
     */
    public VoltageMotor(BatteryVoltage battery, PIDSVAGains gains, DcMotorEx leader, DcMotorEx... followers) {
        this.battery = battery;
        this.controller = new PIDSVAController(gains);
        motors = new DcMotorEx[followers.length + 1];
        motors[0] = leader;
        System.arraycopy(followers, 0, motors, 1, followers.length);
        for (DcMotorEx motor : motors) {
            // The loop runs here, so the hub's own loop stays off. The encoder still reads.
            motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        }
    }

    // ---- Controls ----

    /**
     * DutyCycleOut: a fraction of the battery, -1 to 1. No voltage compensation.
     */
    public void setDutyCycle(double newPower) {
        select(Mode.DUTY_CYCLE);
        demand = newPower;
    }

    /**
     * VoltageOut: open loop volts, the same on any battery.
     */
    public void setVoltage(double newVolts) {
        select(Mode.VOLTAGE);
        demand = newVolts;
    }

    /**
     * VelocityVoltage: holds a velocity, jumping straight to it.
     */
    public void setVelocity(double targetVelocity) {
        select(Mode.VELOCITY);
        target = targetVelocity;
    }

    /**
     * MotionMagicVelocityVoltage: ramps to a velocity at the given acceleration (units per second
     * per second), starting from the real speed, with kA covering the ramp.
     */
    public void setMotionMagicVelocity(double targetVelocity, double acceleration) {
        select(Mode.MOTION_MAGIC_VELOCITY);
        target = targetVelocity;
        rampAcceleration = acceleration;
    }

    /**
     * PositionVoltage: holds a position, jumping straight to it.
     */
    public void setPosition(double targetPosition) {
        select(Mode.POSITION);
        target = targetPosition;
    }

    /**
     * MotionMagicVoltage: moves to a position along a trapezoid profile with motionMagic's limits.
     * The goal can change mid-move.
     */
    public void setMotionMagic(double targetPosition) {
        select(Mode.MOTION_MAGIC);
        target = targetPosition;
    }

    /**
     * NeutralOut: no output, coasting or braking as neutralMode says.
     */
    public void neutral() {
        select(Mode.NEUTRAL);
        neutralRequest = null;
    }

    /**
     * CoastOut: no output, spinning freely.
     */
    public void coast() {
        select(Mode.NEUTRAL);
        neutralRequest = DcMotor.ZeroPowerBehavior.FLOAT;
    }

    /**
     * StaticBrake: no output, motor leads shorted so it resists turning.
     */
    public void brake() {
        select(Mode.NEUTRAL);
        neutralRequest = DcMotor.ZeroPowerBehavior.BRAKE;
    }

    private void select(Mode newMode) {
        if (newMode != mode) {
            mode = newMode;
            modeChanged = true;
        }
    }

    // ---- Loop ----

    /**
     * Call once per loop: reads the encoder, runs the chosen control, and writes the motors.
     */
    public void update() {
        long now = System.nanoTime();
        double dt = Math.min((now - lastUpdateNanos) * 1e-9, maxLoopSeconds);
        lastUpdateNanos = now;
        readEncoder();

        if (modeChanged) {
            // Start fresh from where the mechanism really is, with no leftover error.
            modeChanged = false;
            controller.reset();
            velocityRamp.reset(velocity);
            profileSetpoint.set(position, velocity, 0);
        }

        switch (mode) {
            case DUTY_CYCLE:
                setZeroPower(neutralMode);
                volts = demand * battery.get();
                writePower(limitPower(demand));
                return;
            case VOLTAGE:
                outputVolts(demand);
                return;
            case VELOCITY:
                outputVolts(controller.calculate(target, velocity, target, 0, position, dt));
                return;
            case MOTION_MAGIC_VELOCITY: {
                velocityRamp.maxRate = rampAcceleration;
                double setpoint = velocityRamp.calculate(target, dt);
                outputVolts(controller.calculate(setpoint, velocity, setpoint, velocityRamp.getRate(), position, dt));
                return;
            }
            case POSITION:
                outputVolts(controller.calculate(target, position, 0, 0, position, dt));
                return;
            case MOTION_MAGIC:
                profileGoal.set(target, 0, 0);
                TrapezoidProfile.step(dt, profileSetpoint, profileGoal,
                        motionMagic.cruiseVelocity, motionMagic.acceleration, profileSetpoint);
                outputVolts(controller.calculate(profileSetpoint.position, position,
                        profileSetpoint.velocity, profileSetpoint.acceleration, position, dt));
                return;
            default:
                setZeroPower(neutralRequest != null ? neutralRequest : neutralMode);
                volts = 0;
                writePower(0);
        }
    }

    private void readEncoder() {
        DcMotorEx leader = motors[0];
        position = leader.getCurrentPosition() * positionPerTick + positionOffset;
        double velocityScale = Double.isNaN(velocityPerTickPerSecond) ? positionPerTick : velocityPerTickPerSecond;
        velocity = leader.getVelocity() * velocityScale;
    }

    private void outputVolts(double requested) {
        setZeroPower(neutralMode);
        double limited = Math.max(peakReverseVolts, Math.min(peakForwardVolts, requested));
        if ((limited > 0 && position >= forwardSoftLimit) || (limited < 0 && position <= reverseSoftLimit)) {
            limited = 0;
        }
        volts = limited;
        writePower(battery.toPower(limited));
    }

    // The same peak and soft limits, for duty cycle.
    private double limitPower(double requested) {
        double inverseVolts = battery.getInverse();
        double limited = Math.max(peakReverseVolts * inverseVolts, Math.min(peakForwardVolts * inverseVolts, requested));
        if ((limited > 0 && position >= forwardSoftLimit) || (limited < 0 && position <= reverseSoftLimit)) {
            limited = 0;
        }
        return Math.max(-1, Math.min(1, limited));
    }

    private void writePower(double newPower) {
        boolean small = Math.abs(newPower - power) < powerDeadband;
        boolean endpoint = newPower == 0 || Math.abs(newPower) == 1;
        if (newPower == power || (small && !endpoint)) {
            return;
        }
        power = newPower;
        for (DcMotorEx motor : motors) {
            motor.setPower(power);
        }
    }

    // Changing it is a hub command per motor, so only when it changes.
    private void setZeroPower(DcMotor.ZeroPowerBehavior behavior) {
        if (behavior == appliedZeroPower) {
            return;
        }
        appliedZeroPower = behavior;
        for (DcMotorEx motor : motors) {
            motor.setZeroPowerBehavior(behavior);
        }
    }

    // ---- State ----

    /**
     * Makes the current spot read as this position, without resetting the encoder (which would cost
     * a mode change and stop the motors). For homing against a hard stop or a limit switch.
     */
    public void resetPosition(double newPosition) {
        positionOffset = newPosition - motors[0].getCurrentPosition() * positionPerTick;
        position = newPosition;
        profileSetpoint.set(newPosition, 0, 0);
    }

    /**
     * Whether the position or velocity is within tolerance of the target, for closed loop controls.
     * Motion Magic also has to have finished its profile.
     */
    public boolean isAtTarget(double tolerance) {
        switch (mode) {
            case VELOCITY:
            case MOTION_MAGIC_VELOCITY:
                return Math.abs(target - velocity) <= tolerance;
            case POSITION:
                return Math.abs(target - position) <= tolerance;
            case MOTION_MAGIC:
                return profileSetpoint.position == target && Math.abs(target - position) <= tolerance;
            default:
                return false;
        }
    }

    /** The control running now. */
    public Mode getMode() {
        return mode;
    }

    /** The last target set, in position or velocity units. */
    public double getTarget() {
        return target;
    }

    /** The measured position, in position units, as of the last update(). */
    public double getPosition() {
        return position;
    }

    /** The measured velocity, in velocity units, as of the last update(). */
    public double getVelocity() {
        return velocity;
    }

    /**
     * Where the profile or ramp says the mechanism should be right now.
     */
    public double getSetpointPosition() {
        return mode == Mode.MOTION_MAGIC ? profileSetpoint.position : target;
    }

    /** The velocity the profile or ramp says the mechanism should have right now. */
    public double getSetpointVelocity() {
        if (mode == Mode.MOTION_MAGIC) {
            return profileSetpoint.velocity;
        }
        return mode == Mode.MOTION_MAGIC_VELOCITY ? velocityRamp.getValue() : target;
    }

    /**
     * The volts asked for after limits, before dividing by the battery.
     */
    public double getVolts() {
        return volts;
    }

    /** The power last written to the motors, -1 to 1. */
    public double getPower() {
        return power;
    }

    /**
     * For settings (StaticSign, gravity type, filters) and for each term on telemetry.
     */
    public PIDSVAController getController() {
        return controller;
    }

    /** The battery reading this motor compensates with. */
    public BatteryVoltage getBattery() {
        return battery;
    }
}
