package dev.carphysicsimproved.v1.runtime;

import dev.carphysicsimproved.v1.physics.LegacyPhysics;
import dev.carphysicsimproved.v1.physics.LegacySlideDynamics;

public final class LegacyDriftControlTest {
    private LegacyDriftControlTest() { }

    public static void main(String[] args) throws ReflectiveOperationException {
        chordChecks();
        gearChordPriorityChecks();
        slideChecks();
        steeringChecks();
        System.out.println("LegacyDriftControlTest: chord, immediate entry/release and steering isolation passed");
    }

    private static void chordChecks() {
        LegacyDriftKey chord = new LegacyDriftKey(57, true, false, false);
        check(chord.matches(true, true, false, false), "Shift+Space must match");
        check(!chord.matches(true, false, false, false), "Space alone must remain handbrake");
        check(!chord.matches(false, true, false, false), "Shift alone must not drift");
        check(!chord.matches(true, true, true, false), "Extra Ctrl must not match");
        check(!chord.matches(true, true, false, true), "Extra Alt must not match");
        check(chord.usesKey(57) && chord.usesKey(54) && !chord.usesKey(29), "Chord overlap detection");
        check(chord.suppressesBrake(57, 0, true, false, false), "Chord consumes its Space handbrake");
        check(!chord.suppressesBrake(57, 0, true, false, true), "Forced stop must be preserved");
        check(!chord.suppressesBrake(57, 48, true, true, false), "Separate alternate brake remains usable");
        check(!new LegacyDriftKey(45, false, false, false).suppressesBrake(57, 0, true, false, false),
                "Standalone drift key must not suppress an independent handbrake");
        check(new LegacyDriftKey(42, false, false, false).matches(true, true, false, false),
                "Standalone modifier binding must work too");
        check(!new LegacyDriftKey(0, true, false, false).matches(true, true, false, false), "Unbound is off");
        check(new LegacyDriftKey(10_000, false, false, false).key() == 0, "Reject non-keyboard codes");
    }

    private static void gearChordPriorityChecks() throws ReflectiveOperationException {
        LegacyDrivingKeys defaults = LegacyDrivingKeys.defaults();
        check(defaults.drift().key() == 42 && !defaults.drift().shift(), "Default is standalone Left Shift");
        java.util.Set<Integer> down = new java.util.HashSet<>();
        LegacyDrivingKeys.KeyState keys = down::contains;
        check(!defaults.driftHeld(false, keys), "No key means no drift");
        down.add(57);
        check(!defaults.driftHeld(false, keys), "Space alone is not default drift");
        down.clear();
        down.add(42);
        check(defaults.driftHeld(true, keys), "Standalone Shift works in manual mode");
        LegacyDrivingKeys custom = new LegacyDrivingKeys(defaults.drift(),
                new LegacyDriftKey(18, true, false, false), new LegacyDriftKey(16, true, false, false));
        down.add(18);
        check(!custom.driftHeld(true, keys), "Shift+E must select gear without activating drift");
        check(custom.driftHeld(false, keys), "Automatic mode does not reserve manual gear bindings");
        down.remove(18);
        down.add(16);
        check(!custom.driftHeld(true, keys), "Shift+Q must select gear without activating drift");
        down.remove(16);
        check(custom.driftHeld(true, keys), "Still-held Shift resumes drift after releasing gear key");
        down.add(29);
        check(!custom.driftHeld(true, keys), "Extra Ctrl prevents default Shift activation");
        down.clear();
        check(!custom.driftHeld(true, keys), "Release never latches");
    }

