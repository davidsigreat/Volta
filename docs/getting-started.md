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

Sync Gradle. The classes are in three packages:

| Package | What's in it |
|---|---|
| `io.github.davidsigreat.volta` | `VoltageMotor`, `BatteryVoltage`, `PIDSVAController`, `TrapezoidProfile`, `SlewRateLimiter` |
| `io.github.davidsigreat.volta.configs` | `VoltageMotorConfig`, `SlotConfig` and the other settings |
| `io.github.davidsigreat.volta.controls` | Control requests: `VoltageOut`, `MotionMagicVoltage`, ... |

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

## 3. Write a configuration

The configuration says how the motor runs. Start with just the units, and leave the gains at 0 for now:

```java
public static VoltageMotorConfig LIFT_CONFIG = new VoltageMotorConfig()
        // 1.5 in spool, 384.5 ticks per output revolution (goBILDA 435 RPM)
        .withFeedback(new FeedbackConfig().withPositionPerTick(Math.PI * 1.5 / 384.5));
```

Volta works in whatever units you choose. Pick ones that mean something for the mechanism: inches for a lift, degrees for an arm, RPM for a flywheel. With `positionPerTick` in inches, velocity is in inches per second. For a flywheel in RPM, set `velocityPerTickPerSecond = 60.0 / ticksPerRev` instead.

Your gains use these units too, so set the units before you tune.

## 4. Make the motor

```java
DcMotorEx left = hardwareMap.get(DcMotorEx.class, "liftLeft");    // this one has the encoder cable
DcMotorEx right = hardwareMap.get(DcMotorEx.class, "liftRight");
right.setDirection(DcMotorSimple.Direction.REVERSE);

VoltageMotor lift = new VoltageMotor(new BatteryVoltage(hardwareMap), LIFT_CONFIG, left, right);
```

- The **first motor is the leader**. Its encoder is the one read, so it must be the one with the encoder cable.
- Every motor after it is a **follower** and gets the same power.
- Set each motor's **direction before** passing it in, so positive power moves the mechanism the same way on all of them, and positive power counts the encoder up.

## 5. Check it with volts

Before any closed loop control, drive it open loop and make sure the numbers make sense:

```java
private final VoltageOut manual = new VoltageOut(0);

// loop()
lift.set(manual.withOutput(-gamepad1.left_stick_y * 6));   // up to 6 V either way
lift.update();

telemetry.addData("Position", lift.getPosition());
telemetry.addData("Velocity", lift.getVelocity());
telemetry.addData("Volts", lift.getVolts());
```

Positive volts should move the lift up, and the position should count up. If it doesn't, fix the motor directions now.

## 6. Tune, then use a closed loop control

Find kS, kV, kG and kP with the [tuning guide](gains-and-tuning.md), then add them and the rest of the configuration:

```java
public static VoltageMotorConfig LIFT_CONFIG = new VoltageMotorConfig()
        .withSlot0(new SlotConfig(0.8, 0, 0, 0.15, 0.12, 0.004, 0.35))   // kP kI kD kS kV kA kG
        .withFeedback(new FeedbackConfig().withPositionPerTick(Math.PI * 1.5 / 384.5))
        .withSoftLimits(new SoftLimitConfig().withLimits(0, 30))
        .withMotionMagic(new MotionMagicConfig(40, 120));   // 40 in/s cruise, 120 in/s²
```

```java
private final MotionMagicVoltage liftRequest = new MotionMagicVoltage(0);

// loop()
if (gamepad1.a) liftRequest.withPosition(28);
if (gamepad1.b) liftRequest.withPosition(0);
lift.set(liftRequest);
lift.update();
```

`set()` only records the request, so calling it every loop is fine. `update()` does the work: read the encoder, run the control, write the motors. Call it **exactly once per loop**, even when no button is pressed. That's what keeps the lift holding its position.

Make one request object per mechanism and change it with its `with...` methods, as above. `new MotionMagicVoltage(28)` every loop works too, but it makes garbage that the Java garbage collector has to clean up.

## 7. Tune live

Put the configuration in a Panels `@Configurable` (or FTC Dashboard `@Config`) class. The motor reads it every loop, so changes take effect right away.

```java
@Configurable
public class LiftConstants {
    public static VoltageMotorConfig CONFIG = new VoltageMotorConfig()
            .withSlot0(new SlotConfig(0.8, 0, 0, 0.15, 0.12, 0.004, 0.35));
}
```

## Next

- [Controls](controls.md): every control request
- [Configuration](configuration.md): every setting, and gain slots
- [Examples](examples.md): flywheel, arm and turret
