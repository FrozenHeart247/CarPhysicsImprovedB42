package dev.carphysicsimproved.v1.physics;

import java.util.Arrays;
import java.util.Random;

public final class LegacyAxleDriftTest {
    private LegacyAxleDriftTest() { }

    public static void main(String[] args) {
        wheelIsolation();
        invalidInput();
        entryRamp();
        modeIsolation();
        System.out.println("LegacyAxleDriftTest: rear-only fields, baseline preservation, ramp and old-mode isolation passed");
    }

    private static void wheelIsolation() {
        Random random = new Random(419);
        for (int sample = 0; sample < 500; sample++) {
            float[] carA = new float[24];
            for (int i = 0; i < carA.length; i++) carA[i] = random.nextFloat() * 3.0f;
            for (int wheel = 0; wheel < 4; wheel++) carA[wheel * 6] = 1.0f;
            float[] baseline = carA.clone();
            float[] carB = carA.clone();
            for (int wheel = 0; wheel < 4; wheel++) {
                boolean applied = LegacyAxleDrift.applyWheel(carA, wheel, wheel >= 2, 0.45);
                check(applied == (wheel >= 2), "Only rear wheels should change");
            }
            for (int i = 0; i < carA.length; i++) {
                float expected = i == 14 || i == 20 ? (float) (baseline[i] * .45) : baseline[i];
                check(carA[i] == expected, "Pressure, suspension, bumps and front friction must remain exact");
            }
            check(Arrays.equals(carB, baseline), "Another car with the same parameters must not change");
            float lastRear = carA[14];
            // The native method rebuilds the wheel from its part on every invocation.
            carA[14] = baseline[14];
            LegacyAxleDrift.applyWheel(carA, 2, true, .45);
            check(carA[14] == lastRear, "Fresh baseline must prevent compounding reductions");
            System.arraycopy(baseline, 0, carA, 0, baseline.length);
            LegacyAxleDrift.applyWheel(carA, 2, true, 1.0);
            check(Arrays.equals(carA, baseline), "Disabled/released output must be byte-identical to baseline");
        }
        float[] worn = { 1, .5f, .2f, 2.88f, 3.83f, .04f };
        LegacyAxleDrift.applyWheel(worn, 0, true, .45);
        check(Math.abs(worn[2] - .09f) < 1e-7, "Do not replace worn-tire friction with a healthy constant");
    }

    private static void invalidInput() {
        check(!LegacyAxleDrift.applyWheel(null, 0, true, .45), "Null array");
        float[] values = { 0, 30, 0, 2.88f, 3.83f, 0 };
        float[] original = values.clone();
        check(!LegacyAxleDrift.applyWheel(values, 0, true, .45) && Arrays.equals(values, original),
                "Missing tire must remain missing");
        check(!LegacyAxleDrift.applyWheel(values, Integer.MAX_VALUE, true, .45), "Index overflow");
        check(!LegacyAxleDrift.applyWheel(values, -1, true, .45), "Negative index");
        values[0] = 1;
        values[2] = Float.NaN;
        check(!LegacyAxleDrift.applyWheel(values, 0, true, .45), "NaN friction must not propagate");
        values[2] = 1;
        check(!LegacyAxleDrift.applyWheel(values, 0, true, Double.NaN), "NaN tuning fails neutral");
    }

    private static void entryRamp() {
        for (double dt : new double[] { 1.0 / 30, 1.0 / 60, 1.0 / 120 }) {
            double scale = 1;
            for (int tick = 0; tick < Math.round(.15 / dt) + 1; tick++) {
                double next = LegacyAxleDrift.enter(scale, .45, dt);
                check(next <= scale && next >= .45, "Entry must be bounded and monotonic");
                scale = next;
            }
            check(Math.abs(scale - .45) < 1e-10, "Entry ramp must be time based");
            check(LegacyAxleDrift.enter(scale, 1, dt) == 1, "Return to 1 is immediate");
        }
        check(LegacyAxleDrift.targetGrip(.45, 0) == 1, "Global zero intensity disables loss");
        check(LegacyAxleDrift.targetGrip(.45, 2) == .25, "Global intensity still respects safe minimum");
    }

    private static void modeIsolation() {
        LegacySlideDynamics.Output inactive = LegacySlideDynamics.Output.inactive();
        check(LegacyAxleDrift.withoutBodyOverlay(inactive) == inactive, "Inactive path returns same object");
        for (LegacySlideDynamics.Cause cause : LegacySlideDynamics.Cause.values()) {
            LegacySlideDynamics.Output original = new LegacySlideDynamics.Output(
                    LegacySlideDynamics.Mode.SLIDE, cause, .2, 2000, .4, .1, .8, 1.2,
                    .7, .3, 200, 2100, 3, true, true, true, .35);
            LegacySlideDynamics.Output output = LegacyAxleDrift.withoutBodyOverlay(original);
            if (cause != LegacySlideDynamics.Cause.DRIFT_KEY) {
                check(output == original, "Handbrake, power, clutch and natural modes must be untouched");
            } else {
                check(output.lateralForce() == 0 && output.bulletYawTorque() == 0
                                && output.driftRotation() == 0 && output.wheelFrictionScale() == 1,
                        "Never stack old body/global-friction overlay with rear-only mode");
                check(output.sideSlipAngleRadians() == original.sideSlipAngleRadians()
                                && output.skidSpeedMps() == original.skidSpeedMps()
                                && output.slideBlend() == original.slideBlend(),
                        "Keep observed motion for sound and tire marks");
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
