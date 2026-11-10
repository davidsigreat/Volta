# Changelog

## 0.1.0 (unreleased)

First version, extracted from FTC 22536 BIOBUZZ's 2026 robot code.

- `VoltageMotor`: duty cycle, voltage, velocity, Motion Magic velocity, position and Motion Magic controls; neutral, coast and brake; followers; peak voltages; soft limits; position reset; skipped tiny writes.
- `PIDSVAController` and `PIDSVAGains`: kS/kV/kA/kG feedforward and PID feedback in volts, with elevator and arm cosine gravity, velocity or error kS sign, integral zone and cap, and a filtered derivative.
- `TrapezoidProfile` and `MotionMagicConfig`: Motion Magic profiles that restart cleanly when the goal changes.
- `SlewRateLimiter` and `BatteryVoltage`.
- Javadoc on every public class, method and field, and guides in `docs/`.
- Unit tests for the controller, profile and ramp.
