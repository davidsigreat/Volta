# API reference

All classes are in `io.github.davidsigreat.volta`. Every public member also has Javadoc, so you can hover over it in Android Studio.

- [VoltageMotor](#voltagemotor)
- [PIDSVAController](#pidsvacontroller)
- [PIDSVAGains](#pidsvagains)
- [MotionMagicConfig](#motionmagicconfig)
- [TrapezoidProfile](#trapezoidprofile)
- [SlewRateLimiter](#slewratelimiter)
- [BatteryVoltage](#batteryvoltage)

`BatteryVoltage` and `VoltageMotor` use the FTC SDK. The rest are plain Java and can be unit tested on a computer.

---

## VoltageMotor

One mechanism: a leader motor, its followers, and the control that drives them.

```java
VoltageMotor(BatteryVoltage battery, PIDSVAGains gains, DcMotorEx leader, DcMotorEx... followers)
```

The constructor puts every motor in `RUN_WITHOUT_ENCODER`, because the loop runs in Volta, not on the hub. The leader's encoder still reads.

### Controls

| Method | Mode |
|---|---|
| `void setDutyCycle(double power)` | `DUTY_CYCLE` |
| `void setVoltage(double volts)` | `VOLTAGE` |
| `void setVelocity(double velocity)` | `VELOCITY` |
| `void setMotionMagicVelocity(double velocity, double acceleration)` | `MOTION_MAGIC_VELOCITY` |
| `void setPosition(double position)` | `POSITION` |
| `void setMotionMagic(double position)` | `MOTION_MAGIC` |
| `void neutral()` | `NEUTRAL`, using `neutralMode` |
| `void coast()` | `NEUTRAL`, `FLOAT` |
| `void brake()` | `NEUTRAL`, `BRAKE` |

### Loop and state

| Method | Description |
|---|---|
| `void update()` | Reads the encoder, runs the control, writes the motors. Once per loop. |
| `void resetPosition(double position)` | Makes the current spot read as `position` |
| `boolean isAtTarget(double tolerance)` | Within tolerance of the target. Motion Magic also needs the profile finished. Always false for open loop and neutral. |
| `Mode getMode()` | The current control |
| `double getTarget()` | The last target |
| `double getPosition()` | Measured position |
| `double getVelocity()` | Measured velocity |
| `double getSetpointPosition()` | Profile position (Motion Magic), otherwise the target |
| `double getSetpointVelocity()` | Profile or ramp velocity, otherwise the target |
| `double getVolts()` | Volts asked for, after limits |
| `double getPower()` | Power last written |
| `PIDSVAController getController()` | The controller, for settings and per-term telemetry |
| `BatteryVoltage getBattery()` | The battery reading |

### Fields

| Field | Type | Default |
|---|---|---|
| `positionPerTick` | `double` | 1 |
| `velocityPerTickPerSecond` | `double` | NaN (uses `positionPerTick`) |
| `neutralMode` | `DcMotor.ZeroPowerBehavior` | `BRAKE` |
| `peakForwardVolts` | `double` | +∞ |
| `peakReverseVolts` | `double` | -∞ |
| `forwardSoftLimit` | `double` | +∞ |
| `reverseSoftLimit` | `double` | -∞ |
| `powerDeadband` | `double` | 0.002 |
| `maxLoopSeconds` | `double` | 0.1 |
| `motionMagic` | `MotionMagicConfig` | 0, 0 |

See [controls](controls.md#motor-settings) for what each one does.

---

## PIDSVAController

Feedforward + PID in volts. Keeps state (integral, last error), so use one per mechanism.

```java
PIDSVAController(PIDSVAGains gains)
```

| Method | Description |
|---|---|
| `double calculate(target, measured, velocity, acceleration, position, dt)` | The general form. Feedback on `target - measured`, feedforward on `velocity` and `acceleration`, gravity at `position`. Returns volts. |
| `double calculateVelocity(targetVelocity, measuredVelocity, targetAcceleration, dt)` | Velocity control. The target velocity is also the kV feedforward. |
| `double calculateVelocity(targetVelocity, measuredVelocity, targetAcceleration, measuredPosition, dt)` | The same, with a position for `ARM_COSINE` gravity |
| `double calculatePosition(targetPosition, measuredPosition, targetVelocity, targetAcceleration, dt)` | Position control, with feedforward from a profile, or 0 and 0 to just hold |
| `double feedforward(velocity, acceleration, position)` | Just the feedforward, for open loop |
| `double gravity(position)` | Just kG at a position |
| `void reset()` | Clears the integral and error history |
| `PIDSVAGains getGains()` | The gains |
| `getFeedforwardVolts()`, `getGravityVolts()`, `getProportionalVolts()`, `getIntegralVolts()`, `getDerivativeVolts()` | Each term of the last calculation. Feedforward includes gravity. |
| `double getLastError()` | The last error |

`dt` is in seconds. With `dt <= 0`, the integral and derivative don't update.

| Field | Type | Default |
|---|---|---|
| `staticSign` | `StaticSign` (`VELOCITY`, `ERROR`) | `VELOCITY` |
| `staticDeadband` | `double` | 0 |
| `integralZone` | `double` | +∞ |
| `maxIntegralVolts` | `double` | 2 |
| `derivativeFilter` | `double`, 0 to 1 | 1 (no filter) |
| `gravityType` | `GravityType` (`ELEVATOR`, `ARM_COSINE`) | `ELEVATOR` |
| `armHorizontalPosition` | `double` | 0 |
| `positionToRadians` | `double` | 1 |

---

## PIDSVAGains

Seven public `double` fields: `kP`, `kI`, `kD`, `kS`, `kV`, `kA`, `kG`, all in volts.

```java
PIDSVAGains()                                      // all 0
PIDSVAGains(kP, kI, kD, kS, kV, kA)                // kG = 0
PIDSVAGains(kP, kI, kD, kS, kV, kA, kG)
```

The controller reads the fields every time it calculates, so editing them live (Panels, FTC Dashboard) takes effect on the next loop.

---

## MotionMagicConfig

```java
MotionMagicConfig()                                // 0, 0: jump straight to the goal
MotionMagicConfig(cruiseVelocity, acceleration)
```

| Field | Units |
|---|---|
| `cruiseVelocity` | position units per second |
| `acceleration` | position units per second² |

---

## TrapezoidProfile

Static methods only.

| Method | Description |
|---|---|
| `static void step(double t, State current, State goal, double maxVelocity, double maxAcceleration, State out)` | Where the motion is `t` seconds after `current`. `out` can be `current`. With a limit of 0 or less, jumps to the goal. |
| `static boolean isFinished(State setpoint, State goal)` | Reached the goal and stopped |

`TrapezoidProfile.State` has public `position`, `velocity` and `acceleration` fields, the constructors `State()` and `State(position, velocity)`, and `set(position, velocity, acceleration)`.

---

## SlewRateLimiter

```java
SlewRateLimiter(double maxRate, double startValue)
```

| Member | Description |
|---|---|
| `double maxRate` | Units per second, changeable live |
| `double calculate(double target, double dt)` | Steps toward `target` and returns the new value |
| `void reset(double value)` | Jumps to a value, with no rate |
| `double getValue()` | The current value |
| `double getRate()` | How fast it moved on the last step, per second |

---

## BatteryVoltage

```java
BatteryVoltage(HardwareMap hardwareMap)   // first voltage sensor; reads once now
```

| Member | Description |
|---|---|
| `double get()` | Smoothed voltage, reading the sensor if `readIntervalSeconds` has passed |
| `double getInverse()` | `1 / get()` |
| `double toPower(double volts)` | Volts to motor power, clipped to -1 to 1 |
| `double readIntervalSeconds` | Default 0.5 |
| `double filter` | Smoothing per read, 0 to 1. Default 0.5 |
| `double minVolts` | Floor on readings. Default 9 |
