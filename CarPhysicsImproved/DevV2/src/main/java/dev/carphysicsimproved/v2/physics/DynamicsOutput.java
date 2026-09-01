package dev.carphysicsimproved.v2.physics;

/** Force commands and diagnostics produced by one deterministic V2 step. */
public record DynamicsOutput(
        DynamicsState state,
        double deltaSeconds,
        double clutchCoupling,
        double engineTorqueNm,
        double rawDriveForceN,
        double propulsionForceLimitN,
        double appliedDriveForceN,
        double resistanceForceN,
        double serviceBrakeForceN,
        double longitudinalForceN,
        double lateralForceN,
        double yawTorqueNm,
        double frontSlipAngleRadians,
        double rearSlipAngleRadians,
        double frontGripLimitN,
        double rearGripLimitN,
        double frontTireGripMultiplier,
        double rearTireGripMultiplier,
        double rollingResistanceMultiplier,
        double frontSaturation,
        double rearSaturation,
        double wheelSlipMps,
        boolean burnout,
        boolean drifting,
        boolean understeering,
        boolean shifted) {
}
