# Performance

On a REV Control Hub, the math in your loop is cheap. **Hardware calls** are what cost time: each one is a round trip to the hub's microcontroller, about 1 to 2.5 ms. A loop that makes 8 of them runs at 50 Hz or slower, however fast the code is.

## What Volta does

| Cost | How Volta handles it |
|---|---|
| Encoder reads | One `getCurrentPosition()` and one `getVelocity()` per mechanism, from the leader only. With bulk caching on, both are free. |
| Motor writes | `setPower()` is skipped when the change is smaller than `powerDeadband` (0.002). Stopping and full power always go through. A mechanism holding still makes almost no writes. |
| Zero power behavior | Written only when it changes, not every loop |
| Battery voltage | The sensor is read at most every 0.5 s, and the reading is smoothed |
| Mode changes | The motors are put in `RUN_WITHOUT_ENCODER` once, at construction. `resetPosition()` uses an offset instead of resetting the encoder. |
| Garbage | Nothing is allocated in `update()`, so the garbage collector doesn't pause the loop |
| Math | Divisions on hot paths are multiplications by a cached inverse |

## What you should do

1. **Turn on bulk caching** in `MANUAL` mode, and call `clearBulkCache()` once at the top of every loop, on every hub. Without it, every encoder read is its own round trip.
2. **Share one `BatteryVoltage`** across mechanisms.
3. **Call `update()` once per loop** per mechanism. Calling it twice costs twice as much and gives the controller a tiny dt.
4. **Don't rewrite unchanged values** elsewhere in your code. The SDK only skips a write when the value is exactly the same as last time.
5. **Send telemetry less often.** Panels and FTC Dashboard packets are slow to build. Every 100 ms is plenty for a human.

## Measuring

```java
long start = System.nanoTime();
lift.update();
telemetry.addData("Lift ms", (System.nanoTime() - start) * 1e-6);
```

Measure the whole loop the same way. A hub-only robot with a drivetrain, a couple of mechanisms and bulk caching should run at about 5 to 15 ms per loop.
