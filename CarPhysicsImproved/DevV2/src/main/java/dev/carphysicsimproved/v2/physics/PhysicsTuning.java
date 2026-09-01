package dev.carphysicsimproved.v2.physics;

/** Server/sandbox tuning applied without changing the derived vehicle profile. */
public record PhysicsTuning(
        double enginePowerMultiplier,
        double roadResistanceMultiplier,
        double steeringSensitivityMultiplier,
        double driftEntryDelaySeconds) {
    private static final PhysicsTuning DEFAULT = new PhysicsTuning(1.0, 1.0, 1.0, 1.50);

    public PhysicsTuning {
        enginePowerMultiplier = sanitize(enginePowerMultiplier, 0.50, 1.50);
        roadResistanceMultiplier = sanitize(roadResistanceMultiplier, 0.25, 2.00);
        steeringSensitivityMultiplier = sanitize(steeringSensitivityMultiplier, 0.50, 1.50);
        driftEntryDelaySeconds = sanitize(driftEntryDelaySeconds, 0.50, 3.00);
    }

    public PhysicsTuning(double enginePowerMultiplier, double roadResistanceMultiplier) {
        this(enginePowerMultiplier, roadResistanceMultiplier, 1.0, 1.50);
    }

    public static PhysicsTuning defaults() {
        return DEFAULT;
    }

    private static double sanitize(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return 1.0;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
