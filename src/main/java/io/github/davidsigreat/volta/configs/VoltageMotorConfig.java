package io.github.davidsigreat.volta.configs;

/**
 * Everything about how a VoltageMotor runs, in one object.
 * <pre>
 *   public static VoltageMotorConfig LIFT_CONFIG = new VoltageMotorConfig()
 *           .withSlot0(new SlotConfig(0.8, 0, 0, 0.15, 0.12, 0.004, 0.35))
 *           .withFeedback(new FeedbackConfig().withPositionPerTick(Math.PI * 1.5 / 384.5))
 *           .withSoftLimits(new SoftLimitConfig().withLimits(0, 30))
 *           .withMotionMagic(new MotionMagicConfig(40, 120));
 *
 *   lift = new VoltageMotor(battery, LIFT_CONFIG, leftMotor, rightMotor);
 * </pre>
 * The motor keeps a reference and reads it every update(), so edits take effect on the next loop.
 * Put one in a static field of a Panels {@code @Configurable} or FTC Dashboard {@code @Config}
 * class to tune it all live.
 */
public class VoltageMotorConfig {
    /** Slot 0 gains. Closed loop requests use this one unless they pick another. */
    public SlotConfig slot0 = new SlotConfig();
    /** Slot 1 gains, for requests with {@code withSlot(1)}. */
    public SlotConfig slot1 = new SlotConfig();
    /** Slot 2 gains, for requests with {@code withSlot(2)}. */
    public SlotConfig slot2 = new SlotConfig();
    /** Settings shared by every slot: arm level position, kS deadband, integral and derivative limits. */
    public ClosedLoopConfig closedLoop = new ClosedLoopConfig();
    /** Encoder ticks to position and velocity units. */
    public FeedbackConfig feedback = new FeedbackConfig();
    /** Neutral mode, peak volts and write skipping. */
    public MotorOutputConfig motorOutput = new MotorOutputConfig();
    /** Position limits. */
    public SoftLimitConfig softLimits = new SoftLimitConfig();
    /**
     * Cruise velocity and acceleration for MotionMagicVoltage, and the default ramp rate for
     * MotionMagicVelocityVoltage.
     */
    public MotionMagicConfig motionMagic = new MotionMagicConfig();
    /** The longest loop time the control will use, in seconds. The first loop's includes all of init. */
    public double maxLoopSeconds = 0.1;

    /**
     * The slot with this number: 0, 1 or 2.
     *
     * @throws IllegalArgumentException for any other number
     */
    public SlotConfig getSlot(int slot) {
        switch (slot) {
            case 0:
                return slot0;
            case 1:
                return slot1;
            case 2:
                return slot2;
            default:
                throw new IllegalArgumentException("Slot must be 0, 1 or 2, not " + slot);
        }
    }

    /** Sets slot0 and returns this config. */
    public VoltageMotorConfig withSlot0(SlotConfig slot0) {
        this.slot0 = slot0;
        return this;
    }

    /** Sets slot1 and returns this config. */
    public VoltageMotorConfig withSlot1(SlotConfig slot1) {
        this.slot1 = slot1;
        return this;
    }

    /** Sets slot2 and returns this config. */
    public VoltageMotorConfig withSlot2(SlotConfig slot2) {
        this.slot2 = slot2;
        return this;
    }

    /** Sets closedLoop and returns this config. */
    public VoltageMotorConfig withClosedLoop(ClosedLoopConfig closedLoop) {
        this.closedLoop = closedLoop;
        return this;
    }

    /** Sets feedback and returns this config. */
    public VoltageMotorConfig withFeedback(FeedbackConfig feedback) {
        this.feedback = feedback;
        return this;
    }

    /** Sets motorOutput and returns this config. */
    public VoltageMotorConfig withMotorOutput(MotorOutputConfig motorOutput) {
        this.motorOutput = motorOutput;
        return this;
    }

    /** Sets softLimits and returns this config. */
    public VoltageMotorConfig withSoftLimits(SoftLimitConfig softLimits) {
        this.softLimits = softLimits;
        return this;
    }

    /** Sets motionMagic and returns this config. */
    public VoltageMotorConfig withMotionMagic(MotionMagicConfig motionMagic) {
        this.motionMagic = motionMagic;
        return this;
    }

    /** Sets maxLoopSeconds and returns this config. */
    public VoltageMotorConfig withMaxLoopSeconds(double maxLoopSeconds) {
        this.maxLoopSeconds = maxLoopSeconds;
        return this;
    }
}
