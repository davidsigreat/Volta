# API reference

Every public member also has Javadoc, so you can hover over it in Android Studio.

**`io.github.davidsigreat.volta`**
- [VoltageMotor](#voltagemotor)
- [PIDSVAController](#pidsvacontroller)
- [TrapezoidProfile](#trapezoidprofile)
- [SlewRateLimiter](#slewratelimiter)
- [BatteryVoltage](#batteryvoltage)

**`io.github.davidsigreat.volta.controls`**
- [Control requests](#control-requests)

**`io.github.davidsigreat.volta.configs`**
- [Configuration classes](#configuration-classes)

`BatteryVoltage` and `VoltageMotor` use the FTC SDK. Everything else is plain Java and can be unit tested on a computer.

---

## VoltageMotor

One mechanism: a leader motor, its followers, and the control that drives them.

```java
VoltageMotor(BatteryVoltage battery, VoltageMotorConfig config, DcMotorEx leader, DcMotorEx... followers)
VoltageMotor(BatteryVoltage battery, DcMotorEx leader, DcMotorEx... followers)   // default config
```

The constructor puts every motor in `RUN_WITHOUT_ENCODER`, because the loop runs in Volta, not on the hub. The leader's encoder still reads.

| Method | Description |
|---|---|
| `void set(ControlRequest request)` | Runs this request from the next `update()` on. Copies its values. |
| `void update()` | Reads the encoder, runs the control, writes the motors. Once per loop. |
| `void apply(VoltageMotorConfig config)` | Switches to another configuration, starting fresh |
| `VoltageMotorConfig getConfig()` | The configuration in use. Edit its fields to change things live. |
| `void resetPosition(double position)` | Makes the current spot read as `position` |
| `boolean isAtTarget(double tolerance)` | Within tolerance of the target. Motion Magic also needs the profile finished. Always false for open loop and neutral. |
| `ControlMode getControlMode()` | The current control |
| `int getSlotNumber()` | The gain slot in use |
| `double getTarget()` | The last request's value |
| `double getPosition()` | Measured position |
| `double getVelocity()` | Measured velocity |
| `double getSetpointPosition()` | Profile position (Motion Magic), otherwise the target |
| `double getSetpointVelocity()` | Profile or ramp velocity, otherwise the target |
| `double getVolts()` | Volts asked for, after limits |
| `double getPower()` | Power last written |
| `PIDSVAController getController()` | The controller, for per-term telemetry |
| `BatteryVoltage getBattery()` | The battery reading |

---

## Control requests

All extend `ControlRequest`, which has `getControlMode()`, `getValue()`, `getFeedforwardVelocity()`, `getAcceleration()` and `getSlot()`.

| Class | Constructor | Fields and `with...` methods | `ControlMode` |
|---|---|---|---|
| `DutyCycleOut` | `(output)` | `output` | `DUTY_CYCLE` |
| `VoltageOut` | `(output)` | `output` (volts) | `VOLTAGE` |
| `VelocityVoltage` | `(velocity)` | `velocity`, `acceleration`, `slot` | `VELOCITY` |
| `MotionMagicVelocityVoltage` | `(velocity)` | `velocity`, `acceleration` (0 uses the config's), `slot` | `MOTION_MAGIC_VELOCITY` |
| `PositionVoltage` | `(position)` | `position`, `velocity` (feedforward), `slot` | `POSITION` |
| `MotionMagicVoltage` | `(position)` | `position`, `slot` | `MOTION_MAGIC` |
| `NeutralOut` | `()` | | `NEUTRAL` |
| `CoastOut` | `()` | | `COAST` |
| `StaticBrake` | `()` | | `BRAKE` |

Each field has a matching `with...` method that sets it and returns the request: `withOutput`, `withVelocity`, `withAcceleration`, `withPosition`, `withSlot`.

---

## Configuration classes

See [configuration](configuration.md) for what each setting does.

| Class | Fields |
|---|---|
| `VoltageMotorConfig` | `slot0`, `slot1`, `slot2`, `closedLoop`, `feedback`, `motorOutput`, `softLimits`, `motionMagic`, `maxLoopSeconds`; `getSlot(int)` |
| `SlotConfig` | `kP`, `kI`, `kD`, `kS`, `kV`, `kA`, `kG`, `gravityType`, `staticSign` |
| `ClosedLoopConfig` | `staticDeadband`, `armHorizontalPosition`, `positionToRadians`, `integralZone`, `maxIntegralVolts`, `derivativeFilter` |
| `FeedbackConfig` | `positionPerTick`, `velocityPerTickPerSecond` |
| `MotorOutputConfig` | `neutralMode`, `peakForwardVolts`, `peakReverseVolts`, `powerDeadband` |
| `SoftLimitConfig` | `forward`, `reverse` |
| `MotionMagicConfig` | `cruiseVelocity`, `acceleration` |

| Enum | Values |
|---|---|
| `GravityType` | `ELEVATOR`, `ARM_COSINE` |
| `StaticSign` | `VELOCITY`, `ERROR` |
| `NeutralMode` | `COAST`, `BRAKE` |

Every field is public, and each class has `with...` methods that set fields and return the object, for building a configuration in one expression.

---

## PIDSVAController

Feedforward + PID in volts. Keeps state (integral, last error), so use one per mechanism.

```java
PIDSVAController(SlotConfig slot)
PIDSVAController(SlotConfig slot, ClosedLoopConfig closedLoop)
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
| `getSlot()`, `setSlot(SlotConfig)` | The gains in use. Setting doesn't reset. |
| `getClosedLoop()`, `setClosedLoop(ClosedLoopConfig)` | The shared settings in use |
| `getFeedforwardVolts()`, `getGravityVolts()`, `getProportionalVolts()`, `getIntegralVolts()`, `getDerivativeVolts()` | Each term of the last calculation. Feedforward includes gravity. |
| `double getLastError()` | The last error |

`dt` is in seconds. With `dt <= 0`, the integral and derivative don't update. The slot and closed loop settings are read every calculation.

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
