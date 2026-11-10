package io.github.davidsigreat.volta;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.VoltageSensor;

/**
 * Battery voltage for voltage mode: turns volts from a PIDSVAController into motor power.
 * Power is a fraction of whatever the battery has, so volts / battery gives the motor the same
 * volts on a full or a flat battery.
 * <p>
 * Each sensor read costs loop time, so it reads at most every readIntervalSeconds and smooths the
 * readings. The floor stops one bad reading from spiking every output. One per mechanism is fine;
 * they each read on their own interval.
 */
public class BatteryVoltage {
    private final VoltageSensor sensor;
    private long lastReadNanos = System.nanoTime();

    /** The shortest time between sensor reads, in seconds. */
    public double readIntervalSeconds = 0.5;
    /**
     * Smoothing per read, 0 to 1. 1 is no smoothing.
     */
    public double filter = 0.5;
    /** Readings below this are raised to it, so one bad reading can't spike every output. */
    public double minVolts = 9;

    private double volts;
    // 1 / volts, so turning volts into power is a multiply, not a divide.
    private double inverseVolts;

    /** Uses the first voltage sensor in the hardware map, and reads it once now. */
    public BatteryVoltage(HardwareMap hardwareMap) {
        sensor = hardwareMap.voltageSensor.iterator().next();
        volts = Math.max(sensor.getVoltage(), minVolts);
        inverseVolts = 1 / volts;
    }

    /**
     * The smoothed voltage, reading the sensor first if it's been long enough.
     */
    public double get() {
        refresh();
        return volts;
    }

    /**
     * 1 / get().
     */
    public double getInverse() {
        refresh();
        return inverseVolts;
    }

    private void refresh() {
        long now = System.nanoTime();
        if ((now - lastReadNanos) * 1e-9 < readIntervalSeconds) {
            return;
        }
        lastReadNanos = now;
        double reading = Math.max(sensor.getVoltage(), minVolts);
        volts += filter * (reading - volts);
        inverseVolts = 1 / volts;
    }

    /**
     * Motor power for a voltage, from -1 to 1.
     */
    public double toPower(double outputVolts) {
        return Math.max(-1, Math.min(1, outputVolts * getInverse()));
    }
}
