package dev.carphysicsimproved.v1.physics;

/** Pure-model checks: no engine shutdown, native vehicle or Bullet simulation. */
public final class LegacyZeroPowerTest {
    private static final LegacyPhysics.Settings SETTINGS = LegacyPhysics.Settings.defaults();
    private static final LegacyPhysics.Conditions ROAD = new LegacyPhysics.Conditions(1, 1, 1, false);

    private LegacyZeroPowerTest() {
    }

    public static void main(String[] args) {
        int failures = 0;
        failures += run("zero and invalid power", LegacyZeroPowerTest::normalization);
        failures += run("automatic/manual/forward/reverse", LegacyZeroPowerTest::zeroDrive);
        failures += run("neutral drop and running RPM", LegacyZeroPowerTest::neutralDrop);
        failures += run("moving coast/brakes/steering", LegacyZeroPowerTest::passiveControls);
        failures += run("loss during clutch kick and recovery", LegacyZeroPowerTest::recovery);
        failures += run("ordinary power loss and recovery", LegacyZeroPowerTest::ordinaryRecovery);
        if (failures != 0) throw new AssertionError("Zero-power failures: " + failures);
        System.out.println("LegacyZeroPowerTest: all zero-drive, controls, RPM and recovery checks passed");
    }

    private static int run(String name, Runnable test) {
        try {
            test.run();
            return 0;
        } catch (AssertionError error) {
            System.err.println(name + ": " + error.getMessage());
            return 1;
        }
    }

    private static LegacyPhysics.Spec spec(double power) {
        return new LegacyPhysics.Spec("Base.ZeroPower", 1200, power, 120, 700, 4500, 4350,
                3.2, LegacyPhysics.legacyRatios(5), 20, .55, 1, 3);
    }

    private static LegacyPhysics.State warm(int gear) {
        var state = new LegacyPhysics.State();
        state.gear = gear;
        state.lastStepGear = gear;
        state.engineRpm = 4200;
        state.throttle = 1;
        return state;
    }

    private static LegacyPhysics.Input input(boolean manual, int gear, double speed,
            double throttle, boolean brake, boolean handbrake, double steering) {
        return new LegacyPhysics.Input(speed, true, throttle > 0 && (manual || gear >= 0),
                throttle > 0 && !manual && gear < 0, brake, handbrake, steering,
                throttle, manual, gear, true);
    }

