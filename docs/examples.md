# Examples

Every example assumes bulk caching is on (see [getting started](getting-started.md)) and that `update()` is called once per loop. The gains are placeholders. Tune your own with the [tuning guide](gains-and-tuning.md).

## Flywheel

Ramps to a target RPM, holds it on any battery, and coasts down when stopped.

```java
private static final double TICKS_PER_REV = 28;   // goBILDA bare motor

public static VoltageMotorConfig FLYWHEEL_CONFIG = new VoltageMotorConfig()
        .withSlot0(new SlotConfig(0.007, 0, 0, 0.45, 0.0022, 0.0021))         // volts per RPM
        .withFeedback(new FeedbackConfig().withVelocityPerTickPerSecond(60.0 / TICKS_PER_REV))
        .withMotorOutput(new MotorOutputConfig()
                .withNeutralMode(NeutralMode.COAST)                            // coast down
                .withPeakVolts(12, 0))                                         // never drive it backwards
        .withMotionMagic(new MotionMagicConfig().withAcceleration(6000));      // ramp at 6000 RPM/s

VoltageMotor flywheel = new VoltageMotor(new BatteryVoltage(hardwareMap), FLYWHEEL_CONFIG,
        hardwareMap.get(DcMotorEx.class, "flywheelLeft"),
        hardwareMap.get(DcMotorEx.class, "flywheelRight"));

private final MotionMagicVelocityVoltage spin = new MotionMagicVelocityVoltage(0);
private final NeutralOut stop = new NeutralOut();

// loop()
if (gamepad1.right_bumper) {
    flywheel.set(spin.withVelocity(3200));
} else {
    flywheel.set(stop);
}
flywheel.update();

boolean readyToShoot = flywheel.isAtTarget(50);
```

A reverse peak of 0 matters: without it, dropping the target from 3200 to 2000 RPM would drive the motor backwards to slow down, which is hard on the gearbox and the battery.

## Lift (elevator)

Two motors, profiled moves, gravity held by kG, soft limits, and a second slot for when it's carrying a game piece.

```java
public static VoltageMotorConfig LIFT_CONFIG = new VoltageMotorConfig()
        .withSlot0(new SlotConfig(0.8, 0, 0, 0.15, 0.12, 0.004, 0.35))   // empty, inches
        .withSlot1(new SlotConfig(1.0, 0, 0, 0.15, 0.12, 0.006, 0.55))   // carrying
        .withFeedback(new FeedbackConfig().withPositionPerTick(Math.PI * 1.5 / 384.5))
        .withSoftLimits(new SoftLimitConfig().withLimits(0, 30))
        .withMotionMagic(new MotionMagicConfig(40, 120));                // in/s, in/s²

DcMotorEx left = hardwareMap.get(DcMotorEx.class, "liftLeft");
DcMotorEx right = hardwareMap.get(DcMotorEx.class, "liftRight");
right.setDirection(DcMotorSimple.Direction.REVERSE);

VoltageMotor lift = new VoltageMotor(new BatteryVoltage(hardwareMap), LIFT_CONFIG, left, right);
private final MotionMagicVoltage liftRequest = new MotionMagicVoltage(0);

// loop()
if (gamepad1.y) liftRequest.withPosition(28);   // high
if (gamepad1.x) liftRequest.withPosition(14);   // mid
if (gamepad1.a) liftRequest.withPosition(0);    // down
lift.set(liftRequest.withSlot(hasGamePiece ? 1 : 0));
if (bottomSwitch.isPressed()) lift.resetPosition(0);
lift.update();
```

## Arm

An arm in degrees, with gravity that changes with the angle.

