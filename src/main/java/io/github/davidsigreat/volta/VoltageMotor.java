package io.github.davidsigreat.volta;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;

import io.github.davidsigreat.volta.configs.MotorOutputConfig;
import io.github.davidsigreat.volta.configs.NeutralMode;
import io.github.davidsigreat.volta.configs.SoftLimitConfig;
import io.github.davidsigreat.volta.configs.VoltageMotorConfig;
import io.github.davidsigreat.volta.controls.ControlMode;
import io.github.davidsigreat.volta.controls.ControlRequest;

/**
 * One mechanism's motors and the control that drives them. Give it a control request with set(),
 * and call update() once per loop to run it:
 * <pre>
 *   VoltageMotor lift = new VoltageMotor(battery, LIFT_CONFIG, leftMotor, rightMotor);
 *   MotionMagicVoltage liftRequest = new MotionMagicVoltage(0);
 *
 *   // loop()
 *   lift.set(liftRequest.withPosition(28));
 *   lift.update();
 * </pre>
 * The requests are DutyCycleOut, VoltageOut, VelocityVoltage, MotionMagicVelocityVoltage,
 * PositionVoltage, MotionMagicVoltage, NeutralOut, CoastOut and StaticBrake, in the controls
 * package. How the motor runs (gains, units, limits) is its VoltageMotorConfig.
 * <p>
 * set() only records the request, so call it as often as you like; update() does the work. The
 * first motor is the leader: its encoder is the one read, so it must be the one with an encoder
 * cable. Every motor after it follows with the same power. Set each motor's direction before
 * passing it in, so they all turn the mechanism the same way.
 * <p>
 * Turn on bulk caching (MANUAL, cleared each loop) so the encoder reads are free.
 */
public class VoltageMotor {

    private final DcMotorEx[] motors;
    private final BatteryVoltage battery;
    private final PIDSVAController controller;
    private VoltageMotorConfig config;
    private long lastUpdateNanos = System.nanoTime();

    // The request, copied out of the last set() so the request object can be reused.
    private ControlMode mode = ControlMode.NEUTRAL;
    private int slot = 0;
    private boolean restart = true;
    private double target = 0;
    private double feedforwardVelocity = 0;
    private double requestAcceleration = 0;

    private DcMotor.ZeroPowerBehavior appliedZeroPower = null;
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
     * @param config    how the motor runs; read every loop, so it can be tuned live
     * @param leader    the motor whose encoder is read
     * @param followers motors that get the same power as the leader
     */
    public VoltageMotor(BatteryVoltage battery, VoltageMotorConfig config, DcMotorEx leader, DcMotorEx... followers) {
        this.battery = battery;
        this.config = config;
        this.controller = new PIDSVAController(config.slot0, config.closedLoop);
        motors = new DcMotorEx[followers.length + 1];
        motors[0] = leader;
        System.arraycopy(followers, 0, motors, 1, followers.length);
        for (DcMotorEx motor : motors) {
            motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        }
    }

    /** Makes a mechanism with a default configuration: no gains, 1 position unit per tick. */
    public VoltageMotor(BatteryVoltage battery, DcMotorEx leader, DcMotorEx... followers) {
        this(battery, new VoltageMotorConfig(), leader, followers);
    }

    // ---- Configuration ----

    /** Switches to another configuration. Takes effect on the next update(), starting fresh. */
    public void apply(VoltageMotorConfig newConfig) {
        config = newConfig;
        restart = true;
    }

    /** The configuration in use. Edit its fields to change how the motor runs, live. */
    public VoltageMotorConfig getConfig() {
        return config;
    }

    // ---- Control ----

    /**
     * Runs a control request from the next update() on. The values are copied, so the request
     * object can be changed and set again each loop.
     * <p>
     * Changing to a different control or slot starts fresh from where the mechanism is: the
     * integral and derivative are cleared, and ramps and profiles start at the measured position
     * and velocity. Changing only the target carries on smoothly.
     */
    public void set(ControlRequest request) {
        ControlMode newMode = request.getControlMode();
        int newSlot = request.getSlot();
        if (newMode != mode || newSlot != slot) {
            config.getSlot(newSlot); // fails here, not in update(), for a bad slot number
            mode = newMode;
            slot = newSlot;
            restart = true;
        }
        target = request.getValue();
        feedforwardVelocity = request.getFeedforwardVelocity();
        requestAcceleration = request.getAcceleration();
    }

    // ---- Loop ----

