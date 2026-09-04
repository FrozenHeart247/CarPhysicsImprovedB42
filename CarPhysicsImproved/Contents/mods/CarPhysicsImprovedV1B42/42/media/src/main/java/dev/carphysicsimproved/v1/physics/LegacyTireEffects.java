package dev.carphysicsimproved.v1.physics;

/**
 * Converts drivetrain and slide telemetry into one debounced, normalized tire-slip
 * signal for cosmetic sound and marks. Steering load alone is deliberately not
 * sufficient: there must be wheelspin, a physically established slide, or braking
 * with native wheel lock.
 */
public final class LegacyTireEffects {
    private LegacyTireEffects() {
    }

    public static Output step(Input rawInput, State state, double rawDeltaSeconds) {
        Input input = rawInput == null ? Input.idle() : rawInput;
        double dt = clamp(rawDeltaSeconds, 1.0 / 240.0, 0.05);
        double speed = Math.abs(finite(input.longitudinalSpeedMps(), 0.0));
        double nativeRearSkid = clamp(finite(input.rearNativeSkid(), 0.0), 0.0, 1.0);
        double slideBlend = clamp(finite(input.slideBlend(), 0.0), 0.0, 1.0);
        double physicalSideSlip = Math.max(
                Math.abs(finite(input.lateralSpeedMps(), 0.0)),
                Math.abs(finite(input.sideSlipAngleRadians(), 0.0)) * speed);

        double burnout = smoothStep(clamp(
                (finite(input.burnoutSpeedKph(), 0.0) - 2.5) / 8.0, 0.0, 1.0));
        boolean intentionalEvidence = input.intentionalSlide() && slideBlend >= 0.08
                && (physicalSideSlip >= 0.35 || nativeRearSkid >= 0.14);
        boolean naturalEvidence = input.sliding() && slideBlend >= 0.18
                && (physicalSideSlip >= 0.70 || nativeRearSkid >= 0.24);
        boolean brakingEvidence = speed >= 4.0
                && (input.handbrake() >= 0.55 || input.serviceBrake() >= 0.70)
                && nativeRearSkid >= 0.18;

        double slide = 0.0;
        if (intentionalEvidence || naturalEvidence) {
            double sideAmount = clamp((physicalSideSlip - 0.20) / 1.65, 0.0, 1.0);
            double wheelAmount = clamp((nativeRearSkid - 0.08) / 0.42, 0.0, 1.0);
            slide = Math.max(sideAmount, wheelAmount) * Math.max(0.25, slideBlend);
        }
        double braking = brakingEvidence
                ? clamp((nativeRearSkid - 0.12) / 0.45, 0.0, 1.0)
                : 0.0;
        double rawIntensity = clamp(Math.max(burnout, Math.max(slide, braking)), 0.0, 1.0);

        boolean evidence = rawIntensity >= 0.06;
        if (evidence) {
            state.evidenceSeconds = clamp(state.evidenceSeconds + dt, 0.0, 0.30);
        } else {
            state.evidenceSeconds = Math.max(0.0, state.evidenceSeconds - dt * 4.0);
        }

        double entryDelay = burnout >= 0.25 ? 0.05 : 0.10;
        if (!state.active && evidence && state.evidenceSeconds >= entryDelay) {
            state.active = true;
        } else if (state.active && rawIntensity < 0.035 && state.evidenceSeconds <= 0.01) {
            state.active = false;
        }

        double target = state.active ? rawIntensity : 0.0;
        state.intensity = approach(state.intensity, target,
                (target > state.intensity ? 10.0 : 4.5) * dt);
        if (!state.active && state.intensity < 0.01) {
            state.intensity = 0.0;
        }
        return new Output(state.intensity, state.active, burnout, slide, braking);
    }

    private static double smoothStep(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double approach(double value, double target, double maximumDelta) {
        if (value < target) {
            return Math.min(target, value + maximumDelta);
        }
        return Math.max(target, value - maximumDelta);
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Input(double burnoutSpeedKph, double longitudinalSpeedMps,
            double lateralSpeedMps, double sideSlipAngleRadians, double slideBlend,
            boolean intentionalSlide, boolean sliding, double serviceBrake,
            double handbrake, double rearNativeSkid) {
        public static Input idle() {
            return new Input(0.0, 0.0, 0.0, 0.0, 0.0,
                    false, false, 0.0, 0.0, 0.0);
        }
    }

    public static final class State {
        public double evidenceSeconds;
        public double intensity;
        public boolean active;

        public void reset() {
            evidenceSeconds = 0.0;
            intensity = 0.0;
            active = false;
        }
    }

    public record Output(double intensity, boolean active, double burnout,
            double slide, double braking) {
    }
}
