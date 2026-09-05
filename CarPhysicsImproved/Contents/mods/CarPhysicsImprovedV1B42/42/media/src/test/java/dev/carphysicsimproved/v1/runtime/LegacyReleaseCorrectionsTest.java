package dev.carphysicsimproved.v1.runtime;

import dev.carphysicsimproved.v1.physics.LegacyPhysics;
import dev.carphysicsimproved.v1.physics.LegacySlideDynamics;
import pzmod.carphysicsimproved.v1.CarPhysicsImprovedV1Mod;

/** Production math and reflection adapter; no simulated Bullet world. */
public final class LegacyReleaseCorrectionsTest {
    private LegacyReleaseCorrectionsTest() { }

    public static void main(String[] args) throws Exception {
        ratiosAndBurnout();
        slideCadence();
        safetyAndCruise();
        var original = new LegacyCabinExposureHooks.RainInput(.8f, .2f);
        check(LegacyCabinExposureHooks.mergeRain(original, .3) == original, "Do not double native rain");
        var merged = LegacyCabinExposureHooks.mergeRain(new LegacyCabinExposureHooks.RainInput(0, .1f), .5);
        near(merged.increase(), .5, "Supplement missing windshield rain");
        near(merged.decrease(), 0, "Rain prevents simultaneous drying");
        CarPhysicsImprovedV1Mod.setEnabled(false);
        near(LegacyCabinExposureHooks.adjustWindChill(new Object(), .4f), .4, "Disabled cabin untouched");
        check(LegacyCabinExposureHooks.adjustRainInputs(new Object(), .2f, .1f).equals(
                new LegacyCabinExposureHooks.RainInput(.2f, .1f)), "Disabled rain untouched before reflection");
        CarPhysicsImprovedV1Mod.setEnabled(true);
        System.out.println("LegacyReleaseCorrectionsTest: 1-8 gears, FPS-independent burnout/slide commands, "
                + "chunk safety, towing, cruise and rain merge/disable passed");
    }

    private static void ratiosAndBurnout() {
        for (int gears = 1; gears <= 8; gears++) {
            double[] ratios = LegacyPhysics.legacyRatios(gears);
            check(ratios.length == gears, "Correct supported gear count " + gears);
            for (int i = 0; i < ratios.length; i++) {
                check(Double.isFinite(ratios[i]) && ratios[i] > 0, "Positive finite gear");
                if (i > 0) check(ratios[i] < ratios[i - 1], "Ordered ratios");
            }
        }
        var spec = new LegacyPhysics.Spec("Test.Sport", 1200, 4000, 120, 800, 4500,
                4350, 3.2, LegacyPhysics.legacyRatios(5), 30, .9, 1, 3);
        double baseline = -1, force = -1;
        for (int fps : new int[]{20, 30, 60, 120, 240}) {
            var state = new LegacyPhysics.State(); state.engineRpm = 4000; state.throttle = 1;
            var out = LegacyPhysics.step(spec, new LegacyPhysics.Conditions(1, 1, 1, false),
                    LegacyPhysics.Settings.defaults(),
                    new LegacyPhysics.Input(0, true, true, false, false, false, 0, 1, true, 1), state, 1.0 / fps);
            check(out.burnoutSpeedKph() > 0, "Test must exercise active burnout");
            if (baseline < 0) { baseline = out.burnoutSpeedKph(); force = out.engineForce(); }
            near(out.burnoutSpeedKph(), baseline, "Same slip signal at " + fps);
            near(out.engineForce(), force, "Same delivered traction at " + fps);
        }
    }

    private static void slideCadence() {
        var out = LegacySlideDynamics.step(LegacySlideDynamics.Spec.defaults(),
                LegacySlideDynamics.AxleGrip.defaults(), LegacySlideDynamics.Tuning.defaults(),
                new LegacySlideDynamics.Input(15, 2, -.4, .2, 1, 0, 1, 1, 2, 0, 2400, 0, .4, false),
                new LegacySlideDynamics.State(), .05);
        check(out.bulletYawTorque() != 0, "Active non-key yaw fixture");
        for (int fps : new int[]{20, 30, 60, 120, 240}) {
            var command = new LegacySlideCommand();
            int frame = 0;
            double sum = 0;
            for (int step = 0; step < 300; step++) {
                long now = step * 10_000_000L;
                while (Math.round(frame * 1e9 / fps) <= now) {
                    command.publish(out, Math.round(frame * 1e9 / fps)); frame++;
                }
                check(command.forStep(now) != null, "Live command never expires");
                sum += command.forStep(now).yawTorque();
            }
            near(sum, out.bulletYawTorque() * .6 * 300, "Identical fixed-step impulse budget at " + fps);
            command.publish(out, 10);
            check(command.forStep(9) == null && command.forStep(300_000_010) == null, "Clock/expiry guard");
            command.clear();
            check(command.forStep(10) == null, "Release clears command");
        }
    }

