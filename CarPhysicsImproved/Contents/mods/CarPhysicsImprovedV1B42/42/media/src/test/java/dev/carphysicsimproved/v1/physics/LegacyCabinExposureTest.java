package dev.carphysicsimproved.v1.physics;

public final class LegacyCabinExposureTest {
    private static final double EPSILON = 0.000_001;

    private LegacyCabinExposureTest() {
    }

    public static void main(String[] args) {
        assertNear(0.0, LegacyCabinExposure.windshieldExposure(true, true, false, 41),
                "healthy glass must seal the cabin");
        assertNear(0.45, LegacyCabinExposure.windshieldExposure(true, true, false, 40),
                "40 percent must start the damaged-glass effect");
        assertNear(1.0, LegacyCabinExposure.windshieldExposure(true, true, false, 0),
                "zero-condition glass must be fully exposed");
        assertNear(1.0, LegacyCabinExposure.windshieldExposure(false, false, false, 100),
                "missing windshield part must expose the cabin");
        assertNear(1.0, LegacyCabinExposure.windshieldExposure(true, false, false, 100),
                "missing windshield item must expose the cabin");
        assertNear(1.0, LegacyCabinExposure.windshieldExposure(true, true, true, 100),
                "destroyed vehicle window must expose the cabin");

        assertNear(0.0, LegacyCabinExposure.windChillAmount(10.0, 1.0),
                "low speed must not show Windchill");
        assertBetween(5.0, 10.0, LegacyCabinExposure.windChillAmount(30.0, 1.0),
                "30 km/h full exposure should produce Windchill level one");
        assertBetween(20.0, 25.0, LegacyCabinExposure.windChillAmount(75.0, 1.0),
                "75 km/h full exposure should produce Windchill level four");
        assertNear(LegacyCabinExposure.windChillAmount(60.0, 1.0),
                LegacyCabinExposure.windChillAmount(-60.0, 1.0),
                "reverse speed must use the same airflow magnitude");
        assertNear(0.0, LegacyCabinExposure.windChillAmount(100.0, 0.0),
                "sealed glass must suppress Windchill");

        assertNear(0.0, LegacyCabinExposure.rainExposure(60.0, 1.0, 0.0),
                "sealed glass must suppress rain");
        assertNear(1.0, LegacyCabinExposure.rainExposure(60.0, 1.0, 1.0),
                "heavy rain at speed must clamp to full vanilla exposure");
        assertNear(0.0, LegacyCabinExposure.rainExposure(20.0, 0.2, 1.0),
                "tiny exposure must retain vanilla's rain cutoff");
        assertNear(LegacyCabinExposure.rainExposure(40.0, 0.8, 0.7),
                LegacyCabinExposure.rainExposure(-40.0, 0.8, 0.7),
                "rain must use absolute speed");

        System.out.println("LegacyCabinExposureTest: all assertions passed");
    }

    private static void assertNear(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > EPSILON) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertBetween(double lowerExclusive, double upperInclusive,
            double actual, String message) {
        if (!(actual > lowerExclusive && actual <= upperInclusive)) {
            throw new AssertionError(message + ": actual=" + actual);
        }
    }
}
