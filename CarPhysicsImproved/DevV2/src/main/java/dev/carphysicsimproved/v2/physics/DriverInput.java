package dev.carphysicsimproved.v2.physics;

/** One controller request. Values are sanitized by VehicleDynamics. */
public record DriverInput(
        double throttle,
        double serviceBrake,
        double handbrake,
        double steering,
        TransmissionMode transmissionMode,
        int requestedGear,
        double surfaceGripMultiplier) {

    public static DriverInput idle(TransmissionMode mode, int gear) {
        return new DriverInput(0.0, 0.0, 0.0, 0.0, mode, gear, 1.0);
    }
}