    private static void safetyAndCruise() throws Exception {
        var a = LegacyMultiplayerRuntimeTest.fixtureAccess();
        var car = new Car();
        var controller = new Controller();
        LegacyMultiplayerRuntimeTest.replace(a, "controllerControls", Controller.class.getField("controls"));
        for (String pair : new String[]{"controlsForward:forward", "controlsBackward:backward",
                "controlsBrake:brake", "controlsShift:shift", "controlsSteering:steering"}) {
            String[] names = pair.split(":");
            LegacyMultiplayerRuntimeTest.replace(a, names[0], Inputs.class.getField(names[1]));
        }
        LegacyMultiplayerRuntimeTest.replace(a, "vehicleKeyboardControlled", Car.class.getMethod("isKeyboardControlled"));
        LegacyMultiplayerRuntimeTest.replace(a, "vehicleThrottle", Car.class.getMethod("getThrottle"));
        LegacyMultiplayerRuntimeTest.replace(a, "vehicleRegulator", Car.class.getMethod("isRegulator"));
        LegacyMultiplayerRuntimeTest.replace(a, "controllerGas", Controller.class.getMethod("isGas"));
        CarPhysicsImprovedV1Mod.setManualTransmission(false);
        check(!a.controls(controller, car).forward(), "No unsolicited throttle");
        car.regulator = true; controller.gas = true;
        var input = a.controls(controller, car);
        check(input.forward(), "Native cruise demand survives adapter");
        near(input.throttle(), .5, "Cruise uses native throttle, not keyboard full gas");
        controller.gas = false;
        check(!a.controls(controller, car).forward(), "Cruise cuts demand above target");
        controller.controls.forward = true;
        near(a.controls(controller, car).throttle(), 1, "Normal keyboard acceleration unchanged");
        controller.controls.shift = true;
        check(!a.controls(controller, car).forward(), "Regulator adjustment is not gas");
        controller.controls.shift = false; controller.controls.forward = false; controller.gas = true;
        CarPhysicsImprovedV1Mod.setManualTransmission(true); car.gear = 0;
        check(!a.controls(controller, car).forward(), "No cruise throttle in manual neutral");
        car.gear = -1;
        check(!a.controls(controller, car).forward(), "No forward cruise in manual reverse");
        car.gear = 1; // Vanilla may have rewritten its field before CPI's manual step.
        check(!a.controls(controller, car, 0).forward(), "Use CPI selected neutral, not native fallback gear");
        CarPhysicsImprovedV1Mod.setManualTransmission(false);
        car.invalidAhead = true;
        check(a.boundaryStop(car, 20, 0), "Coasting toward unloaded chunk must stop");
        check(a.boundaryStop(car, 0, 100), "No launch into unloaded chunk");
        check(!a.boundaryStop(car, -2, -100), "Can reverse away from forward boundary");
        car.invalidAhead = false; car.invalidBehind = true;
        check(a.boundaryStop(car, -20, 0), "Reverse coasting boundary");
        check(!a.boundaryStop(car, 2, 100), "Can leave rear boundary");
        var nativeController = new LegacyMultiplayerRuntimeTest.Controller(car);
        var output = new LegacyPhysics.Output(-1, -2000, 0, .2, 0, 1, 0, 2400, 1, 2000, 0);
        var motion = new PzLegacyAccess.Motion(-20, 0, 0, 1, 0, -20, 0, 0);
        a.apply(nativeController, car, output, motion, .2, 1.0 / 60);
        near(LegacyMultiplayerRuntimeTest.Native.engine, 0, "Final safety gate overrides CPI engine force");
        near(LegacyMultiplayerRuntimeTest.Native.brake, 30, "Final safety gate retains braking");
        near(LegacyMultiplayerRuntimeTest.Native.steer, .2, "Safety does not lock steering");
        car.towedBy = new Object();
        check(a.controlledDriver(car) == null, "Towed vehicle cannot acquire control session");
    }

    public static final class Car extends LegacyMultiplayerRuntimeTest.Car {
        public boolean regulator;
        public boolean isKeyboardControlled() { return true; }
        public float getThrottle() { return .5f; }
        public boolean isRegulator() { return regulator; }
    }
    public static final class Controller {
        public Inputs controls = new Inputs();
        public boolean gas;
        public boolean isGas() { return gas; }
    }
    public static final class Inputs {
        public boolean forward, backward, brake, shift;
        public float steering;
    }
    private static void near(double value, double expected, String message) {
        check(Double.isFinite(value) && Math.abs(value - expected) < 1e-5, message + ": " + value);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
