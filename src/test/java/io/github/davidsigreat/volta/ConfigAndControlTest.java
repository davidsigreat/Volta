package io.github.davidsigreat.volta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

import io.github.davidsigreat.volta.configs.ClosedLoopConfig;
import io.github.davidsigreat.volta.configs.SlotConfig;
import io.github.davidsigreat.volta.configs.VoltageMotorConfig;
import io.github.davidsigreat.volta.controls.CoastOut;
import io.github.davidsigreat.volta.controls.ControlMode;
import io.github.davidsigreat.volta.controls.DutyCycleOut;
import io.github.davidsigreat.volta.controls.MotionMagicVelocityVoltage;
import io.github.davidsigreat.volta.controls.MotionMagicVoltage;
import io.github.davidsigreat.volta.controls.NeutralOut;
import io.github.davidsigreat.volta.controls.PositionVoltage;
import io.github.davidsigreat.volta.controls.StaticBrake;
import io.github.davidsigreat.volta.controls.VelocityVoltage;
import io.github.davidsigreat.volta.controls.VoltageOut;

public class ConfigAndControlTest {

    @Test
    public void requestsReportTheirControlAndValues() {
        assertEquals(ControlMode.DUTY_CYCLE, new DutyCycleOut(0.5).getControlMode());
        assertEquals(0.5, new DutyCycleOut(0.5).getValue(), 0);
        assertEquals(ControlMode.VOLTAGE, new VoltageOut(6).getControlMode());
        assertEquals(ControlMode.NEUTRAL, new NeutralOut().getControlMode());
        assertEquals(ControlMode.COAST, new CoastOut().getControlMode());
        assertEquals(ControlMode.BRAKE, new StaticBrake().getControlMode());
        assertEquals(0, new VoltageOut(6).getSlot());

        VelocityVoltage velocity = new VelocityVoltage(3200).withAcceleration(100).withSlot(1);
        assertEquals(ControlMode.VELOCITY, velocity.getControlMode());
        assertEquals(3200, velocity.getValue(), 0);
        assertEquals(100, velocity.getAcceleration(), 0);
        assertEquals(1, velocity.getSlot());

        MotionMagicVelocityVoltage ramp = new MotionMagicVelocityVoltage(3000).withAcceleration(6000);
        assertEquals(ControlMode.MOTION_MAGIC_VELOCITY, ramp.getControlMode());
        assertEquals(6000, ramp.getAcceleration(), 0);

        PositionVoltage position = new PositionVoltage(90).withVelocity(45).withSlot(2);
        assertEquals(ControlMode.POSITION, position.getControlMode());
        assertEquals(90, position.getValue(), 0);
        assertEquals(45, position.getFeedforwardVelocity(), 0);
        assertEquals(2, position.getSlot());

        MotionMagicVoltage motionMagic = new MotionMagicVoltage(0);
        assertSame(motionMagic, motionMagic.withPosition(28));
        assertEquals(28, motionMagic.getValue(), 0);
        assertEquals(ControlMode.MOTION_MAGIC, motionMagic.getControlMode());
    }

    @Test
    public void configPicksSlotsByNumber() {
        SlotConfig empty = new SlotConfig(1, 0, 0, 0, 0, 0);
        SlotConfig loaded = new SlotConfig(2, 0, 0, 0, 0, 0);
        SlotConfig tracking = new SlotConfig(3, 0, 0, 0, 0, 0);
        VoltageMotorConfig config = new VoltageMotorConfig().withSlot0(empty).withSlot1(loaded).withSlot2(tracking);
        assertSame(empty, config.getSlot(0));
        assertSame(loaded, config.getSlot(1));
        assertSame(tracking, config.getSlot(2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void configRejectsMissingSlots() {
        new VoltageMotorConfig().getSlot(3);
    }

    @Test
    public void controllerUsesWhicheverSlotItsGiven() {
        SlotConfig soft = new SlotConfig().withKP(1);
        SlotConfig stiff = new SlotConfig().withKP(4).withKG(0.5);
        PIDSVAController controller = new PIDSVAController(soft, new ClosedLoopConfig());
        assertEquals(10, controller.calculatePosition(10, 0, 0, 0, 0.02), 1e-12);

        controller.setSlot(stiff);
        assertEquals(40.5, controller.calculatePosition(10, 0, 0, 0, 0.02), 1e-12);

        // Gains are read every calculation, so live edits apply right away.
        stiff.kP = 2;
        assertEquals(20.5, controller.calculatePosition(10, 0, 0, 0, 0.02), 1e-12);
    }
}
