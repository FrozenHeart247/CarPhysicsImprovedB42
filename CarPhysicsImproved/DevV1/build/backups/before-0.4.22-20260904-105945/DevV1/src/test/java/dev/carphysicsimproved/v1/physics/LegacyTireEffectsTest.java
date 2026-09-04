package dev.carphysicsimproved.v1.physics;

public final class LegacyTireEffectsTest {
    private LegacyTireEffectsTest() {
    }

    public static void main(String[] args) {
        ordinaryCorneringDoesNotTriggerEffects();
        oneFrameSpikeIsRejected();
        sustainedPhysicalSlideTriggersAndReleases();
        burnoutTriggersQuickly();
        lockedBrakingTriggers();
        System.out.println("LegacyTireEffectsTest passed");
    }

    private static void ordinaryCorneringDoesNotTriggerEffects() {
        LegacyTireEffects.State state = new LegacyTireEffects.State();
        LegacyTireEffects.Output output = null;
        for (int index = 0; index < 60; index++) {
            output = LegacyTireEffects.step(input(0.0, 14.0, 0.18, 0.012,
                    0.0, false, false, 0.0, 0.0, 0.20), state, 1.0 / 60.0);
        }
        require(output != null && output.intensity() == 0.0,
                "ordinary steering/native tire load must stay silent");
    }

    private static void oneFrameSpikeIsRejected() {
        LegacyTireEffects.State state = new LegacyTireEffects.State();
        LegacyTireEffects.Output output = LegacyTireEffects.step(input(0.0, 15.0, 2.0, 0.15,
                0.8, true, true, 0.0, 0.0, 0.5), state, 1.0 / 60.0);
        require(!output.active(), "one frame of slide telemetry must not start cosmetics");
    }

    private static void sustainedPhysicalSlideTriggersAndReleases() {
        LegacyTireEffects.State state = new LegacyTireEffects.State();
        LegacyTireEffects.Output output = null;
        for (int index = 0; index < 18; index++) {
            output = LegacyTireEffects.step(input(0.0, 13.0, 1.8, 0.12,
                    0.75, true, true, 0.0, 0.0, 0.35), state, 1.0 / 60.0);
        }
        require(output != null && output.active() && output.intensity() > 0.20,
                "established drift must produce a visible/audible signal");
        for (int index = 0; index < 45; index++) {
            output = LegacyTireEffects.step(LegacyTireEffects.Input.idle(), state, 1.0 / 60.0);
        }
        require(output != null && !output.active() && output.intensity() == 0.0,
                "cosmetic signal must release after grip returns");
    }

    private static void burnoutTriggersQuickly() {
        LegacyTireEffects.State state = new LegacyTireEffects.State();
        LegacyTireEffects.Output output = null;
        for (int index = 0; index < 5; index++) {
            output = LegacyTireEffects.step(input(12.0, 0.4, 0.0, 0.0,
                    0.0, false, false, 0.0, 0.0, 0.0), state, 1.0 / 60.0);
        }
        require(output != null && output.active() && output.intensity() > 0.20,
                "real drivetrain wheelspin must start cosmetics promptly");
    }

    private static void lockedBrakingTriggers() {
        LegacyTireEffects.State state = new LegacyTireEffects.State();
        LegacyTireEffects.Output output = null;
        for (int index = 0; index < 10; index++) {
            output = LegacyTireEffects.step(input(0.0, 12.0, 0.0, 0.0,
                    0.0, false, false, 0.85, 0.0, 0.45), state, 1.0 / 60.0);
        }
        require(output != null && output.active() && output.braking() > 0.0,
                "hard braking plus native wheel lock must trigger cosmetics");
    }

    private static LegacyTireEffects.Input input(double burnout, double speed, double lateral,
            double beta, double blend, boolean intentional, boolean sliding,
            double serviceBrake, double handbrake, double rearSkid) {
        return new LegacyTireEffects.Input(burnout, speed, lateral, beta, blend,
                intentional, sliding, serviceBrake, handbrake, rearSkid);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
