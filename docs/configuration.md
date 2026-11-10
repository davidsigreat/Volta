# Configuration

Everything about how a `VoltageMotor` runs is in one `VoltageMotorConfig`. Build it with the `with...` methods:

```java
public static VoltageMotorConfig ARM_CONFIG = new VoltageMotorConfig()
        .withSlot0(new SlotConfig(0.05, 0, 0.002, 0.2, 0.02, 0.001, 0.6)
                .withGravityType(GravityType.ARM_COSINE))
        .withClosedLoop(new ClosedLoopConfig().withArmHorizontal(0, Math.PI / 180))
        .withFeedback(new FeedbackConfig().withPositionPerTick(360.0 / (28 * 50.9)))
        .withMotorOutput(new MotorOutputConfig().withNeutralMode(NeutralMode.BRAKE))
        .withSoftLimits(new SoftLimitConfig().withLimits(-30, 120))
        .withMotionMagic(new MotionMagicConfig(180, 540));

VoltageMotor arm = new VoltageMotor(battery, ARM_CONFIG, armMotor);
```

Or set fields directly:

```java
VoltageMotorConfig config = new VoltageMotorConfig();
config.slot0.kP = 0.05;
config.motorOutput.peakReverseVolts = 0;
```

The motor **keeps a reference** to the configuration and reads it every `update()`. Edit any field, from code or live from Panels or FTC Dashboard, and it takes effect on the next loop. To switch to a different configuration object, call `motor.apply(newConfig)`.

All configuration classes are in `io.github.davidsigreat.volta.configs` and are plain Java.

## The parts

| Field | Class | What it holds |
|---|---|---|
| `slot0`, `slot1`, `slot2` | `SlotConfig` | Gains, gravity type and kS sign |
| `closedLoop` | `ClosedLoopConfig` | Settings shared by every slot |
| `feedback` | `FeedbackConfig` | Encoder ticks to your units |
| `motorOutput` | `MotorOutputConfig` | Neutral mode, peak volts, write skipping |
| `softLimits` | `SoftLimitConfig` | Position limits |
| `motionMagic` | `MotionMagicConfig` | Profile cruise velocity and acceleration |
| `maxLoopSeconds` | `double` | Longest dt the control uses. Default 0.1 |

## Gain slots

A motor has three slots of gains. Each closed loop request picks one with `withSlot(n)`, and uses slot 0 if it doesn't.

```java
public static VoltageMotorConfig LIFT_CONFIG = new VoltageMotorConfig()
        .withSlot0(new SlotConfig(0.8, 0, 0, 0.15, 0.12, 0.004, 0.35))    // empty
        .withSlot1(new SlotConfig(1.0, 0, 0, 0.15, 0.12, 0.006, 0.55));   // carrying a game piece

private final MotionMagicVoltage liftRequest = new MotionMagicVoltage(0);

// loop()
lift.set(liftRequest.withPosition(28).withSlot(hasGamePiece ? 1 : 0));
```

Some uses:

- **Loaded and empty.** A lift or arm with and without a game piece needs a different kG and kA.
- **Moving and holding.** Gentle gains for a Motion Magic move, stiffer ones for holding a position against bumps.
- **Big moves and tracking.** A turret swinging 180° wants different gains from one making small corrections while it follows a target.

Switching slots starts fresh, like switching controls: the integral and derivative are cleared.

### SlotConfig

| Field | Default | What it does |
|---|---|---|
| `kP`, `kI`, `kD`, `kS`, `kV`, `kA`, `kG` | 0 | Gains in volts. See [gains and tuning](gains-and-tuning.md) |
| `gravityType` | `ELEVATOR` | kG constant (`ELEVATOR`) or times cos(angle) (`ARM_COSINE`) |
| `staticSign` | `VELOCITY` | kS follows the commanded velocity (`VELOCITY`) or the error (`ERROR`) |

Constructors: `SlotConfig()`, `SlotConfig(kP, kI, kD, kS, kV, kA)` and `SlotConfig(kP, kI, kD, kS, kV, kA, kG)`. Each field also has a `with...` method: `withKP`, `withKG`, `withGravityType` and so on.

## ClosedLoopConfig

Shared by every slot.

| Field | Default | What it does |
|---|---|---|
| `staticDeadband` | 0 | With `StaticSign.ERROR`, no kS inside this error, so it doesn't chatter |
| `armHorizontalPosition` | 0 | `ARM_COSINE`: the position where the arm is level |
| `positionToRadians` | 1 | `ARM_COSINE`: `Math.PI / 180` for degrees |
| `integralZone` | +∞ | Only integrates while the error is smaller than this |
| `maxIntegralVolts` | 2 | The most volts kI can add |
| `derivativeFilter` | 1 | Smoothing on the error rate for kD, 0 to 1. Lower for noisy encoders |

`with...` methods: `withStaticDeadband`, `withArmHorizontal(position, toRadians)`, `withIntegralLimits(zone, maxVolts)`, `withDerivativeFilter`.

## FeedbackConfig

| Field | Default | What it does |
|---|---|---|
| `positionPerTick` | 1 | Encoder ticks to position units |
| `velocityPerTickPerSecond` | NaN (uses `positionPerTick`) | Ticks per second to velocity units. `60.0 / ticksPerRev` for RPM |

## MotorOutputConfig

| Field | Default | What it does |
|---|---|---|
| `neutralMode` | `BRAKE` | What `NeutralOut` does, and how the motors behave at zero output while running. `COAST` or `BRAKE` |
| `peakForwardVolts` | +∞ | The most forward volts sent |
| `peakReverseVolts` | -∞ | The most reverse volts sent, as a negative number. 0 for a flywheel |
| `powerDeadband` | 0.002 | Power changes smaller than this aren't written |

`with...` methods: `withNeutralMode`, `withPeakVolts(forward, reverse)`, `withPowerDeadband`.

## SoftLimitConfig

| Field | Default | What it does |
|---|---|---|
| `forward` | +∞ | At or past this position, forward output is cut |
| `reverse` | -∞ | At or past this position, reverse output is cut |

`withLimits(reverse, forward)` sets both. Soft limits only cut output in the direction that would go further, so the mechanism can always drive back.

## MotionMagicConfig

| Field | Default | What it does |
|---|---|---|
| `cruiseVelocity` | 0 | The fastest the profile moves, in position units per second |
| `acceleration` | 0 | How fast it speeds up and slows down. Also the default ramp rate for `MotionMagicVelocityVoltage` |

With both at 0, `MotionMagicVoltage` jumps straight to the goal like `PositionVoltage`.

Keep `cruiseVelocity` under what the motors can reach on a low battery, about `(10 - kS - kG) / kV`. Otherwise the profile asks for more than the motors have and falls behind.
