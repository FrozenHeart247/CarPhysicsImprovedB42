package dev.carphysicsimproved.v1.runtime;

import dev.carphysicsimproved.v1.physics.LegacySlideDynamics;

/** Latest non-key slide force, calibrated to the previous 60 FPS command rate. */
final class LegacySlideCommand {
    static final double REFERENCE_STEP_GAIN = 60.0 / 100.0;
    private volatile Request request;

    void publish(LegacySlideDynamics.Output output, long now) {
        request = output == null || !Double.isFinite(output.lateralForce())
                || !Double.isFinite(output.bulletYawTorque())
                || output.lateralForce() == 0.0 && output.bulletYawTorque() == 0.0
                ? null : new Request(output.lateralForce() * REFERENCE_STEP_GAIN,
                        output.bulletYawTorque() * REFERENCE_STEP_GAIN, output.intentionalSlide(), now);
    }

    Request forStep(long now) {
        Request value = request;
        long age = value == null ? -1 : now - value.updatedAt();
        return age >= 0 && age <= LegacyKeyDriftCommand.MAX_AGE_NANOS ? value : null;
    }

    void clear() { request = null; }

    record Request(double lateralForce, double yawTorque, boolean intentional, long updatedAt) { }
}
