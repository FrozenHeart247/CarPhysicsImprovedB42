package dev.carphysicsimproved.v1.physics;

/** Rear-only experiment. It never changes drivetrain inputs or generates body forces. */
public final class LegacyAxleDrift {
    private LegacyAxleDrift() { }

    public static boolean isDedicatedSlide(LegacySlideDynamics.Output output) {
        return output != null && output.intentionalSlide()
                && output.cause() == LegacySlideDynamics.Cause.DRIFT_KEY;
    }

    public static LegacySlideDynamics.Output withoutBodyOverlay(LegacySlideDynamics.Output output) {
        if (!isDedicatedSlide(output)) return output;
        return new LegacySlideDynamics.Output(output.mode(), output.cause(), output.sideSlipAngleRadians(),
                0.0, output.expectedYawRateRadiansPerSecond(), output.yawErrorRadiansPerSecond(),
                output.frontGripUse(), output.rearGripUse(), output.slideBlend(), output.controlBlend(),
                0.0, 0.0, output.skidSpeedMps(), output.yawSignCalibrated(), true, true, 1.0);
    }

    public static double grip(double value) {
        return Double.isFinite(value) ? Math.max(0.25, Math.min(1.0, value)) : 1.0;
    }

    public static double enter(double current, double target, double dt) {
        double safeDt = Double.isFinite(dt) ? Math.max(0.0, Math.min(0.05, dt)) : 0.0;
        target = grip(target);
        // Brief entry ramp; release is handled separately and always restores the baseline.
        return Math.max(target, Math.min(1.0, current) - (1.0 - target) * safeDt / 0.15);
    }

    public static double targetGrip(double rearGrip, double intensity) {
        double amount = Double.isFinite(intensity) ? Math.max(0.0, Math.min(2.0, intensity)) : 0.0;
        return grip(1.0 - (1.0 - grip(rearGrip)) * amount);
    }

    /** Called once AFTER vanilla has freshly filled this wheel's six fields. */
    public static boolean applyWheel(float[] data, int wheel, boolean rear, double multiplier) {
        if (data == null || wheel < 0 || wheel >= data.length / 6 || !rear) return false;
        int offset = wheel * 6;
        float baseline = data[offset + 2];
        double scale = grip(multiplier);
        if (data[offset] != 1.0f || !Float.isFinite(baseline) || baseline <= 0.0f || scale >= 1.0) return false;
        data[offset + 2] = (float) (baseline * scale);
        return true;
    }
}
