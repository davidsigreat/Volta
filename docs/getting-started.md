# Getting started

This guide takes you from an empty OpMode to a lift that moves to a height and holds it.

## 1. Install

In your project's `build.dependencies.gradle`, add JitPack to `repositories` and Volta to `dependencies`:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.davidsigreat:Volta:v0.1.0'
}
```

Sync Gradle. The classes are in `io.github.davidsigreat.volta`.

## 2. Turn on bulk caching

Volta reads the encoder every loop. With bulk caching in `MANUAL` mode, every encoder read on a hub comes from one transaction per loop instead of one each.

```java
private List<LynxModule> hubs;

@Override
public void init() {
    hubs = hardwareMap.getAll(LynxModule.class);
    for (LynxModule hub : hubs) {
        hub.setBulkCachingMode(LynxModule.BulkCachingMode.MANUAL);
    }
}

@Override
public void loop() {
    for (LynxModule hub : hubs) {
        hub.clearBulkCache();   // first thing, every loop
    }
    // ...
}
```

## 3. Make the motor

```java
DcMotorEx left = hardwareMap.get(DcMotorEx.class, "liftLeft");    // this one has the encoder cable
DcMotorEx right = hardwareMap.get(DcMotorEx.class, "liftRight");
right.setDirection(DcMotorSimple.Direction.REVERSE);

BatteryVoltage battery = new BatteryVoltage(hardwareMap);
PIDSVAGains gains = new PIDSVAGains();   // all zero for now

VoltageMotor lift = new VoltageMotor(battery, gains, left, right);
```

- The **first motor is the leader**. Its encoder is the one read, so it must be the one with the encoder cable.
- Every motor after it is a **follower** and gets the same power.
- Set each motor's **direction before** passing it in, so positive power moves the mechanism the same way on all of them, and positive power counts the encoder up.

## 4. Set the units

Volta works in whatever units you choose. Pick ones that mean something for the mechanism: inches for a lift, degrees for an arm, RPM for a flywheel.

```java
// Spool 1.5 in across, 384.5 ticks per output revolution (goBILDA 435 RPM)
lift.positionPerTick = Math.PI * 1.5 / 384.5;   // inches per tick
```

Velocity is then in inches per second. For a flywheel in RPM, set `velocityPerTickPerSecond = 60.0 / ticksPerRev` instead.

Your gains use these units too, so set the units before you tune.

## 5. Check it with volts

Before any closed loop control, drive it open loop and make sure the numbers make sense:

```java
lift.setVoltage(gamepad1.left_stick_y * -6);   // up to 6 V either way
lift.update();

telemetry.addData("Position", lift.getPosition());
telemetry.addData("Velocity", lift.getVelocity());
telemetry.addData("Volts", lift.getVolts());
```

Positive volts should move the lift up, and the position should count up. If it doesn't, fix the motor directions now.

## 6. Tune, then use a closed loop control

Find kS, kV, kG and kP with the [tuning guide](gains-and-tuning.md), then:

```java
lift.motionMagic = new MotionMagicConfig(40, 120);   // 40 in/s cruise, 120 in/s² acceleration
lift.reverseSoftLimit = 0;
lift.forwardSoftLimit = 30;

// in loop():
if (gamepad1.a) lift.setMotionMagic(28);
if (gamepad1.b) lift.setMotionMagic(0);
lift.update();
```

Set methods only choose the control and target, so calling them every loop is fine. `update()` does the work: read the encoder, run the control, write the motors. Call it **exactly once per loop**, even when no button is pressed. That's what keeps the lift holding its position.

## 7. Tune live

Make the gains a `public static` field of a Panels `@Configurable` (or FTC Dashboard `@Config`) class. The controller reads the gains every loop, so changes take effect right away.

```java
@Configurable
public class LiftConstants {
    public static PIDSVAGains GAINS = new PIDSVAGains(0.8, 0, 0, 0.15, 0.12, 0.004, 0.35);
    public static MotionMagicConfig MOTION = new MotionMagicConfig(40, 120);
}

lift = new VoltageMotor(battery, LiftConstants.GAINS, left, right);
lift.motionMagic = LiftConstants.MOTION;
```

## Next

- [Controls](controls.md): every control and setting
- [Examples](examples.md): flywheel, arm and turret
