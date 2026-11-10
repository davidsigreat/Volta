# VoltageControl

Phoenix 6 style motor control for FTC, in volts.

If you've used a TalonFX with `VelocityVoltage` or `MotionMagicVoltage`, this is the same idea on a REV hub: pick a control, give it a target, and a feedforward + PID loop drives the motor in **volts**, so the same gains work on a full battery and a flat one.

```java
lift.setMotionMagic(28);   // move to 28 inches along a smooth profile
lift.update();             // once per loop
```

> **Status: 0.1.0, early.** The math is covered by unit tests and simulations. Hardware testing on our robot (FTC 22536 BIOBUZZ) is in progress. Expect small API changes before 1.0.

## Why volts

`setPower()` is a fraction of whatever the battery has right now. A gain tuned at 13.5 V gives a different speed at 12 V, so flywheels shoot short and lifts sag as the match goes on. VoltageControl works out the volts the mechanism needs and divides by the measured battery voltage, like Phoenix `VoltageOut`. Tune once, and it holds.

## Install

Add JitPack and the library to `build.dependencies.gradle`:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.davidsigreat:VoltageControl:v0.1.0'
}
```

The FTC SDK is a `compileOnly` dependency, so the library uses the SDK version your project already has. Built for FTC SDK 12.0 (Java 8, min SDK 24).

## Controls

| Phoenix 6 | VoltageMotor |
|---|---|
| `DutyCycleOut` | `setDutyCycle(power)` |
| `VoltageOut` | `setVoltage(volts)` |
| `VelocityVoltage` | `setVelocity(velocity)` |
| `MotionMagicVelocityVoltage` | `setMotionMagicVelocity(velocity, acceleration)` |
| `PositionVoltage` | `setPosition(position)` |
| `MotionMagicVoltage` | `setMotionMagic(position)` |
| `NeutralOut` / `CoastOut` / `StaticBrake` | `neutral()` / `coast()` / `brake()` |
| `Follower` / `StrictFollower` | Every motor passed in after the first |
| Peak forward/reverse voltage | `peakForwardVolts`, `peakReverseVolts` |
| Soft limits | `forwardSoftLimit`, `reverseSoftLimit` |
| `NeutralModeValue` | `neutralMode` |
| `setPosition()` (sensor) | `resetPosition(position)` |

Set methods only choose the control, so call them as often as you like. `update()` reads the encoder, runs the control and writes the motors.

Not included: current and FOC controls. FTC motors are brushed, and hub current readings are too slow to close a loop on.

## Example: a two-motor lift

```java
@Configurable // Panels; use @Config for FTC Dashboard. Makes the gains editable live.
@TeleOp(name = "Lift Example")
public class LiftExample extends OpMode {
    public static PIDSVAGains LIFT_GAINS = new PIDSVAGains(
            0.8,    // kP: volts per inch of error
            0,      // kI
            0,      // kD
            0.15,   // kS: volts to break friction
            0.12,   // kV: volts per inch/s
            0.004,  // kA: volts per inch/s^2
            0.35);  // kG: volts to hold against gravity
    public static MotionMagicConfig LIFT_MOTION = new MotionMagicConfig(40, 120); // in/s, in/s^2

    private List<LynxModule> hubs;
    private VoltageMotor lift;

    @Override
    public void init() {
        // Bulk caching makes every encoder read on a hub one transaction per loop.
        hubs = hardwareMap.getAll(LynxModule.class);
        for (LynxModule hub : hubs) {
            hub.setBulkCachingMode(LynxModule.BulkCachingMode.MANUAL);
        }

        DcMotorEx left = hardwareMap.get(DcMotorEx.class, "liftLeft");   // has the encoder
        DcMotorEx right = hardwareMap.get(DcMotorEx.class, "liftRight");
        right.setDirection(DcMotorSimple.Direction.REVERSE);

        lift = new VoltageMotor(new BatteryVoltage(hardwareMap), LIFT_GAINS, left, right);
        lift.positionPerTick = Math.PI * 1.5 / 384.5;  // inches per tick: spool circumference / ticks per rev
        lift.motionMagic = LIFT_MOTION;
        lift.reverseSoftLimit = 0;
        lift.forwardSoftLimit = 30;
    }

