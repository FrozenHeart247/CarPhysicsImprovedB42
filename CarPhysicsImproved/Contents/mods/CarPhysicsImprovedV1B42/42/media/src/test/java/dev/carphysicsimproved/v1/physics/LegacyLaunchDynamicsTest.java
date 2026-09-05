package dev.carphysicsimproved.v1.physics;

import pzmod.carphysicsimproved.v1.CarPhysicsImprovedV1Mod;

/** Force/state regression tests, not a native Bullet acceleration-time simulation. */
public final class LegacyLaunchDynamicsTest {
    private LegacyLaunchDynamicsTest() { }

    public static void main(String[] args) {
        environmentGate();
        var bus = spec(2, 55);
        var automatic = output(bus, false, 1, 9.5, 1, 1.12, false, 1.0 / 60);
        var manual = output(bus, true, 1, 9.5, 1, 1.12, false, 1.0 / 60);
        check(automatic.equals(manual), "Same-state auto/manual use identical drivetrain output");
        check(automatic.rawDriveForce() > automatic.engineForce() * 2,
                "B700-like launch is strongly traction-limited");
        double launch = LegacyLaunchDynamics.forceScale(bus, 1, 9.5, .7);
        near(launch, .7, "Full low-speed reduction");
        var softened = LegacyLaunchDynamics.withDelivery(automatic, launch);
        near(softened.engineForce(), automatic.engineForce() * .7,
                "Reduction must work even when raw torque exceeds the traction cap");
        check(softened.equals(LegacyLaunchDynamics.withDelivery(manual, launch)),
                "Same reduction in auto/manual");

        int checked = 0;
        for (int type = 1; type <= 3; type++) {
            for (double top : new double[]{10, 55, 65, 85, 120}) {
                var spec = spec(type, top);
                double previousScale = .7;
                for (int speed = 0; speed <= 150; speed++) {
                    double scale = LegacyLaunchDynamics.forceScale(spec, 1, speed, .7);
                    check(scale >= previousScale && scale <= 1, "Smooth fade never strengthens the reduction");
                    if (speed > 0) check(scale - previousScale < .023, "No abrupt per-speed-unit force step");
                    previousScale = scale;
                }
                for (boolean manualMode : new boolean[]{false, true}) {
                    for (int gear = -1; gear <= 8; gear++) {
                        for (double speed : new double[]{-45, -10, 0, 10, 25, 45, 80}) {
                            for (double throttle : new double[]{0, .5, 1}) {
                                for (double grip : new double[]{.1, .7, 1.12}) {
                                    for (double dt : new double[]{1.0 / 30, 1.0 / 60, 1.0 / 120}) {
                                        var source = output(spec, manualMode, gear, speed, throttle, grip,
                                                grip < .7, dt);
                                        double scale = LegacyLaunchDynamics.forceScale(spec, source.gear(), speed, .7);
                                        var tuned = LegacyLaunchDynamics.withDelivery(source, scale);
                                        var otherFields = new LegacyPhysics.Output(source.gear(), tuned.engineForce(),
                                                source.brakingForce(), source.steeringRadians(), source.dragMagnitude(),
                                                source.tireTraction(), source.burnoutSpeedKph(), source.engineRpm(),
                                                source.throttle(), source.rawDriveForce(), source.clutchKickIntensity());
                                        check(tuned.equals(otherFields), "Only delivered drive force may change");
                                        check(Math.abs(tuned.engineForce()) <= Math.abs(source.engineForce()),
                                                "Must not exceed the existing drive/grip limit");
                                        if (type != 2 || source.gear() <= 0 || source.engineForce() <= 0 || Math.abs(speed) >= 70) {
                                            check(tuned == source, "Unaffected class/reverse/coast/high-speed is exact passthrough");
                                        }
                                        check(LegacyLaunchDynamics.withDelivery(source,
                                                LegacyLaunchDynamics.forceScale(spec, source.gear(), speed, 1)) == source,
                                                "100 percent restores the exact original output");
                                        checked++;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        near(LegacyLaunchDynamics.forceScale(bus, 1, 50, .7), 1,
                "Fast sideways movement is judged by total native speed, not forward projection");
        near(LegacyLaunchDynamics.forceScale(bus, 1, Double.NaN, .7), 1, "Invalid speed fails open");
        near(LegacyLaunchDynamics.sanitize(Double.NaN), .5, "Invalid option uses the new 50 percent default");
        near(LegacyLaunchDynamics.sanitize(-1), .25, "No zero/reversed drive from invalid option");
        near(LegacyLaunchDynamics.sanitize(2), 1, "No boost beyond the existing grip cap");
        CarPhysicsImprovedV1Mod.configureHeavyLaunch(.85);
        CarPhysicsImprovedV1Mod.configurePhysics(1, 1, 1, 2.5, 40, .7, 1, 1.5,
                .05, .1, .2, 1, 1, 1, .6, .7, .4);
        CarPhysicsImprovedV1Mod.configureSteering(1, .1, 1, .1, 3, 75);
        near(CarPhysicsImprovedV1Mod.heavyLaunchMultiplier(), .85,
                "Reapplying physics/steering must not discard independent launch setting");
        CarPhysicsImprovedV1Mod.configureHeavyLaunch(1);
        near(CarPhysicsImprovedV1Mod.heavyLaunchMultiplier(), 1, "Lua bridge can disable the feature");
        CarPhysicsImprovedV1Mod.configureHeavyLaunch(.5);
        System.out.println("LegacyLaunchDynamicsTest: " + checked
                + " same-state comparisons; dry-road/weather gate, transitions, saturated launch, fade, auto/manual and config passed");
    }

    private static void environmentGate() {
        near(LegacyLaunchDynamics.DEFAULT_HEAVY_MULTIPLIER, .5, "Java default is 50 percent");
        near(CarPhysicsImprovedV1Mod.heavyLaunchMultiplier(), .5, "Runtime starts at 50 before Lua configuration");
        var road = terrain(false, false, 0, 0);
        // Deliberately all have surfaceGrip=1: Sandbox recovery cannot hide actual terrain/weather.
        var excluded = new LegacyTerrainDynamics.Output[]{
                terrain(true, false, 0, 0), terrain(false, true, 0, 0),
                terrain(false, false, .0001, 0), terrain(false, false, 1, 0),
                terrain(false, false, 0, .001), terrain(false, false, 0, 1),
                terrain(true, true, .5, 1), null,
                terrain(false, false, Double.NaN, 0), terrain(false, false, 0, Double.NaN)};
        for (int type = 1; type <= 3; type++) {
            var car = spec(type, 85);
            for (int gear = -1; gear <= 8; gear++) {
                for (double speed : new double[]{-40, 0, 10, 35, 60, 80}) {
                    for (double multiplier : new double[]{.25, .5, .7, 1}) {
                        double acceptedDry = LegacyLaunchDynamics.forceScale(car, gear, speed, multiplier);
                        double gatedDry = LegacyLaunchDynamics.forceScale(car, gear, speed, multiplier,
                                road, new LegacyLaunchDynamics.State(), 1.0 / 60);
                        check(gatedDry == acceptedDry, "Dry-road curve is bit-exact at the same saved setting");
                        for (var environment : excluded) {
                            near(LegacyLaunchDynamics.forceScale(car, gear, speed, multiplier,
                                    environment, new LegacyLaunchDynamics.State(), 1.0 / 60), 1,
                                    "Excluded/unknown conditions start without the extra launch penalty");
                        }
                    }
                }
            }
        }
        var bus = spec(2, 55);
        for (double dt : new double[]{1.0 / 20, 1.0 / 30, 1.0 / 60, 1.0 / 120, 1.0 / 240}) {
            for (var bad : excluded) {
                var gate = new LegacyLaunchDynamics.State();
                near(LegacyLaunchDynamics.forceScale(bus, 1, 10, .5, road, gate, dt), .5,
                        "Start on dry road uses the saved coefficient immediately");
                double previous = .5;
                for (int tick = 0; tick < Math.ceil(.5 / dt); tick++) {
                    double scale = LegacyLaunchDynamics.forceScale(bus, 1, 10, .5, bad, gate, dt);
                    check(scale >= previous && scale <= 1, "Exit transition must be monotone and bounded");
                    check(scale - previous <= .5 * dt / .35 + 1e-9, "No abrupt force jump on surface exit");
                    previous = scale;
                }
                near(previous, 1, "No residual penalty after leaving dry road");
                for (int tick = 0; tick < Math.ceil(.5 / dt); tick++) {
                    double scale = LegacyLaunchDynamics.forceScale(bus, 1, 10, .5, road, gate, dt);
                    check(scale <= previous && scale >= .5, "Return transition is monotone and bounded");
                    check(previous - scale <= .5 * dt / .35 + 1e-9, "No abrupt force drop on returning to road");
                    previous = scale;
                }
                near(previous, .5, "Return to accepted dry-road tuning");
            }
        }
        // Mid-transition cancellation, full disable and per-vehicle isolation.
        var gate = new LegacyLaunchDynamics.State();
        LegacyLaunchDynamics.forceScale(bus, 1, 10, .5, road, gate, .05);
        double leaving = LegacyLaunchDynamics.forceScale(bus, 1, 10, .5, excluded[0], gate, .05);
        check(leaving > .5 && leaving < 1, "Transition actually blends");
        near(LegacyLaunchDynamics.forceScale(bus, 1, 10, .5, road, gate, .05), .5,
                "Rapid surface return cancels the pending transition");
        near(LegacyLaunchDynamics.forceScale(bus, 1, 10, 1, excluded[0], gate, .05), 1,
                "100 percent disables even during transition");
        near(LegacyLaunchDynamics.forceScale(bus, 1, 10, .5, excluded[0],
                new LegacyLaunchDynamics.State(), .05), 1, "Another vehicle has no inherited dry-road weight");
        var stoppedGate = new LegacyLaunchDynamics.State();
        LegacyLaunchDynamics.forceScale(bus, 1, 10, .5, road, stoppedGate, 0);
        for (double badDt : new double[]{0, -1, Double.NaN}) {
            near(LegacyLaunchDynamics.forceScale(bus, 1, 10, .5, excluded[0], stoppedGate, badDt), .5,
                    "Invalid elapsed time cannot advance/reverse the blend");
        }
    }

    private static LegacyTerrainDynamics.Output terrain(boolean offroad, boolean forest, double rain, double snow) {
        return new LegacyTerrainDynamics.Output(LegacyTerrainDynamics.Profile.TERRAIN,
                1, 1, 1, rain, snow, offroad, forest);
    }

    private static LegacyPhysics.Spec spec(int type, double top) {
        return new LegacyPhysics.Spec("Base.LaunchTest" + type, 1315, 505, top,
                750, 4500, 4350, 3, LegacyPhysics.legacyRatios(8), 50, .8, 1, type);
    }

    private static LegacyPhysics.Output output(LegacyPhysics.Spec spec, boolean manual, int gear,
            double speed, double throttle, double grip, boolean offroad, double dt) {
        var state = new LegacyPhysics.State();
        state.gear = gear;
        state.lastStepGear = gear;
        state.engineRpm = 2849;
        state.throttle = throttle;
        state.fullThrottleSeconds = 1;
        return LegacyPhysics.step(spec, new LegacyPhysics.Conditions(1, grip, 1, offroad),
                LegacyPhysics.Settings.defaults(), new LegacyPhysics.Input(speed / 3.6, true,
                        throttle > 0 && (manual || gear >= 0), throttle > 0 && !manual && gear < 0,
                        false, false, .4, throttle, manual, gear), state, dt);
    }

    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-9, message + ": " + actual + " != " + expected);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
