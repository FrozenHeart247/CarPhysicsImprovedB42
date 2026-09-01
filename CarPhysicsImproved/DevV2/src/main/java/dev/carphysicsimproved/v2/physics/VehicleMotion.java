package dev.carphysicsimproved.v2.physics;

/** Body-space motion observed from the native physics engine. */
public record VehicleMotion(
        double longitudinalSpeedMps,
        double lateralSpeedMps,
        double yawRateRadiansPerSecond,
        double roadGradeRadians) {

    public VehicleMotion(
            double longitudinalSpeedMps,
            double lateralSpeedMps,
            double yawRateRadiansPerSecond) {
        this(longitudinalSpeedMps, lateralSpeedMps, yawRateRadiansPerSecond, 0.0);
    }

    public static VehicleMotion stopped() {
        return new VehicleMotion(0.0, 0.0, 0.0, 0.0);
    }
}
