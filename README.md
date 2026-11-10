# Volta

Voltage-compensated motor control for FTC.

Volta drives your motors in **volts**, not power. Tell it what you want the mechanism to do: hold 3200 RPM, move the lift to 28 inches, or keep the arm level. A feedforward + PID loop works out the volts, then divides by the measured battery voltage. The same gains work at 13.8 V at the start of a match and at 12 V at the end.

```java
lift.set(new MotionMagicVoltage(28));   // move to 28 inches along a smooth profile
lift.update();                          // once per loop
```

> **Status: 0.1.0, early.** The math is covered by unit tests and simulations. Hardware testing on FTC 22536 BIOBUZZ's robot is in progress. Expect small API changes before 1.0.

## Features

- **One `set()` for every control.** Requests for duty cycle, voltage, velocity, ramped velocity, position, profiled position (Motion Magic), and neutral, coast or brake.
- **One configuration object** per mechanism: gains, units, output limits, soft limits and motion profile.
- **Three gain slots.** Each request picks one, for a lift that's loaded or empty, or a turret making big moves or tracking.
- **PIDSVA+G gains** in volts: kS, kV, kA feedforward, kG for lifts and arms, and kP, kI, kD feedback.
- **Motion profiles** that can change goal mid-move.
- **Followers**, peak voltages and soft limits.
- **Built for loop time:** no objects are made each loop, battery reads are throttled, and motor writes are skipped when nothing changed.
- **Live tuning:** the configuration is read every loop, so it works with Panels `@Configurable` and FTC Dashboard `@Config`.

## Install

Add JitPack and Volta to `build.dependencies.gradle`:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.davidsigreat:Volta:v0.1.0'
}
```

Volta builds against FTC SDK 12.0 (Java 8, min SDK 24). The SDK isn't bundled, so it uses the version your project already has.

## Quick start

```java
public static VoltageMotorConfig LIFT_CONFIG = new VoltageMotorConfig()
        .withSlot0(new SlotConfig(0.8, 0, 0, 0.15, 0.12, 0.004, 0.35))     // kP kI kD kS kV kA kG
        .withFeedback(new FeedbackConfig().withPositionPerTick(Math.PI * 1.5 / 384.5))   // inches
        .withSoftLimits(new SoftLimitConfig().withLimits(0, 30))
        .withMotionMagic(new MotionMagicConfig(40, 120));                   // in/s, in/s²

VoltageMotor lift = new VoltageMotor(new BatteryVoltage(hardwareMap), LIFT_CONFIG, leftMotor, rightMotor);
MotionMagicVoltage liftRequest = new MotionMagicVoltage(0);

// in loop():
lift.set(liftRequest.withPosition(gamepad1.a ? 28 : 0));
lift.update();
```

The [getting started guide](docs/getting-started.md) walks through this step by step.

## Documentation

| Page | What's in it |
|---|---|
| [Getting started](docs/getting-started.md) | Install, bulk caching, your first mechanism |
| [Controls](docs/controls.md) | Every control request and when to use it |
| [Configuration](docs/configuration.md) | Every setting, and gain slots |
| [Gains and tuning](docs/gains-and-tuning.md) | What each gain does and how to find it |
| [Examples](docs/examples.md) | Flywheel, lift, arm, turret, and the controller on its own |
| [API reference](docs/api.md) | Every public class, method and field |
| [Performance](docs/performance.md) | What costs loop time and how Volta avoids it |

## License

MIT. See [LICENSE](LICENSE).
