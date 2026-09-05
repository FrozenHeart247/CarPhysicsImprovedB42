package dev.carphysicsimproved.v1.physics;

/** Optional low-speed delivery calibration; never edits native mass or tire grip. */
public final class LegacyLaunchDynamics {
    public static final double DEFAULT_HEAVY_MULTIPLIER = 0.50;
    private static final double SURFACE_TRANSITION_SECONDS = 0.35;

    private LegacyLaunchDynamics() { }

    public static double sanitize(double multiplier) {
        return Double.isFinite(multiplier) ? clamp(multiplier, .25, 1.0) : DEFAULT_HEAVY_MULTIPLIER;
    }

    /** Per-owned-vehicle gate; reuses the terrain snapshot instead of scanning weather/wheels again. */
    public static double forceScale(LegacyPhysics.Spec spec, int gear, double nativeSpeed,
            double heavyMultiplier, LegacyTerrainDynamics.Output terrain, State state, double deltaSeconds) {
        double target = dryRoad(terrain) ? 1.0 : 0.0;
        if (!state.initialized) {
            // Entering/spawning a car in rain or on dirt must not start with a dry-road penalty.
            state.dryRoadWeight = target;
            state.initialized = true;
        } else {
            double dt = Double.isFinite(deltaSeconds) ? clamp(deltaSeconds, 0.0, .05) : 0.0;
            double change = dt / SURFACE_TRANSITION_SECONDS;
            state.dryRoadWeight = target > state.dryRoadWeight
                    ? Math.min(target, state.dryRoadWeight + change)
                    : Math.max(target, state.dryRoadWeight - change);
        }
        double dryScale = forceScale(spec, gear, nativeSpeed, heavyMultiplier);
        if (dryScale == 1.0 || state.dryRoadWeight == 0.0) return 1.0;
        if (state.dryRoadWeight == 1.0) return dryScale; // Exact accepted dry-road curve.
        return 1.0 - (1.0 - dryScale) * state.dryRoadWeight;
    }

    private static boolean dryRoad(LegacyTerrainDynamics.Output terrain) {
        // TERRAIN is a vehicle capability profile, not the surface under the car.
        // Likewise, grip can be 1 even in rain/dirt when Sandbox recovery is 100%.
        return terrain != null && !terrain.offroad() && !terrain.forest()
                && Double.isFinite(terrain.rainIntensity()) && terrain.rainIntensity() <= 0.0
                && Double.isFinite(terrain.snowIntensity()) && terrain.snowIntensity() <= 0.0;
    }

    public static double forceScale(LegacyPhysics.Spec spec, int gear, double nativeSpeed,
            double heavyMultiplier) {
        double multiplier = sanitize(heavyMultiplier);
        if (spec.mechanicType() != 2 || gear <= 0 || multiplier == 1.0
                || !Double.isFinite(nativeSpeed)) return 1.0;

        // Use TOTAL native speed, not its longitudinal projection. Turning sideways
        // during a fast drift must not make the car appear to be launching again.
        // These thresholds use PZ's native speed scale, not a new mph/kmh conversion.
        double speed = Math.abs(nativeSpeed);
        double fullEffectEnd = clamp(spec.maximumSpeedKph() * .25, 10.0, 20.0);
        double fadeEnd = clamp(spec.maximumSpeedKph() * .75, 30.0, 70.0);
        if (speed >= fadeEnd) return 1.0;
        if (speed <= fullEffectEnd) return multiplier;
        double t = (speed - fullEffectEnd) / (fadeEnd - fullEffectEnd);
        double smooth = t * t * (3.0 - 2.0 * t);
        return multiplier + (1.0 - multiplier) * smooth;
    }

    public static LegacyPhysics.Output withDelivery(LegacyPhysics.Output source, double scale) {
        double safeScale = Double.isFinite(scale) ? clamp(scale, .25, 1.0) : 1.0;
        if (safeScale == 1.0 || source.gear() <= 0 || source.engineForce() <= 0.0) return source;
        // Scale AFTER the existing traction cap. Engine-side torque reduction alone
        // cannot tame a saturated launch. Preserve raw torque/slip for the existing
        // burnout and slide observers: this is not an extra loss of tire friction.
        return new LegacyPhysics.Output(source.gear(), source.engineForce() * safeScale,
                source.brakingForce(), source.steeringRadians(), source.dragMagnitude(),
                source.tireTraction(), source.burnoutSpeedKph(), source.engineRpm(),
                source.throttle(), source.rawDriveForce(), source.clutchKickIntensity());
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static final class State {
        private boolean initialized;
        private double dryRoadWeight;
    }
}
