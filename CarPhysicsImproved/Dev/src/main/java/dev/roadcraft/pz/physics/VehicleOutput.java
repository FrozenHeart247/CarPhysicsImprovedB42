package zombie.roadcraft.physics;

/**
 * Result and diagnostics from one model step. Force values are magnitudes
 * except {@code rawDriveForceN}, {@code appliedDriveForceN}, and
 * {@code netLongitudinalForceN}, whose sign follows vehicle travel direction.
 */
public record VehicleOutput(
        VehicleState state,
        double sanitizedDeltaSeconds,
        double engineTorqueNm,
        double converterSlip,
        double converterTorqueMultiplier,
        double driveDeliveryFactor,
        double rawDriveForceN,
        double appliedDriveForceN,
        double tractionLimitN,
        double effectiveGripCoefficient,
        double serviceBrakeForceN,
        double parkingBrakeForceN,
        double aerodynamicDragForceN,
        double rollingDragForceN,
        double engineBrakingForceN,
        double netLongitudinalForceN,
        double steeringAngleRadians,
        boolean burnout,
        boolean rearWheelsLocked,
        boolean reverseLimited,
        boolean shifted) {
}
