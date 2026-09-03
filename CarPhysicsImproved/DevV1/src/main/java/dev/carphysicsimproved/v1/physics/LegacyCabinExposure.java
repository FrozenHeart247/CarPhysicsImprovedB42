package dev.carphysicsimproved.v1.physics;

/** Pure calculations for airflow and rain entering through the front windshield. */
public final class LegacyCabinExposure {
    public static final int DAMAGED_WINDSHIELD_THRESHOLD = 40;

    private static final double DAMAGED_THRESHOLD_EXPOSURE = 0.45;
    private static final double WIND_SPEED_THRESHOLD_KPH = 10.0;
    private static final double WIND_CHILL_PER_KPH = 0.32;
    private static final double MAX_WIND_CHILL_AMOUNT = 25.0;
    private static final double RAIN_SPEED_REFERENCE_KPH = 50.0;
    private static final double MIN_RAIN_EXPOSURE = 0.1;

    private LegacyCabinExposure() {
    }

    /**
     * Returns cabin exposure in the 0..1 range. Missing or destroyed glass is
     * fully open; an installed windshield starts leaking at exactly 40%.
     */
    public static double windshieldExposure(
            boolean partPresent,
            boolean itemPresent,
            boolean windowDestroyed,
            int condition) {
        if (!partPresent || !itemPresent || windowDestroyed) {
            return 1.0;
        }
        if (condition > DAMAGED_WINDSHIELD_THRESHOLD) {
            return 0.0;
        }
        double clampedCondition = clamp(condition, 0.0, DAMAGED_WINDSHIELD_THRESHOLD);
        double damageBelowThreshold = 1.0 - clampedCondition / DAMAGED_WINDSHIELD_THRESHOLD;
        return DAMAGED_THRESHOLD_EXPOSURE
                + (1.0 - DAMAGED_THRESHOLD_EXPOSURE) * damageBelowThreshold;
    }

    /**
     * Produces the same scalar consumed by the vanilla Windchill moodle:
     * levels start above 5/10/15/20. Full exposure reaches those levels at
     * approximately 26/42/57/73 km/h.
     */
    public static double windChillAmount(double speedKph, double exposure) {
        if (!Double.isFinite(speedKph) || !Double.isFinite(exposure)) {
            return 0.0;
        }
        double airflow = Math.max(0.0, Math.abs(speedKph) - WIND_SPEED_THRESHOLD_KPH);
        return clamp(airflow * WIND_CHILL_PER_KPH * clamp(exposure, 0.0, 1.0),
                0.0, MAX_WIND_CHILL_AMOUNT);
    }

    /** Mirrors B42's speed-and-rain curve, then scales it by glass exposure. */
    public static double rainExposure(double speedKph, double rainIntensity, double exposure) {
        if (!Double.isFinite(speedKph)
                || !Double.isFinite(rainIntensity)
                || !Double.isFinite(exposure)) {
            return 0.0;
        }
        double rain = clamp(rainIntensity, 0.0, 1.0);
        double result = rain * rain
                * (Math.abs(speedKph) / RAIN_SPEED_REFERENCE_KPH)
                * clamp(exposure, 0.0, 1.0);
        return result < MIN_RAIN_EXPOSURE ? 0.0 : clamp(result, 0.0, 1.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
