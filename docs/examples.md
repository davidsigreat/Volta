# Examples

Every example assumes bulk caching is on (see [getting started](getting-started.md)) and that `update()` is called once per loop. The gains are placeholders. Tune your own with the [tuning guide](gains-and-tuning.md).

## Flywheel

Ramps to a target RPM, holds it on any battery, and coasts down when stopped.

```java
public static PIDSVAGains FLYWHEEL_GAINS = new PIDSVAGains(
        0.007, 0, 0, 0.45, 0.0022, 0.0021);   // kP kI kD kS kV kA, in volts per RPM

private static final double TICKS_PER_REV = 28;   // goBILDA bare motor

BatteryVoltage battery = new BatteryVoltage(hardwareMap);
VoltageMotor flywheel = new VoltageMotor(battery, FLYWHEEL_GAINS,
        hardwareMap.get(DcMotorEx.class, "flywheelLeft"),
        hardwareMap.get(DcMotorEx.class, "flywheelRight"));
flywheel.velocityPerTickPerSecond = 60.0 / TICKS_PER_REV;   // RPM
flywheel.peakReverseVolts = 0;                              // never brake it backwards
flywheel.neutralMode = DcMotor.ZeroPowerBehavior.FLOAT;     // coast down

// loop()
if (gamepad1.right_bumper) {
    flywheel.setMotionMagicVelocity(3200, 6000);   // 3200 RPM, ramping at 6000 RPM/s
} else {
    flywheel.neutral();
}
flywheel.update();

boolean readyToShoot = flywheel.isAtTarget(50);
```

`peakReverseVolts = 0` matters: without it, dropping the target from 3200 to 2000 RPM would drive the motor backwards to slow down, which is hard on the gearbox and the battery.

## Lift (elevator)

Two motors, profiled moves, gravity held by kG, and soft limits.

```java
public static PIDSVAGains LIFT_GAINS = new PIDSVAGains(
        0.8, 0, 0, 0.15, 0.12, 0.004, 0.35);   // kP kI kD kS kV kA kG, inches
public static MotionMagicConfig LIFT_MOTION = new MotionMagicConfig(40, 120);   // in/s, in/s²

DcMotorEx left = hardwareMap.get(DcMotorEx.class, "liftLeft");
DcMotorEx right = hardwareMap.get(DcMotorEx.class, "liftRight");
right.setDirection(DcMotorSimple.Direction.REVERSE);

VoltageMotor lift = new VoltageMotor(new BatteryVoltage(hardwareMap), LIFT_GAINS, left, right);
lift.positionPerTick = Math.PI * 1.5 / 384.5;   // 1.5 in spool, 435 RPM motor
lift.motionMagic = LIFT_MOTION;
lift.reverseSoftLimit = 0;
lift.forwardSoftLimit = 30;

// loop()
if (gamepad1.y) lift.setMotionMagic(28);   // high
if (gamepad1.x) lift.setMotionMagic(14);   // mid
if (gamepad1.a) lift.setMotionMagic(0);    // down
if (bottomSwitch.isPressed()) lift.resetPosition(0);
lift.update();
```

## Arm

An arm in degrees, with gravity that changes with the angle.

```java
public static PIDSVAGains ARM_GAINS = new PIDSVAGains(
        0.05, 0, 0.002, 0.2, 0.02, 0.001, 0.6);   // volts per degree; kG = volts to hold it level
public static MotionMagicConfig ARM_MOTION = new MotionMagicConfig(180, 540);   // deg/s, deg/s²

VoltageMotor arm = new VoltageMotor(new BatteryVoltage(hardwareMap), ARM_GAINS,
        hardwareMap.get(DcMotorEx.class, "arm"));
arm.positionPerTick = 360.0 / (28 * 50.9);   // degrees per tick: 28 ticks × 50.9:1 gearbox
arm.motionMagic = ARM_MOTION;

PIDSVAController controller = arm.getController();
controller.gravityType = PIDSVAController.GravityType.ARM_COSINE;
controller.armHorizontalPosition = 0;            // 0° is level
controller.positionToRadians = Math.PI / 180;

// The encoder reads 0 wherever the arm was at init. If it starts resting at -30°:
arm.resetPosition(-30);

// loop()
arm.setMotionMagic(gamepad1.a ? 90 : -30);
arm.update();
```

With `ARM_COSINE`, kG is full when the arm is level and zero when it points straight up or down.

## Turret (motor driven)

A turret that tracks a moving target. Use `setPosition` for small, constant updates. For big jumps, `setMotionMagic` gives a smooth move.

```java
public static PIDSVAGains TURRET_GAINS = new PIDSVAGains(
        0.12, 0, 0.004, 0.25, 0.015, 0);   // volts per degree
VoltageMotor turret = new VoltageMotor(new BatteryVoltage(hardwareMap), TURRET_GAINS,
        hardwareMap.get(DcMotorEx.class, "turret"));
turret.positionPerTick = 360.0 / (28 * 13.7 * 5);   // motor ticks, gearbox, and a 5:1 ring
turret.reverseSoftLimit = -170;
turret.forwardSoftLimit = 170;

PIDSVAController c = turret.getController();
c.staticSign = PIDSVAController.StaticSign.ERROR;   // kS helps finish small corrections
c.staticDeadband = 0.5;                             // no kS within half a degree

// loop()
turret.setPosition(angleToGoal());
turret.update();
```

## Sharing one battery reading

`BatteryVoltage` reads the sensor at most every 0.5 s, so sharing one between mechanisms means at most one read per half second for the whole robot:

```java
BatteryVoltage battery = new BatteryVoltage(hardwareMap);
VoltageMotor lift = new VoltageMotor(battery, LIFT_GAINS, liftMotor);
VoltageMotor arm = new VoltageMotor(battery, ARM_GAINS, armMotor);
```

## The controller on its own

`PIDSVAController` is plain Java. Use it directly for something `VoltageMotor` doesn't cover, like a CRServo with an external encoder or a drivetrain heading loop.

```java
PIDSVAController heading = new PIDSVAController(new PIDSVAGains(4, 0, 0.2, 0.1, 0, 0));
heading.staticSign = PIDSVAController.StaticSign.ERROR;

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
