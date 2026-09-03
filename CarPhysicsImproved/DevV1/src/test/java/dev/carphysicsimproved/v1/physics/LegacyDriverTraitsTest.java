package dev.carphysicsimproved.v1.physics;

public final class LegacyDriverTraitsTest {
    private LegacyDriverTraitsTest() {
    }

    public static void main(String[] args) {
        LegacyDriverTraits.Modifiers normal = LegacyDriverTraits.modifiers(false, false);
        LegacyDriverTraits.Modifiers sunday = LegacyDriverTraits.modifiers(true, false);
        LegacyDriverTraits.Modifiers speed = LegacyDriverTraits.modifiers(false, true);

        check(sunday.throttleResponse() < normal.throttleResponse(),
                "Sunday Driver must have slower throttle response");
        check(sunday.torqueMultiplier() == 0.75 && sunday.speedRangeMultiplier() == 0.75,
                "Sunday Driver profile must retain the intended 25 percent penalties");
        check(speed.throttleResponse() > normal.throttleResponse(),
                "Speed Demon must have faster throttle response");
        check(speed.torqueMultiplier() == 1.15 && speed.speedRangeMultiplier() == 1.20,
                "Speed Demon must use the buffed V1 profile");
        check(LegacyDriverTraits.modifiers(true, true) == sunday,
                "Sunday Driver must win for malformed characters carrying both traits");

        LegacyPhysics.Spec spec = new LegacyPhysics.Spec(
                "Base.TestCar", 2_000.0, 80.0, 120.0, 700.0, 4_500.0, 4_350.0,
                3.2, LegacyPhysics.legacyRatios(5), 20.0, 0.55, 1.0, 1);
        LegacyPhysics.Conditions road = new LegacyPhysics.Conditions(1.0, 1.0, 1.0, false);
        LegacyPhysics.Settings settings = LegacyPhysics.Settings.defaults();
        LegacyPhysics.Output normalOutput = step(spec, road, settings, normal);
        LegacyPhysics.Output sundayOutput = step(spec, road, settings, sunday);
        LegacyPhysics.Output speedOutput = step(spec, road, settings, speed);

        check(sundayOutput.throttle() < normalOutput.throttle(),
                "Sunday Driver must feed throttle more slowly into the drivetrain");
        check(speedOutput.throttle() > normalOutput.throttle(),
                "Speed Demon must feed throttle more quickly into the drivetrain");
        check(sundayOutput.engineForce() < normalOutput.engineForce(),
                "Sunday Driver must reduce real submitted engine force");
        check(speedOutput.engineForce() > normalOutput.engineForce(),
                "Speed Demon must increase real submitted engine force");

        int normalGear = automaticGear(spec, road, settings, normal);
        int sundayGear = automaticGear(spec, road, settings, sunday);
        int speedGear = automaticGear(spec, road, settings, speed);
        check(sundayGear > normalGear,
                "Sunday Driver automatic transmission must shift earlier");
        check(speedGear < normalGear,
                "Speed Demon automatic transmission must hold lower gears longer");

        System.out.println("LegacyDriverTraitsTest: V1 driver-trait profiles passed");
    }

    private static LegacyPhysics.Output step(LegacyPhysics.Spec spec,
            LegacyPhysics.Conditions conditions, LegacyPhysics.Settings settings,
            LegacyDriverTraits.Modifiers modifiers) {
        LegacyPhysics.State state = new LegacyPhysics.State();
        state.engineRpm = 2_000.0;
        return LegacyPhysics.step(spec, conditions, settings,
                new LegacyPhysics.Input(0.0, true, true, false, false, false,
                        0.0, 1.0, true, 1, true, modifiers),
                state, 0.05);
    }

    private static int automaticGear(LegacyPhysics.Spec spec,
            LegacyPhysics.Conditions conditions, LegacyPhysics.Settings settings,
            LegacyDriverTraits.Modifiers modifiers) {
        LegacyPhysics.State state = new LegacyPhysics.State();
        state.gear = 3;
        state.lastStepGear = 3;
        state.engineRpm = 3_000.0;
        state.throttle = 1.0;
        state.fullThrottleSeconds = 1.0;
        return LegacyPhysics.step(spec, conditions, settings,
                new LegacyPhysics.Input(17.5, true, true, false, false, false,
                        0.0, 1.0, false, 3, true, modifiers),
                state, 0.05).gear();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
