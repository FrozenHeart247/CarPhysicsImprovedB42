package zombie.roadcraft.runtime;

/** Pure state-sequence checks for the rear-wheel visual adapter. */
public final class PzAccessWheelVisualTest {
    private static int assertions;

    private PzAccessWheelVisualTest() {
    }

    public static void main(String[] args) {
        nativeReadbackAndVisualWritesStaySeparated();
        invalidAndExtremeDeltasAreBounded();
        System.out.println("PzAccessWheelVisualTest: " + assertions + " assertions passed");
    }

    private static void nativeReadbackAndVisualWritesStaySeparated() {
        double lastNative = 0.0;
        double lastWritten = 0.0;
        double accumulated = 0.0;

        double observed = 0.10;
        double nativeDelta = PzAccess.wheelRotationDelta(observed, lastNative, lastWritten);
        close(0.10, nativeDelta, "first native readback");
        lastNative = observed;
        accumulated += nativeDelta + 1.0;
        lastWritten = accumulated;
        close(1.10, lastWritten, "first burnout visual write");

        // A second controller calculation without a Bullet readback observes
        // the value written above. It must not count that value as native motion.
        observed = lastWritten;
        nativeDelta = PzAccess.wheelRotationDelta(observed, lastNative, lastWritten);
        close(0.0, nativeDelta, "visual write is not fed back as native motion");
        check(!PzAccess.hasNativeWheelReadback(observed, lastWritten),
                "visual write is not classified as a native readback");
        accumulated += nativeDelta + 1.0;
        lastWritten = accumulated;
        close(2.10, lastWritten, "burnout grows linearly across repeated controller ticks");

        // WorldSimulation then replaces WheelInfo with Bullet's absolute raw
        // rotation. The physical delta is measured against the last raw sample.
        observed = 0.20;
        nativeDelta = PzAccess.wheelRotationDelta(observed, lastNative, lastWritten);
        close(0.10, nativeDelta, "fresh Bullet readback restores native delta tracking");
        check(PzAccess.hasNativeWheelReadback(observed, lastWritten),
                "fresh Bullet value is classified as a native readback");
    }

    private static void invalidAndExtremeDeltasAreBounded() {
        close(100.0, PzAccess.wheelRotationDelta(1_000.0, 0.0, -1.0),
                "positive native delta safety cap");
        close(-100.0, PzAccess.wheelRotationDelta(-1_000.0, 0.0, 1.0),
                "negative native delta safety cap");
        close(0.0, PzAccess.wheelRotationDelta(Double.NaN, 0.0, 1.0),
                "non-finite wheel input");
    }

    private static void close(double expected, double actual, String message) {
        assertions++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 1.0e-12) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