    private static void slideChecks() {
        LegacySlideDynamics.State state = new LegacySlideDynamics.State();
        LegacySlideDynamics.Output active = slide(state, true, 18.0, 4, 1.0);
        check(active.intentionalSlide() && active.cause() == LegacySlideDynamics.Cause.DRIFT_KEY,
                "Dedicated drift enters immediately in fourth gear, without gas or handbrake");
        check(Math.abs(active.wheelFrictionScale() - 0.35) < 1e-10, "Dedicated grip uses power setting");
        check(active.bulletYawTorque() > 0.0 && active.lateralForce() == 0.0, "Rotate, do not push sideways");
        double torque = active.bulletYawTorque();
        LegacySlideDynamics.Output opposite = slide(state, true, 18.0, 4, -1.0);
        check(opposite.bulletYawTorque() == -torque, "Countersteer must reverse torque in this frame");
        LegacySlideDynamics.Output release = slide(state, false, 18.0, 4, -1.0);
        check(!release.intentionalSlide() && release.wheelFrictionScale() == 1.0
                && release.bulletYawTorque() == 0.0 && release.lateralForce() == 0.0,
                "Key release restores grip with no delayed force tail");
        check(!slide(new LegacySlideDynamics.State(), true, 3.0, 1, 1.0).intentionalSlide(), "Low speed gate");
        check(!slide(new LegacySlideDynamics.State(), true, 18.0, 0, 1.0).intentionalSlide(), "Neutral gate");
        check(!slide(new LegacySlideDynamics.State(), true, -18.0, -1, 1.0).intentionalSlide(), "Reverse gate");
        check(!slide(new LegacySlideDynamics.State(), true, 18.0, 4, 0.0).intentionalSlide(), "Straight gate");
        check(!slide(new LegacySlideDynamics.State(), true, 32.0, 4, 1.0).intentionalSlide(), "Upper speed gate");
        LegacySlideDynamics.Tuning defaults = LegacySlideDynamics.Tuning.defaults();
        LegacySlideDynamics.Tuning off = new LegacySlideDynamics.Tuning(false, 1, 1, .8, true,
                2000, 2000, .35, .35, 1.5);
        check(!LegacySlideDynamics.step(LegacySlideDynamics.Spec.defaults(), LegacySlideDynamics.AxleGrip.defaults(),
                off, slideInput(true, 18, 4, 1), state, .05).intentionalSlide(), "Sandbox disabled");
        check(defaults.driftSteeringMultiplier() == 1.5, "Preserve existing defaults");
    }

    private static LegacySlideDynamics.Output slide(LegacySlideDynamics.State state,
            boolean held, double speed, int gear, double steering) {
        return LegacySlideDynamics.step(LegacySlideDynamics.Spec.defaults(), LegacySlideDynamics.AxleGrip.defaults(),
                LegacySlideDynamics.Tuning.defaults(), slideInput(held, speed, gear, steering), state, .05);
    }

    private static LegacySlideDynamics.Input slideInput(boolean held, double speed, int gear, double steering) {
        return new LegacySlideDynamics.Input(speed, 0, 0, .2 * steering, steering, 0, 0, 0,
                gear, 0, 2400, 0, 0, false, held);
    }

    private static void steeringChecks() {
        LegacyPhysics.Spec car = new LegacyPhysics.Spec("Test.Sport", 1250, 250, 120, 700, 4500, 4350,
                3.2, LegacyPhysics.legacyRatios(5), 100, .9, 1, 3);
        LegacyPhysics.Conditions road = new LegacyPhysics.Conditions(1, 1, 1, false);
        LegacyPhysics.Settings tuning = LegacyPhysics.Settings.defaults();
        LegacyPhysics.Input input = new LegacyPhysics.Input(18, true, true, false, false, false, 1, 1, true, 4);
        LegacyPhysics.State normalState = new LegacyPhysics.State();
        LegacyPhysics.State dedicatedState = new LegacyPhysics.State();
        LegacyPhysics.Output normal = LegacyPhysics.step(car, road, tuning, input, normalState, .05);
        LegacyPhysics.Output drift = LegacyPhysics.step(car, road, tuning, input, dedicatedState, .05, 1.5, .55);
        check(Math.abs(drift.steeringRadians() - normal.steeringRadians() * 1.5) < 1e-10, "Boost response rate");
        check(normal.engineForce() == drift.engineForce() && normal.engineRpm() == drift.engineRpm()
                && normal.dragMagnitude() == drift.dragMagnitude() && drift.brakingForce() == 0.0,
                "Dedicated steering does not alter drivetrain, coast or brake");
        for (int i = 0; i < 180; i++) {
            drift = LegacyPhysics.step(car, road, tuning, input, dedicatedState, .05, 1.5, .55);
        }
        check(Math.abs(drift.steeringRadians() - .55) < 1e-10, "Use actual speed clamp, not enlarged angle");
        LegacyPhysics.State baseline = new LegacyPhysics.State();
        baseline.steering = dedicatedState.steering;
        LegacyPhysics.Output released = LegacyPhysics.step(car, road, tuning, input, dedicatedState, .05);
        LegacyPhysics.Output expected = LegacyPhysics.step(car, road, tuning, input, baseline, .05);
        check(released.steeringRadians() == expected.steeringRadians(), "No stored response boost after release");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
