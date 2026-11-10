# Controls

A `VoltageMotor` is one mechanism: a leader motor, any followers, and the control running them. Pick a control with a set method, then call `update()` once per loop.

| Method | What it does | Good for |
|---|---|---|
| `setDutyCycle(power)` | Power from -1 to 1, a fraction of the battery | Matching old code, quick tests |
| `setVoltage(volts)` | Open loop volts, the same on any battery | Intakes, drive-by-stick, tuning kS/kV |
| `setVelocity(velocity)` | Holds a velocity, stepping straight to it | Mechanisms that are already at speed |
| `setMotionMagicVelocity(velocity, acceleration)` | Ramps to a velocity at a set acceleration, then holds it | Flywheels, shooters |
| `setPosition(position)` | Holds a position, stepping straight to it | Small moves, holding still |
| `setMotionMagic(position)` | Moves along a trapezoid profile, then holds | Lifts, arms, turrets, slides |
| `neutral()` | No output, using `neutralMode` | Stopping |
| `coast()` | No output, spinning freely | Letting a flywheel spin down |
| `brake()` | No output, motor leads shorted | Holding a light mechanism still |

## How a loop works

```
setX(target)     chooses the control and target. Cheap, so call it as often as you like.
update()         reads the encoder
                 restarts the controller if the control changed
                 runs the control: volts = feedforward + PID
                 applies peak volts and soft limits
                 divides by the battery voltage and writes the motors
```

When the control **changes** (for example from `setVoltage` to `setMotionMagic`), Volta restarts from where the mechanism really is. The integral and derivative are cleared, and a ramp or profile starts at the measured position and velocity, so nothing jumps.

Changing only the **target** doesn't restart anything. A Motion Magic move picks up the new goal mid-move and replans from its current setpoint.

## Velocity controls

`setVelocity(v)` gives the controller `v` as both the setpoint and the kV feedforward. It's immediate, so a big change makes a big error and a big kP kick.

`setMotionMagicVelocity(v, a)` ramps the setpoint toward `v` at `a` units per second per second, starting from the measured speed. kA gets the ramp rate, so the motor gets the extra volts it needs to speed up. Use this for flywheels: a shot taken mid-ramp is easy to spot, and the battery doesn't sag from a step.

```java
flywheel.setMotionMagicVelocity(3200, 6000);   // to 3200 RPM at 6000 RPM/s
flywheel.isAtTarget(50);                       // within 50 RPM?
```

## Position controls

`setPosition(p)` runs PID on the position error with kG for gravity and no velocity feedforward. Fine for holding, harsh for big moves.

`setMotionMagic(p)` steps a trapezoid profile each loop: speed up at `motionMagic.acceleration`, cruise at `motionMagic.cruiseVelocity`, slow down to stop at `p`. The controller follows the profile's position, with kV and kA feedforward from its velocity and acceleration. So kP only has to fix what the feedforward missed.

```java
lift.motionMagic = new MotionMagicConfig(40, 120);
lift.setMotionMagic(28);
lift.getSetpointPosition();   // where the profile is now
lift.isAtTarget(0.5);         // profile done and within 0.5 in?
```

Keep `cruiseVelocity` under what the motors can reach on a low battery, about `(10 - kS - kG) / kV`. Otherwise the profile asks for more than the motors have and falls behind.

## Motor settings

Public fields. Set them once after construction, or change them live.

| Field | Default | What it does |
|---|---|---|
| `positionPerTick` | 1 | Encoder ticks to position units |
| `velocityPerTickPerSecond` | NaN (uses `positionPerTick`) | Ticks per second to velocity units. `60.0 / ticksPerRev` for RPM |
| `neutralMode` | `BRAKE` | What `neutral()` and the closed loop controls use at zero power |
| `peakForwardVolts` | +∞ | The most forward volts sent |
| `peakReverseVolts` | -∞ | The most reverse volts sent. 0 for a flywheel |
| `forwardSoftLimit` | +∞ | At or past this position, forward output is cut |
| `reverseSoftLimit` | -∞ | At or past this position, reverse output is cut |
| `powerDeadband` | 0.002 | Power changes smaller than this aren't written |
| `maxLoopSeconds` | 0.1 | Longest dt the control uses, so a slow first loop doesn't jump |
| `motionMagic` | 0, 0 | Cruise velocity and acceleration for `setMotionMagic` |

Soft limits cut output only in the direction that would go further, so the mechanism can always drive back.

## Controller settings

`motor.getController()` returns the `PIDSVAController`:

| Field | Default | What it does |
|---|---|---|
| `staticSign` | `VELOCITY` | kS follows the commanded velocity (`VELOCITY`) or the error (`ERROR`) |
| `staticDeadband` | 0 | With `ERROR`, no kS inside this error, so it doesn't chatter |
| `gravityType` | `ELEVATOR` | kG constant (`ELEVATOR`) or times cos(angle) (`ARM_COSINE`) |
| `armHorizontalPosition` | 0 | `ARM_COSINE`: the position where the arm is level |
| `positionToRadians` | 1 | `ARM_COSINE`: `Math.PI / 180` for degrees |
| `integralZone` | +∞ | Only integrates while the error is smaller than this |
| `maxIntegralVolts` | 2 | The most volts kI can add |
| `derivativeFilter` | 1 | Smoothing on the error rate for kD. Lower for noisy encoders |

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
| `getTarget()` | The last target set |
| `getSetpointPosition()`, `getSetpointVelocity()` | Where the profile or ramp is now |
| `getVolts()` | Volts asked for after limits |
| `getPower()` | Power last written |
| `getMode()` | The current control |
| `isAtTarget(tolerance)` | Within tolerance (and, for Motion Magic, the profile finished) |
| `getController().getProportionalVolts()` etc. | Each term, for tuning on telemetry |
