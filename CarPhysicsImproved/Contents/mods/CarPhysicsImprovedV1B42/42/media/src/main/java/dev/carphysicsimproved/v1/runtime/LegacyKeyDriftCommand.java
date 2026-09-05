package dev.carphysicsimproved.v1.runtime;

/** Latest controller intent, consumed once per native step, never accumulated per frame. */
final class LegacyKeyDriftCommand {
    static final long MAX_AGE_NANOS = 250_000_000L;
    private volatile Request request;

    void publish(double torque, long now) {
        request = Double.isFinite(torque) && torque != 0.0 ? new Request(torque, now) : null;
    }

    double torqueForPhysicsStep(long now) {
        Request current = request;
        if (current == null) return 0.0;
        long age = now - current.updatedAt();
        return age >= 0 && age <= MAX_AGE_NANOS ? current.torque() : 0.0;
    }

    void clear() { request = null; }

    private record Request(double torque, long updatedAt) { }
}
