package dev.carphysicsimproved.v1.physics;

/** Driver-trait modifiers owned by the V1 drivetrain rather than vanilla force output. */
public final class LegacyDriverTraits {
    private static final Modifiers NORMAL = new Modifiers("normal", 1.0, 1.0, 1.0);
    private static final Modifiers SUNDAY = new Modifiers("sunday-driver", 0.75, 0.75, 0.75);
    private static final Modifiers SPEED = new Modifiers("speed-demon", 1.25, 1.15, 1.20);

    private LegacyDriverTraits() {
    }

    public static Modifiers modifiers(boolean sundayDriver, boolean speedDemon) {
        // The vanilla character creator treats them as mutually exclusive.
        // Sunday Driver wins for malformed/modded characters carrying both.
        if (sundayDriver) {
            return SUNDAY;
        }
        return speedDemon ? SPEED : NORMAL;
    }

    public static Modifiers normal() {
        return NORMAL;
    }

    public record Modifiers(String telemetryName, double throttleResponse,
            double torqueMultiplier, double speedRangeMultiplier) {
        public Modifiers {
            telemetryName = telemetryName == null ? "normal" : telemetryName;
            throttleResponse = clamp(throttleResponse, 0.25, 2.0);
            torqueMultiplier = clamp(torqueMultiplier, 0.25, 2.0);
            speedRangeMultiplier = clamp(speedRangeMultiplier, 0.50, 1.50);
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
