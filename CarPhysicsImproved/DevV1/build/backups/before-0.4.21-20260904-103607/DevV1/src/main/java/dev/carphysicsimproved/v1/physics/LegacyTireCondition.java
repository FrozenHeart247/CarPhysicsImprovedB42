package dev.carphysicsimproved.v1.physics;

/** Separates tire construction quality from the nonlinear loss caused by wear. */
public final class LegacyTireCondition {
    private static final double DESTROYED_TIRE_GRIP = 0.08;
    private static final double WORN_THRESHOLD = 0.70;
    private static final double WORN_THRESHOLD_GRIP = 0.75;
    private static final double DEEP_WEAR_EXPONENT = 1.60;

    private LegacyTireCondition() {
    }

    public static double gripMultiplier(double durabilityFraction, double baseWheelFriction) {
        double durability = clamp(durabilityFraction, 0.0, 1.0);
        double qualityGrip = 0.5 + 0.5 * clamp(baseWheelFriction, 0.0, 1.25);
        double wearGrip;
        if (durability >= WORN_THRESHOLD) {
            double healthyRange = (durability - WORN_THRESHOLD) / (1.0 - WORN_THRESHOLD);
            wearGrip = WORN_THRESHOLD_GRIP
                    + (1.0 - WORN_THRESHOLD_GRIP) * healthyRange;
        } else {
            double wornRange = durability / WORN_THRESHOLD;
            wearGrip = DESTROYED_TIRE_GRIP
                    + (WORN_THRESHOLD_GRIP - DESTROYED_TIRE_GRIP)
                            * Math.pow(wornRange, DEEP_WEAR_EXPONENT);
        }
        return clamp(qualityGrip * wearGrip, 0.05, 1.25);
    }

    /** Native tire pressure contribution: half pressure is already severely soft. */
    public static double pressureMultiplier(double pressureFraction) {
        double pressure = clamp(pressureFraction, 0.0, 1.35);
        if (pressure < 0.85) {
            return lerp(0.08, 1.0, pressure / 0.85);
        }
        return lerp(1.0, 0.84, clamp((pressure - 1.10) / 0.25, 0.0, 1.0));
    }

    /** Grip attributable to tire hardware only, independent of road and weather. */
    public static double hardwareGrip(double pressureFraction, double conditionGrip) {
        return clamp(conditionGrip, 0.0, 1.25) * pressureMultiplier(pressureFraction);
    }

    /** Damaged and soft tires turn both more slowly and through a smaller useful angle. */
    public static double steeringAuthority(double hardwareGrip) {
        return clamp(0.20 + 0.80 * clamp(hardwareGrip, 0.0, 1.0), 0.20, 1.0);
    }

    /** Keeps a small residual contact patch while allowing destroyed tires to slide. */
    public static double nativeFrictionScale(double hardwareGrip) {
        return clamp(hardwareGrip, 0.12, 1.0);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * clamp(amount, 0.0, 1.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
