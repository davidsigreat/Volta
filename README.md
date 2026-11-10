# Volta

Voltage-compensated motor control for FTC.

Volta drives your motors in **volts**, not power. Tell it what you want the mechanism to do: hold 3200 RPM, move the lift to 28 inches, or keep the arm level. A feedforward + PID loop works out the volts, then divides by the measured battery voltage. The same gains work at 13.8 V at the start of a match and at 12 V at the end.

```java
lift.setMotionMagic(28);   // move to 28 inches along a smooth profile
lift.update();             // once per loop
```

> **Status: 0.1.0, early.** The math is covered by unit tests and simulations. Hardware testing on FTC 22536 BIOBUZZ's robot is in progress. Expect small API changes before 1.0.

## Features

- **Seven controls** on one class: duty cycle, voltage, velocity, ramped velocity, position, profiled position (Motion Magic), and neutral, coast or brake.
- **PIDSVA+G gains** in volts: kS, kV, kA feedforward, kG for lifts and arms, and kP, kI, kD feedback.
- **Motion profiles** that can change goal mid-move.
- **Followers**, peak voltages and soft limits.
- **Built for loop time:** no objects are made each loop, battery reads are throttled, and motor writes are skipped when nothing changed.
- **Live tuning:** gains and limits are plain public fields, so they work with Panels `@Configurable` and FTC Dashboard `@Config`.

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
BatteryVoltage battery = new BatteryVoltage(hardwareMap);
PIDSVAGains gains = new PIDSVAGains(0.8, 0, 0, 0.15, 0.12, 0.004, 0.35); // kP kI kD kS kV kA kG

VoltageMotor lift = new VoltageMotor(battery, gains, leftMotor, rightMotor);
lift.positionPerTick = Math.PI * 1.5 / 384.5;   // inches per tick
lift.motionMagic = new MotionMagicConfig(40, 120);

// in loop():
lift.setMotionMagic(gamepad1.a ? 28 : 0);
lift.update();
```

The [getting started guide](docs/getting-started.md) walks through this step by step.

## Documentation

| Page | What's in it |
|---|---|
| [Getting started](docs/getting-started.md) | Install, bulk caching, your first mechanism |
| [Controls](docs/controls.md) | Every control, when to use it, and the motor settings |
| [Gains and tuning](docs/gains-and-tuning.md) | What each gain does and how to find it |
| [Examples](docs/examples.md) | Flywheel, lift, arm, turret, and the controller on its own |
| [API reference](docs/api.md) | Every public class, method and field |
| [Performance](docs/performance.md) | What costs loop time and how Volta avoids it |

## License

MIT. See [LICENSE](LICENSE).
