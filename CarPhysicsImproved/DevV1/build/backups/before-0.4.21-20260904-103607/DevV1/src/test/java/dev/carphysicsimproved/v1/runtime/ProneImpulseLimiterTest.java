package dev.carphysicsimproved.v1.runtime;

public final class ProneImpulseLimiterTest {
    private ProneImpulseLimiterTest() {
    }

    public static void main(String[] args) {
        ProneImpulseLimiter limiter = new ProneImpulseLimiter();
        Object firstVehicle = new Object();
        Object secondVehicle = new Object();

        assertClose(1.0, limiter.scaleFor(firstVehicle, true, 1.0), "first bump");
        for (int step = 1; step < ProneImpulseLimiter.COOLDOWN_STEPS; step++) {
            assertClose(0.0, limiter.scaleFor(firstVehicle, true, 1.0), "cooldown step " + step);
        }
        assertClose(1.0, limiter.scaleFor(firstVehicle, true, 1.0), "bump after cooldown");
        assertClose(0.4, limiter.scaleFor(secondVehicle, true, 0.4), "per-vehicle state");
        assertClose(0.0, limiter.scaleFor(new Object(), true, 0.0), "disabled bump");

        Object clearedByTime = new Object();
        assertClose(0.7, limiter.scaleFor(clearedByTime, true, 0.7), "timed bump");
        for (int step = 1; step <= ProneImpulseLimiter.COOLDOWN_STEPS; step++) {
            limiter.scaleFor(clearedByTime, false, 0.7);
        }
        assertClose(0.7, limiter.scaleFor(clearedByTime, true, 0.7), "cooldown without contact");
        System.out.println("ProneImpulseLimiterTest: separated corpse bump cooldown passed");
    }

    private static void assertClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > 1.0E-9) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
