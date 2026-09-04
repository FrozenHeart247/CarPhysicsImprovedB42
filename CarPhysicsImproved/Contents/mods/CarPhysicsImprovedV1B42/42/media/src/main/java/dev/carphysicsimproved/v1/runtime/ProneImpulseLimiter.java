package dev.carphysicsimproved.v1.runtime;

import java.util.WeakHashMap;

/**
 * Keeps the B42 wheel-over-body lift, but prevents a fresh vertical force from
 * being applied on every 10 ms physics substep while a wheel remains on the
 * same body. Eight substeps matches the 0.08 second limiter used by the legacy
 * handling model.
 */
final class ProneImpulseLimiter {
    static final int COOLDOWN_STEPS = 8;

    private final WeakHashMap<Object, Integer> remainingSteps = new WeakHashMap<>();

    synchronized double scaleFor(Object vehicle, boolean hasPendingImpulse, double configuredScale) {
        if (vehicle == null) {
            return 0.0;
        }

        int remaining = Math.max(0, remainingSteps.getOrDefault(vehicle, 0) - 1);
        double scale = 0.0;
        if (hasPendingImpulse && remaining == 0 && Double.isFinite(configuredScale)
                && configuredScale > 0.0) {
            scale = configuredScale;
            remaining = COOLDOWN_STEPS;
        }

        if (remaining == 0) {
            remainingSteps.remove(vehicle);
        } else {
            remainingSteps.put(vehicle, remaining);
        }
        return scale;
    }
}
