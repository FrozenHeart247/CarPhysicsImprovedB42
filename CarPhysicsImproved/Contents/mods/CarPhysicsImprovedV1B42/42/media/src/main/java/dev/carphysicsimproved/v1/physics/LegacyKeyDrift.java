package dev.carphysicsimproved.v1.physics;

/** Reference-style key drift, independent of the natural/power/handbrake slide state machine. */
public final class LegacyKeyDrift {
    // Fixed calibration from the accepted RaceCar58 run (2026-09-04 11:20:17):
    // its former world-mass scale was ~0.511, or ~1.96 angular gain. Preserve
    // that response without making steering depend on other loaded vehicles.
    public static final double YAW_GAIN = 1.96;

    private LegacyKeyDrift() { }

    public record Tuning(double rotation, double grip, double steeringBoost, double minimumSpeedKph) {
        public Tuning {
            rotation = clamp(rotation, 0, 3000);
            grip = clamp(grip, .10, 1);
            steeringBoost = clamp(steeringBoost, 1, 2);
            minimumSpeedKph = clamp(minimumSpeedKph, 0, 60);
        }
        public static Tuning defaults() { return new Tuning(2000, .35, 1.5, 20); }
    }

    public static boolean active(boolean heldAndSafe, double speedKph, double steering, Tuning tuning) {
        // BVD gates on total native speed and current input, not forward gear,
        // longitudinal projection, throttle, accumulated slip or an upper speed cap.
        return heldAndSafe && Double.isFinite(speedKph) && Double.isFinite(steering)
                && Math.abs(speedKph) > tuning.minimumSpeedKph() && Math.abs(steering) > .25;
    }

    public static double torque(boolean active, double normalizedSteering, double nativeMass,
            double intensity, Tuning tuning) {
        if (!active || !Double.isFinite(nativeMass) || nativeMass <= 0) return 0;
        // Scale only with this body's native mass; Bullet retains its actual
        // inertia/shape. No world census, mass rewrites or persistent gain state.
        return clamp(normalizedSteering, -1, 1) * tuning.rotation() * nativeMass * .001
                * YAW_GAIN * clamp(intensity, 0, 2);
    }

    public static double gripMultiplier(double intensity, Tuning tuning) {
        return clamp(1.0 - (1.0 - tuning.grip()) * clamp(intensity, 0, 2), .10, 1);
    }

    public static double friction(double base, double hardwareAndSurfaceGrip, double driftGrip) {
        return Math.min(1.8, Math.max(0, finite(base, 0))
                * clamp(hardwareAndSurfaceGrip, 0, 1.8)) * clamp(driftGrip, .10, 1);
    }

    public static double steering(double previous, double normalizedInput, double nativeSpeedKph,
            double speedClamp, double hardwareGrip, double realDelta, LegacyPhysics.Settings settings,
            Tuning tuning) {
        double dt = clamp(realDelta, 1.0 / 240.0, .05);
        double ratio = clamp(Math.abs(nativeSpeedKph) / settings.steeringHighSpeedKph(), 0, 1);
        double authority = LegacyTireCondition.steeringAuthority(hardwareGrip);
        double gain = lerp(settings.steeringFactorLowSpeed(), settings.steeringFactorHighSpeed(), ratio)
                * authority;
        double center = lerp(settings.steeringCenteringLowSpeed(), settings.steeringCenteringHighSpeed(), ratio);
        double angle = finite(previous, 0);
        double input = clamp(normalizedInput, -1, 1);
        if (Math.abs(input) > .1) {
            // The reference compares native input with its oppositely signed angle.
            if ((input > 0) == (angle < 0)) gain *= settings.steeringSnapback();
            angle += (input - angle) * 3 * dt * gain * tuning.steeringBoost();
        } else if (Math.abs(angle) <= .04) {
            angle = 0;
        } else {
            angle = Math.signum(angle) * Math.max(0, Math.abs(angle) - center * 4 * dt);
        }
        double limit = clamp(speedClamp, .05, 1.5) * authority;
        return clamp(angle, -limit, limit);
    }

    public static LegacyPhysics.Output withSteering(LegacyPhysics.Output source, double steering) {
        return new LegacyPhysics.Output(source.gear(), source.engineForce(), source.brakingForce(), steering,
                source.dragMagnitude(), source.tireTraction(), source.burnoutSpeedKph(), source.engineRpm(),
                source.throttle(), source.rawDriveForce(), source.clutchKickIntensity());
    }

    public static LegacySlideDynamics.Output observation(boolean active, double torque, double rotation,
            double driftGrip, double longitudinal, double lateral, double nativeSkid) {
        double beta = Math.atan2(lateral, Math.max(1.5, Math.abs(longitudinal)));
        double skid = Math.max(Math.abs(lateral), Math.max(0, nativeSkid) * 3);
        return new LegacySlideDynamics.Output(
                active ? LegacySlideDynamics.Mode.SLIDE : LegacySlideDynamics.Mode.GRIP,
                active ? LegacySlideDynamics.Cause.DRIFT_KEY : LegacySlideDynamics.Cause.NONE,
                beta, active ? rotation : 0, 0, 0, 0, 0, active ? 1 : 0, 0,
                0, torque, skid, false, active, active, active ? driftGrip : 1);
    }

    private static double lerp(double a, double b, double amount) { return a + (b - a) * amount; }
    private static double finite(double value, double fallback) { return Double.isFinite(value) ? value : fallback; }
    private static double clamp(double value, double lo, double hi) { return Math.max(lo, Math.min(hi, finite(value, lo))); }
}
