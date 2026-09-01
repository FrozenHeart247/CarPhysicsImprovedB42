package dev.carphysicsimproved.v2.physics;

import java.util.List;

/**
 * Version-neutral snapshot of a Project Zomboid VehicleScript. The runtime
 * adapter supplies this data; the physics core never imports game classes.
 */
public record ScriptVehicleData(
        String fullType,
        double massKg,
        double engineForce,
        double engineIdleRpm,
        double maximumSpeedKph,
        double wheelFriction,
        double steeringClampRadians,
        double centerOfMassForwardMeters,
        DriveLayout driveLayout,
        double reverseGearRatio,
        double[] forwardGearRatios,
        List<Wheel> wheels) {

    public ScriptVehicleData(
            String fullType,
            double massKg,
            double engineForce,
            double engineIdleRpm,
            double maximumSpeedKph,
            double wheelFriction,
            double steeringClampRadians,
            double centerOfMassForwardMeters,
            DriveLayout driveLayout,
            double[] forwardGearRatios,
            List<Wheel> wheels) {
        this(
                fullType,
                massKg,
                engineForce,
                engineIdleRpm,
                maximumSpeedKph,
                wheelFriction,
                steeringClampRadians,
                centerOfMassForwardMeters,
                driveLayout,
                Double.NaN,
                forwardGearRatios,
                wheels);
    }

    public ScriptVehicleData {
        forwardGearRatios = forwardGearRatios == null
                ? new double[0]
                : forwardGearRatios.clone();
        wheels = wheels == null ? List.of() : List.copyOf(wheels);
    }

    @Override
    public double[] forwardGearRatios() {
        return forwardGearRatios.clone();
    }

    /** Local wheel coordinates use X for left/right and Z for front/rear. */
    public record Wheel(
            String id,
            boolean front,
            double offsetX,
            double offsetZ,
            double radiusMeters,
            double widthMeters) {
    }
}
