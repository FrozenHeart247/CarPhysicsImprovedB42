package dev.carphysicsimproved.v2.physics;

/** Internal drivetrain/controller state; vehicle motion remains native-owned. */
public record DynamicsState(
        int gear,
        double engineRpm,
        double filteredThrottle,
        double steeringAngleRadians,
        double drivenWheelSpeedMps,
        double previousLongitudinalAccelerationMps2,
        double driftIntentSeconds,
        double previousLateralForceN,
        double previousYawTorqueNm) {

    public DynamicsState(
            int gear,
            double engineRpm,
            double filteredThrottle,
            double steeringAngleRadians,
            double drivenWheelSpeedMps,
            double previousLongitudinalAccelerationMps2) {
        this(gear, engineRpm, filteredThrottle, steeringAngleRadians,
                drivenWheelSpeedMps, previousLongitudinalAccelerationMps2,
                0.0, 0.0, 0.0);
    }

    public static DynamicsState stopped(VehicleSpec specification) {
        return new DynamicsState(
                1,
                specification.engine().idleRpm(),
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0);
    }
}