```java
public static VoltageMotorConfig ARM_CONFIG = new VoltageMotorConfig()
        .withSlot0(new SlotConfig(0.05, 0, 0.002, 0.2, 0.02, 0.001, 0.6)   // kG = volts to hold it level
                .withGravityType(GravityType.ARM_COSINE))
        .withClosedLoop(new ClosedLoopConfig().withArmHorizontal(0, Math.PI / 180))   // 0° is level
        .withFeedback(new FeedbackConfig().withPositionPerTick(360.0 / (28 * 50.9)))  // 50.9:1 gearbox
        .withMotionMagic(new MotionMagicConfig(180, 540));                             // deg/s, deg/s²

VoltageMotor arm = new VoltageMotor(new BatteryVoltage(hardwareMap), ARM_CONFIG,
        hardwareMap.get(DcMotorEx.class, "arm"));
// The encoder reads 0 wherever the arm was at init. If it starts resting at -30°:
arm.resetPosition(-30);

private final MotionMagicVoltage armRequest = new MotionMagicVoltage(-30);

// loop()
arm.set(armRequest.withPosition(gamepad1.a ? 90 : -30));
arm.update();
```

With `ARM_COSINE`, kG is full when the arm is level and zero when it points straight up or down.

## Turret (motor driven)

A turret that tracks a moving target, with one slot for big swings and a stiffer one for tracking.

```java
public static VoltageMotorConfig TURRET_CONFIG = new VoltageMotorConfig()
        .withSlot0(new SlotConfig(0.06, 0, 0.003, 0.25, 0.015, 0.0005))    // big moves
        .withSlot1(new SlotConfig(0.12, 0, 0.004, 0.25, 0.015, 0)          // tracking
                .withStaticSign(StaticSign.ERROR))                          // kS finishes small corrections
        .withClosedLoop(new ClosedLoopConfig().withStaticDeadband(0.5))    // no kS within 0.5°
        .withFeedback(new FeedbackConfig().withPositionPerTick(360.0 / (28 * 13.7 * 5)))   // 5:1 ring
        .withSoftLimits(new SoftLimitConfig().withLimits(-170, 170))
        .withMotionMagic(new MotionMagicConfig(360, 1440));

VoltageMotor turret = new VoltageMotor(new BatteryVoltage(hardwareMap), TURRET_CONFIG,
        hardwareMap.get(DcMotorEx.class, "turret"));
private final MotionMagicVoltage swing = new MotionMagicVoltage(0);
private final PositionVoltage track = new PositionVoltage(0).withSlot(1);

// loop()
double error = angleToGoal() - turret.getPosition();
if (Math.abs(error) > 20) {
    turret.set(swing.withPosition(angleToGoal()));
} else {
    // the goal's angular rate as the robot drives, so kV keeps up with it
    turret.set(track.withPosition(angleToGoal()).withVelocity(goalAngleRate()));
}
turret.update();
```

## Sharing one battery reading

`BatteryVoltage` reads the sensor at most every 0.5 s, so sharing one between mechanisms means at most one read per half second for the whole robot:

```java
BatteryVoltage battery = new BatteryVoltage(hardwareMap);
VoltageMotor lift = new VoltageMotor(battery, LIFT_CONFIG, liftMotor);
VoltageMotor arm = new VoltageMotor(battery, ARM_CONFIG, armMotor);
```

## The controller on its own

`PIDSVAController` is plain Java. Use it directly for something `VoltageMotor` doesn't cover, like a CRServo with an external encoder or a drivetrain heading loop.

```java
PIDSVAController heading = new PIDSVAController(
        new SlotConfig(4, 0, 0.2, 0.1, 0, 0).withStaticSign(StaticSign.ERROR));

// loop(): dt in seconds since the last call
double volts = heading.calculatePosition(targetRadians, currentRadians, 0, 0, dt);
double turnPower = battery.toPower(volts);
```

## A profile on its own

`TrapezoidProfile.step()` is allocation free and can be stepped each loop for anything that needs a smooth setpoint:

```java
TrapezoidProfile.State setpoint = new TrapezoidProfile.State(0, 0);
TrapezoidProfile.State goal = new TrapezoidProfile.State(48, 0);

// loop()
TrapezoidProfile.step(dt, setpoint, goal, 30, 60, setpoint);   // 30 in/s, 60 in/s²
// setpoint.position, setpoint.velocity, setpoint.acceleration
boolean done = TrapezoidProfile.isFinished(setpoint, goal);
```
