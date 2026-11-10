# Controls

A `VoltageMotor` is one mechanism: a leader motor, any followers, and the control running them. Give it a control request with `set()`, then call `update()` once per loop.

```java
motor.set(new VelocityVoltage(3200));
motor.update();
```

All requests are in `io.github.davidsigreat.volta.controls`.

| Request | What it does | Good for |
|---|---|---|
| `DutyCycleOut(power)` | Power from -1 to 1, a fraction of the battery | Matching old code, quick tests |
| `VoltageOut(volts)` | Open loop volts, the same on any battery | Intakes, drive-by-stick, tuning kS/kV |
| `VelocityVoltage(velocity)` | Holds a velocity, stepping straight to it | Mechanisms that are already at speed |
| `MotionMagicVelocityVoltage(velocity)` | Ramps to a velocity at a set acceleration, then holds it | Flywheels, shooters |
| `PositionVoltage(position)` | Holds a position, stepping straight to it | Small moves, holding still, tracking |
| `MotionMagicVoltage(position)` | Moves along a trapezoid profile, then holds | Lifts, arms, turrets, slides |
| `NeutralOut()` | No output, using the configured `neutralMode` | Stopping |
| `CoastOut()` | No output, spinning freely | Letting a flywheel spin down |
| `StaticBrake()` | No output, motor leads shorted | Holding a light mechanism still |

## Reusing requests

Every request has `with...` methods that change it and return it, so one request object per mechanism is all you need:

```java
private final MotionMagicVoltage liftRequest = new MotionMagicVoltage(0);

// loop()
lift.set(liftRequest.withPosition(targetHeight));
```

`set()` copies the values out of the request. Changing the request later does nothing until you `set()` it again.

## Request options

| Request | Options |
|---|---|
| `DutyCycleOut` | `withOutput(power)` |
| `VoltageOut` | `withOutput(volts)` |
| `VelocityVoltage` | `withVelocity(v)`, `withAcceleration(a)` for kA feedforward, `withSlot(n)` |
| `MotionMagicVelocityVoltage` | `withVelocity(v)`, `withAcceleration(a)` for the ramp rate (0 uses the config's), `withSlot(n)` |
| `PositionVoltage` | `withPosition(p)`, `withVelocity(v)` for kV feedforward, `withSlot(n)` |
| `MotionMagicVoltage` | `withPosition(p)`, `withSlot(n)` |

`withSlot(n)` picks gain slot 0, 1 or 2. See [configuration](configuration.md#gain-slots).

## How a loop works

```
set(request)     records the control, target and slot. Cheap, so call it as often as you like.
update()         reads the encoder
                 starts fresh if the control or slot changed
                 runs the control: volts = feedforward + PID
                 applies peak volts and soft limits
                 divides by the battery voltage and writes the motors
```

When the **control or slot changes** (for example from `VoltageOut` to `MotionMagicVoltage`), Volta starts fresh from where the mechanism really is. The integral and derivative are cleared, and a ramp or profile starts at the measured position and velocity, so nothing jumps.

Changing only the **target** doesn't restart anything. A Motion Magic move picks up the new goal mid-move and replans from its current setpoint.

## Velocity controls

`VelocityVoltage(v)` gives the controller `v` as both the setpoint and the kV feedforward. It's immediate, so a big change makes a big error and a big kP kick.

`MotionMagicVelocityVoltage(v)` ramps the setpoint toward `v`, starting from the measured speed. The ramp rate is the request's `acceleration`, or the configuration's `motionMagic.acceleration` if that's 0. kA gets the ramp rate, so the motor gets the extra volts it needs to speed up. Use this for flywheels: the battery doesn't sag from a step, and the mechanism is ready when the ramp reaches the target.

```java
flywheel.set(new MotionMagicVelocityVoltage(3200).withAcceleration(6000));   // RPM, RPM/s
flywheel.isAtTarget(50);   // within 50 RPM?
```

## Position controls

`PositionVoltage(p)` runs PID on the position error, with kG for gravity. Fine for holding, harsh for big moves. When the target is moving, like a turret tracking a goal while the robot drives, pass its velocity with `withVelocity(v)`. kV then drives the mechanism along with the target instead of waiting for an error to build up.

`MotionMagicVoltage(p)` steps a trapezoid profile each loop: speed up at `motionMagic.acceleration`, cruise at `motionMagic.cruiseVelocity`, slow down to stop at `p`. The controller follows the profile's position, with kV and kA feedforward from its velocity and acceleration. So kP only has to fix what the feedforward missed.

```java
lift.set(liftRequest.withPosition(28));
lift.getSetpointPosition();   // where the profile is now
lift.isAtTarget(0.5);         // profile done and within 0.5 in?
```

## Homing

`resetPosition(p)` makes the current spot read as `p`, without resetting the encoder. Resetting the encoder costs a mode change that stops the motors.

```java
if (limitSwitch.isPressed()) {
    lift.resetPosition(0);
}
```

## Reading state

| Method | Returns |
|---|---|
| `getPosition()`, `getVelocity()` | Measured, in your units |
| `getTarget()` | The last request's value |
| `getSetpointPosition()`, `getSetpointVelocity()` | Where the profile or ramp is now |
| `getVolts()` | Volts asked for after limits |
| `getPower()` | Power last written |
| `getControlMode()` | The current control |
| `getSlotNumber()` | The gain slot in use |
| `isAtTarget(tolerance)` | Within tolerance (and, for Motion Magic, the profile finished) |
| `getController().getProportionalVolts()` etc. | Each term, for tuning on telemetry |
