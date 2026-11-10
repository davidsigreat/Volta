# Gains and tuning

## The formula

```
volts = kS·sign(velocity) + kV·velocity + kA·acceleration + kG·gravity     feedforward
      + kP·error + kI·∫error + kD·d(error)/dt                              feedback
power = volts / battery voltage
```

**Feedforward** is what the mechanism needs to follow the plan, worked out ahead of time. **Feedback** fixes whatever the feedforward missed. When the feedforward is good, the feedback has little to do, and the mechanism is fast without overshooting.

Every gain is in **volts**, in the units you set with `positionPerTick`. So kV for a flywheel in RPM is volts per RPM, and kP for an arm in degrees is volts per degree.

| Gain | Volts per | Covers |
|---|---|---|
| `kS` | (fixed) | Static friction, in the direction of motion |
| `kV` | unit/s | Holding a speed (back-EMF) |
| `kA` | unit/s² | Changing speed (inertia) |
| `kG` | (fixed) | Gravity: constant (`ELEVATOR`) or times cos(angle) (`ARM_COSINE`) |
| `kP` | unit of error | What the feedforward missed |
| `kI` | unit of error × s | Steady error that won't go away |
| `kD` | unit of error / s | Damping |

## Tuning order

Tune the feedforward first, then the feedback. Start with every gain at 0.

### 1. kS and kV

Use `setVoltage()`. Hold each of 4 to 6 voltages, for example 2, 4, 6, 8 and 10 V, for a couple of seconds, and write down the steady velocity at each.

Fit a straight line through the points: **volts = kS + kV × velocity**. The slope is kV and the intercept is kS.

For a lift or arm, do this somewhere gravity doesn't matter (on its side, or with the arm moving through level), or tune kG first and subtract it.

### 2. kG

**Lift:** slowly raise the voltage until the lift just starts moving up, and call it V_up. Then lower it until the lift just starts sliding down, and call it V_down.

```
kG = (V_up + V_down) / 2
kS = (V_up - V_down) / 2
```

**Arm:** the voltage that holds the arm level. Set `gravityType = ARM_COSINE`, `armHorizontalPosition` to the level position, and `positionToRadians` (`Math.PI / 180` for degrees).

### 3. kA (optional)

Step the voltage, say from 0 to 8 V, and time how long the velocity takes to cover 63% of its change. Then **kA = kV × that time**.

Leave kA at 0 until everything else works. It matters most for ramps and profiles with high acceleration.

### 4. kP

With the feedforward on, raise kP until the error is small, stopping before it oscillates. A good starting guess is the volts you'd accept for 1 unit of error.

- **Overshoots or rings?** Add a little kD, or lower kP.
- **Settles a little short and stays there?** Add a small kI. `maxIntegralVolts` (2 V by default) stops it winding up.
- **Chatters at the target while holding?** For `setPosition` with `staticSign = ERROR`, raise `staticDeadband`.

### 5. Motion Magic

Set `cruiseVelocity` to about 80% of what the motors reach on a low battery:

```
cruiseVelocity ≈ 0.8 × (10 - kS - kG) / kV
```

Raise `acceleration` until the mechanism starts to lag the profile at the corners (watch `getPosition()` against `getSetpointPosition()`), then back off a bit.

## Watching the terms

Put each term on telemetry while tuning to see who's doing the work:

```java
PIDSVAController c = lift.getController();
telemetry.addData("FF V", c.getFeedforwardVolts());
telemetry.addData("Gravity V", c.getGravityVolts());
telemetry.addData("P V", c.getProportionalVolts());
telemetry.addData("I V", c.getIntegralVolts());
telemetry.addData("D V", c.getDerivativeVolts());
```

When it's well tuned, the feedforward carries almost all of it while moving, and P, I and D stay small.

## kS sign

`staticSign = VELOCITY` (default) pushes kS in the direction of the commanded velocity. Use it for velocity control and Motion Magic, where friction opposes the planned motion.

`staticSign = ERROR` pushes kS toward the target. Use it with `setPosition` and no profile, so kS helps the last bit of error break static friction. Set `staticDeadband` so it turns off right at the target.

## Converting old power gains

If you have gains tuned in power (-1 to 1), multiply each one by the battery voltage you tuned at. For gains tuned at about 12.5 V, multiply by 12.5. That's a good starting point in volts. Then retune kS and kV properly.
