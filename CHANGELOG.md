# Changelog

## 0.1.0 (unreleased)

First version, extracted from FTC 22536 BIOBUZZ's 2026 robot code.

- `VoltageMotor` with one `set(request)` for every control, and `update()` once per loop. Followers, peak voltages, soft limits, position reset, and skipped tiny writes.
- Control requests: `DutyCycleOut`, `VoltageOut`, `VelocityVoltage`, `MotionMagicVelocityVoltage`, `PositionVoltage`, `MotionMagicVoltage`, `NeutralOut`, `CoastOut` and `StaticBrake`.
- `VoltageMotorConfig`, read live every loop, with three gain slots (`SlotConfig`) and `ClosedLoopConfig`, `FeedbackConfig`, `MotorOutputConfig`, `SoftLimitConfig` and `MotionMagicConfig`.
- `PIDSVAController`: kS/kV/kA/kG feedforward and PID feedback in volts, with elevator and arm cosine gravity, velocity or error kS sign, integral zone and cap, and a filtered derivative.
- `TrapezoidProfile`: Motion Magic profiles that restart cleanly when the goal changes.
- `SlewRateLimiter` and `BatteryVoltage`.
- Javadoc on every public class, method and field, and guides in `docs/`.
- Unit tests for the controller, profile, ramp, control requests and slots.