    @Override
    public void loop() {
        for (LynxModule hub : hubs) {
            hub.clearBulkCache();
        }

        if (gamepad1.a) lift.setMotionMagic(28);
        if (gamepad1.b) lift.setMotionMagic(0);
        lift.update();

        telemetry.addData("Position", lift.getPosition());
        telemetry.addData("Setpoint", lift.getSetpointPosition());
        telemetry.addData("Volts", lift.getVolts());
    }
}
```

A flywheel looks the same with `setMotionMagicVelocity(targetRpm, rampRpmPerSecond)`, `velocityPerTickPerSecond = 60.0 / ticksPerRev` to read RPM, `peakReverseVolts = 0` so it never drives backwards, and `neutralMode = FLOAT` so it coasts.

## Gains

All gains are in volts, in the units you set with `positionPerTick` (inches, degrees, revolutions...).

| Gain | Volts per | Covers |
|---|---|---|
| `kS` | (fixed) | Static friction, in the direction of motion |
| `kV` | unit/s | Holding a speed (back-EMF) |
| `kA` | unit/s² | Changing speed (inertia) |
| `kG` | (fixed) | Gravity: constant (`ELEVATOR`) or times cos(angle) (`ARM_COSINE`) |
| `kP` | unit of error | What the feedforward missed |
| `kI` | unit of error × s | Steady error that won't go away |
| `kD` | unit of error / s | Damping |

Controller settings (`motor.getController()`):

- `staticSign`: `VELOCITY` (kS follows the commanded motion) or `ERROR` (kS pushes toward the target, for holding without a profile), with `staticDeadband` to stop chatter at the target.
- `gravityType`: `ELEVATOR` or `ARM_COSINE`, with `armHorizontalPosition` and `positionToRadians` (`Math.PI / 180` for degrees).
- `integralZone` and `maxIntegralVolts` (default 2 V) to stop windup.
- `derivativeFilter`: 0 to 1, lower for noisy encoders.

## Tuning

1. **kS and kV.** With `setVoltage()`, hold 4 to 6 voltages for a couple of seconds each and record the steady speed. Fit a line: volts = kS + kV × speed. For a lift or arm, do this on the horizontal, or subtract kG first.
2. **kG.** Lift: find the voltage that just starts it moving up and the one that just lets it move down. kG is their average, and kS is half their difference. Arm: the voltage that holds it level.
3. **kA.** Optional. Step the voltage and time how fast it speeds up: kA = kV × the time it takes to cover 63% of the change. Leave it at 0 until the rest works.
4. **kP**, with feedforward on. Raise it until the error is small without oscillation. Add **kD** if it overshoots, and a little **kI** only if it settles short.
5. **Motion Magic.** Keep `cruiseVelocity` under what the motors can reach on a low battery, about (10 V - kS - kG) / kV.

## Tips

- Turn on bulk caching (MANUAL, cleared each loop). Encoder reads are then free.
- `VoltageMotor` skips motor writes smaller than `powerDeadband` (0.002), since each write costs about 1 to 2 ms of loop time.
- `BatteryVoltage` reads the sensor at most every 0.5 s. Share one between mechanisms if you like.
- Everything except `VoltageMotor` and `BatteryVoltage` is plain Java with no SDK calls, so it can be unit tested on a computer.

## Classes

| Class | What it is |
|---|---|
| `VoltageMotor` | A motor and its followers, with every control above |
| `PIDSVAController` | The feedforward + PID loop, usable on its own |
| `PIDSVAGains` | kP, kI, kD, kS, kV, kA, kG |
| `TrapezoidProfile` | The motion behind Motion Magic (WPILib's algorithm, allocation free) |
| `MotionMagicConfig` | Cruise velocity and acceleration |
| `SlewRateLimiter` | Ramps a setpoint, and reports the rate for kA |
| `BatteryVoltage` | Filtered battery voltage, and volts to power |

## License

MIT. See [LICENSE](LICENSE).
