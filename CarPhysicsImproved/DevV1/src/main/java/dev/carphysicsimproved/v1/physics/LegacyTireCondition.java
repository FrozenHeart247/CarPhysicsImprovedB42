package dev.carphysicsimproved.v1.physics;

/** Separates tire construction quality from the nonlinear loss caused by wear. */
public final class LegacyTireCondition {
    private static final double WORN_TIRE_FLOOR = 0.40;
    private static final double WEAR_EXPONENT = 1.50;

    private LegacyTireCondition() {
    }

    public static double gripMultiplier(double durabilityFraction, double baseWheelFriction) {
        double durability = clamp(durabilityFraction, 0.0, 1.0);
        double qualityGrip = 0.5 + 0.5 * clamp(baseWheelFriction, 0.0, 1.25);
        double wearGrip = WORN_TIRE_FLOOR
                + (1.0 - WORN_TIRE_FLOOR) * Math.pow(durability, WEAR_EXPONENT);
        return clamp(qualityGrip * wearGrip, 0.05, 1.25);
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