    private static void normalization() {
        for (double power : new double[]{0, -0.0, -10, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            check(spec(power).enginePower() == 0, "nonpositive/invalid power must not become positive: " + power);
        }
        check(spec(320).enginePower() == 320, "healthy power must remain unchanged");
        check(spec(.1).enginePower() == 1, "existing positive-power floor must remain unchanged");
        check(spec(6000).enginePower() == 5000, "existing positive-power cap must remain unchanged");
    }

    private static void zeroDrive() {
        for (boolean manual : new boolean[]{false, true}) {
            for (int gear : new int[]{-1, 0, 1, 5}) {
                for (double dt : new double[]{1.0 / 30, 1.0 / 60, 1.0 / 120}) {
                    var state = warm(gear);
                    state.clutchKick = 1;
                    state.burnout = 40;
                    for (int tick = 0; tick < 120; tick++) {
                        var out = LegacyPhysics.step(spec(0), ROAD, SETTINGS,
                                input(manual, gear, 0, 1, false, false, 0), state, dt);
                        noDrive(out, state);
                        check(out.engineRpm() > 0, "zero drive power must not simulate an engine shutdown");
                    }
                }
            }
        }
    }

    private static void neutralDrop() {
        for (int gear : new int[]{-1, 1, 5}) {
            var state = warm(0);
            state.engineRpm = 700;
            LegacyPhysics.Output neutral = null;
            for (int tick = 0; tick < 90; tick++) {
                neutral = LegacyPhysics.step(spec(0), ROAD, SETTINGS,
                        input(true, 0, 0, 1, false, false, 0), state, 1.0 / 60);
                noDrive(neutral, state);
            }
            check(neutral != null && neutral.engineRpm() > 3500, "running engine must still rev in neutral");
            var engaged = LegacyPhysics.step(spec(0), ROAD, SETTINGS,
                    input(true, gear, 0, 1, false, false, 0), state, 1.0 / 60);
            noDrive(engaged, state);
            check(engaged.gear() == gear, "manual selector must still work without drive power");
        }
    }

    private static void passiveControls() {
        for (boolean manual : new boolean[]{false, true}) {
            for (double speed : new double[]{-8, 0, 15, 30}) {
                for (int control = 0; control < 3; control++) {
                    int gear = speed < 0 ? -1 : 2;
                    var input = input(manual, gear, speed, 0, control == 1, control == 2, .8);
                    var zeroState = warm(gear);
                    var healthyState = warm(gear);
                    zeroState.throttle = 0;
                    healthyState.throttle = 0;
                    for (int tick = 0; tick < 30; tick++) {
                        var zero = LegacyPhysics.step(spec(0), ROAD, SETTINGS, input, zeroState, 1.0 / 60);
                        var healthy = LegacyPhysics.step(spec(320), ROAD, SETTINGS, input, healthyState, 1.0 / 60);
                        noDrive(zero, zeroState);
                        check(zero.brakingForce() == healthy.brakingForce(), "brakes changed with zero power");
                        check(zero.steeringRadians() == healthy.steeringRadians(), "steering changed with zero power");
                        check(zero.dragMagnitude() == healthy.dragMagnitude(), "coasting resistance changed");
                        check(zero.tireTraction() == healthy.tireTraction(), "tire grip changed");
                        check(zero.gear() == healthy.gear(), "passive gear selection changed");
                        if (control != 0) check(zero.brakingForce() > 0, "brakes must remain available");
                    }
                }
            }
        }
    }

    private static void recovery() {
        var state = warm(0);
        var demand = input(true, 1, 0, 1, false, false, 0);
        var kick = LegacyPhysics.step(spec(320), ROAD, SETTINGS, demand, state, 1.0 / 60);
        check(kick.clutchKickIntensity() > 0 && kick.engineForce() > 0, "healthy clutch kick must still work");
        var lost = LegacyPhysics.step(spec(0), ROAD, SETTINGS, demand, state, 1.0 / 60);
        noDrive(lost, state);
        var restored = LegacyPhysics.step(spec(320), ROAD, SETTINGS, demand, state, 1.0 / 60);
        check(restored.engineForce() > 0, "drive must recover without recreating the vehicle state");
        check(restored.clutchKickIntensity() == 0, "repair must not replay an interrupted clutch kick");
        LegacyPhysics.step(spec(320), ROAD, SETTINGS,
                input(true, 0, 0, 1, false, false, 0), state, .05);
        state.engineRpm = 4200;
        var freshKick = LegacyPhysics.step(spec(320), ROAD, SETTINGS, demand, state, 1.0 / 60);
        check(freshKick.clutchKickIntensity() > 0, "a new deliberate kick after repair must still work");
    }

    private static void ordinaryRecovery() {
        for (boolean manual : new boolean[]{false, true}) {
            var state = warm(1);
            var demand = input(manual, 1, 2, 1, false, false, .5);
            check(LegacyPhysics.step(spec(320), ROAD, SETTINGS, demand, state, .05).engineForce() > 0,
                    "healthy moving vehicle must have drive force");
            for (int tick = 0; tick < 60; tick++) {
                noDrive(LegacyPhysics.step(spec(0), ROAD, SETTINGS, demand, state, .05), state);
            }
            boolean resumed = false;
            for (int tick = 0; tick < 90; tick++) {
                var out = LegacyPhysics.step(spec(320), ROAD, SETTINGS, demand, state, .05);
                resumed |= out.engineForce() > 0;
                check(out.clutchKickIntensity() == 0, "restored power must not synthesize a neutral drop");
            }
            check(resumed, "ordinary drive must resume after restoring power");
        }
    }

    private static void noDrive(LegacyPhysics.Output out, LegacyPhysics.State state) {
        check(out.engineForce() == 0 && out.rawDriveForce() == 0, "zero power produced drive force");
        check(out.burnoutSpeedKph() == 0 && state.burnout == 0, "zero power produced burnout");
        check(out.clutchKickIntensity() == 0 && state.clutchKick == 0, "zero power retained a clutch kick");
        check(Double.isFinite(out.engineRpm()) && Double.isFinite(out.brakingForce())
                && Double.isFinite(out.steeringRadians()) && Double.isFinite(out.dragMagnitude()),
                "non-finite physics output");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