    /** Call once per loop: reads the encoder, runs the control, and writes the motors. */
    public void update() {
        VoltageMotorConfig c = config;
        long now = System.nanoTime();
        double dt = Math.min((now - lastUpdateNanos) * 1e-9, c.maxLoopSeconds);
        lastUpdateNanos = now;
        readEncoder(c);
        controller.setSlot(c.getSlot(slot));
        controller.setClosedLoop(c.closedLoop);

        if (restart) {
            restart = false;
            controller.reset();
            velocityRamp.reset(velocity);
            profileSetpoint.set(position, velocity, 0);
        }

        MotorOutputConfig output = c.motorOutput;
        switch (mode) {
            case DUTY_CYCLE:
                setZeroPower(zeroPower(output.neutralMode));
                volts = target * battery.get();
                writePower(limitPower(target, c), output);
                return;
            case VOLTAGE:
                outputVolts(target, c);
                return;
            case VELOCITY:
                outputVolts(controller.calculate(target, velocity, target, requestAcceleration, position, dt), c);
                return;
            case MOTION_MAGIC_VELOCITY: {
                double rate = requestAcceleration > 0 ? requestAcceleration : c.motionMagic.acceleration;
                // No ramp rate at all means no ramp, not a ramp that never moves.
                velocityRamp.maxRate = rate > 0 ? rate : Double.MAX_VALUE;
                double setpoint = velocityRamp.calculate(target, dt);
                outputVolts(controller.calculate(setpoint, velocity, setpoint, velocityRamp.getRate(), position, dt), c);
                return;
            }
            case POSITION:
                outputVolts(controller.calculate(target, position, feedforwardVelocity, 0, position, dt), c);
                return;
            case MOTION_MAGIC:
                profileGoal.set(target, 0, 0);
                TrapezoidProfile.step(dt, profileSetpoint, profileGoal,
                        c.motionMagic.cruiseVelocity, c.motionMagic.acceleration, profileSetpoint);
                outputVolts(controller.calculate(profileSetpoint.position, position,
                        profileSetpoint.velocity, profileSetpoint.acceleration, position, dt), c);
                return;
            case COAST:
                stop(DcMotor.ZeroPowerBehavior.FLOAT, output);
                return;
            case BRAKE:
                stop(DcMotor.ZeroPowerBehavior.BRAKE, output);
                return;
            default:
                stop(zeroPower(output.neutralMode), output);
        }
    }

    private void readEncoder(VoltageMotorConfig c) {
        DcMotorEx leader = motors[0];
        double positionPerTick = c.feedback.positionPerTick;
        double velocityPerTick = c.feedback.velocityPerTickPerSecond;
        position = leader.getCurrentPosition() * positionPerTick + positionOffset;
        velocity = leader.getVelocity() * (Double.isNaN(velocityPerTick) ? positionPerTick : velocityPerTick);
    }

    private void stop(DcMotor.ZeroPowerBehavior behavior, MotorOutputConfig output) {
        setZeroPower(behavior);
        volts = 0;
        writePower(0, output);
    }

    private void outputVolts(double requested, VoltageMotorConfig c) {
        MotorOutputConfig output = c.motorOutput;
        setZeroPower(zeroPower(output.neutralMode));
        double limited = Math.max(output.peakReverseVolts, Math.min(output.peakForwardVolts, requested));
        volts = softLimit(limited, c.softLimits);
        writePower(battery.toPower(volts), output);
    }

    // The same peak and soft limits, for duty cycle.
    private double limitPower(double requested, VoltageMotorConfig c) {
        MotorOutputConfig output = c.motorOutput;
        double inverseVolts = battery.getInverse();
        double limited = Math.max(output.peakReverseVolts * inverseVolts,
                Math.min(output.peakForwardVolts * inverseVolts, requested));
        return Math.max(-1, Math.min(1, softLimit(limited, c.softLimits)));
    }

    private double softLimit(double output, SoftLimitConfig limits) {
        if ((output > 0 && position >= limits.forward) || (output < 0 && position <= limits.reverse)) {
            return 0;
        }
        return output;
    }

    private void writePower(double newPower, MotorOutputConfig output) {
        boolean small = Math.abs(newPower - power) < output.powerDeadband;
        boolean endpoint = newPower == 0 || Math.abs(newPower) == 1;
        if (newPower == power || (small && !endpoint)) {
            return;
        }
        power = newPower;
        for (DcMotorEx motor : motors) {
            motor.setPower(power);
        }
    }

    private static DcMotor.ZeroPowerBehavior zeroPower(NeutralMode mode) {
        return mode == NeutralMode.COAST ? DcMotor.ZeroPowerBehavior.FLOAT : DcMotor.ZeroPowerBehavior.BRAKE;
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
     * Makes the current spot read as this position, without resetting the encoder (which would
     * cost a mode change and stop the motors). For homing against a hard stop or a limit switch.
     */
    public void resetPosition(double newPosition) {
        positionOffset = newPosition - motors[0].getCurrentPosition() * config.feedback.positionPerTick;
        position = newPosition;
        profileSetpoint.set(newPosition, 0, 0);
    }

    /**
     * Whether the position or velocity is within tolerance of the target, for closed loop
     * controls. Motion Magic also has to have finished its profile. Always false for open loop
     * and neutral.
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
    public ControlMode getControlMode() {
        return mode;
    }

    /** The gain slot in use: 0, 1 or 2. */
    public int getSlotNumber() {
        return slot;
    }

    /** The last request's main value: power, volts, velocity or position. */
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

    /** Where the profile says the mechanism should be right now. The target for other controls. */
    public double getSetpointPosition() {
        return mode == ControlMode.MOTION_MAGIC ? profileSetpoint.position : target;
    }

    /** The velocity the profile or ramp says the mechanism should have right now. */
    public double getSetpointVelocity() {
        if (mode == ControlMode.MOTION_MAGIC) {
            return profileSetpoint.velocity;
        }
        return mode == ControlMode.MOTION_MAGIC_VELOCITY ? velocityRamp.getValue() : target;
    }

    /** The volts asked for after limits, before dividing by the battery. */
    public double getVolts() {
        return volts;
    }

    /** The power last written to the motors, -1 to 1. */
    public double getPower() {
        return power;
    }

    /** The controller, for each term on telemetry while tuning. */
    public PIDSVAController getController() {
        return controller;
    }

    /** The battery reading this motor compensates with. */
    public BatteryVoltage getBattery() {
        return battery;
    }
}
