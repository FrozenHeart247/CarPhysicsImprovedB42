package dev.carphysicsimproved.v2.physics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sanitized, immutable physics specification derived from VehicleScript data.
 * No rule in this class depends on a vanilla or modded vehicle name.
 */
public record VehicleSpec(
        String fullType,
        double massKg,
        double maximumSpeedKph,
        Engine engine,
        Transmission transmission,
        Chassis chassis,
        Tires tires,
        Steering steering,
        DriveLayout driveLayout,
        List<ScriptVehicleData.Wheel> wheels) {

    private static final double TWO_PI = Math.PI * 2.0;

    public VehicleSpec {
        fullType = fullType == null || fullType.isBlank() ? "unknown" : fullType;
        wheels = wheels == null ? List.of() : List.copyOf(wheels);
    }

    public static VehicleSpec fromScript(ScriptVehicleData data) {
        if (data == null) {
            throw new IllegalArgumentException("VehicleScript snapshot is required");
        }

        double mass = clamp(finite(data.massKg(), 1_200.0), 500.0, 12_000.0);
        List<ScriptVehicleData.Wheel> wheels = sanitizeWheels(data.wheels());
        double frontZ = axleMeanZ(wheels, true, 1.2);
        double rearZ = axleMeanZ(wheels, false, -1.2);
        if (frontZ <= rearZ + 1.2) {
            frontZ = 1.2;
            rearZ = -1.2;
        }
        double cgZ = clamp(finite(data.centerOfMassForwardMeters(), 0.0), rearZ + 0.35, frontZ - 0.35);
        double cgToFront = frontZ - cgZ;
        double cgToRear = cgZ - rearZ;
        double wheelbase = cgToFront + cgToRear;
        double track = deriveTrack(wheels);
        double radius = deriveWheelRadius(wheels);

        double[] ratios = sanitizeRatios(data.forwardGearRatios());
        double reverseRatio = clamp(finite(data.reverseGearRatio(), ratios[0] * 0.92), 0.4, 6.5);
        double idleRpm = clamp(finite(data.engineIdleRpm(), 850.0), 550.0, 1_600.0);
        double maximumSpeedKph = clamp(finite(data.maximumSpeedKph(), 150.0), 45.0, 360.0);
        double redlineRpm = clamp(Math.max(4_500.0, idleRpm * 6.2), 4_500.0, 8_500.0);
        double topRatio = ratios[ratios.length - 1];
        double topWheelRpm = maximumSpeedKph / 3.6 / radius * 60.0 / TWO_PI;
        double finalDrive = clamp(redlineRpm * 0.91 / Math.max(100.0, topWheelRpm * topRatio), 2.4, 5.2);

        // PZ engineForce is a relative script value, not SI torque. This mapping
        // deliberately stays continuous so arbitrary Workshop vehicles work.
        double engineForce = clamp(finite(data.engineForce(), 3_000.0), 500.0, 20_000.0);
        double peakTorque = clamp(90.0 + engineForce * 0.040, 115.0, 720.0);
        double wheelFriction = clamp(finite(data.wheelFriction(), 1.0), 0.35, 2.5);
        double dryGrip = clamp(0.58 + wheelFriction * 0.34, 0.62, 1.35);
        double frontWeightShare = clamp(cgToRear / wheelbase, 0.35, 0.72);

        return new VehicleSpec(
                data.fullType(),
                mass,
                maximumSpeedKph,
                new Engine(idleRpm, redlineRpm, peakTorque, 0.055),
                new Transmission(ratios, reverseRatio, finalDrive, 0.86),
                new Chassis(wheelbase, track, radius, cgToFront, cgToRear, frontWeightShare, 0.55),
                new Tires(dryGrip, mass * 37.0, mass * 40.0, 0.014, 0.10),
                new Steering(
                        clamp(finite(data.steeringClampRadians(), 0.58), 0.25, 0.95),
                        1.35,
                        3.2,
                        0.020),
                data.driveLayout() == null ? DriveLayout.REAR : data.driveLayout(),
                wheels);
    }

    private static List<ScriptVehicleData.Wheel> sanitizeWheels(List<ScriptVehicleData.Wheel> source) {
        if (source == null || source.isEmpty()) {
            return List.of(
                    new ScriptVehicleData.Wheel("FrontLeft", true, -0.78, 1.2, 0.32, 0.20),
                    new ScriptVehicleData.Wheel("FrontRight", true, 0.78, 1.2, 0.32, 0.20),
                    new ScriptVehicleData.Wheel("RearLeft", false, -0.78, -1.2, 0.32, 0.20),
                    new ScriptVehicleData.Wheel("RearRight", false, 0.78, -1.2, 0.32, 0.20));
        }
        List<ScriptVehicleData.Wheel> result = new ArrayList<>();
        int index = 0;
        for (ScriptVehicleData.Wheel wheel : source) {
            if (wheel == null) {
                continue;
            }
            result.add(new ScriptVehicleData.Wheel(
                    wheel.id() == null || wheel.id().isBlank() ? "Wheel" + index : wheel.id(),
                    wheel.front(),
                    clamp(finite(wheel.offsetX(), 0.0), -3.0, 3.0),
                    clamp(finite(wheel.offsetZ(), wheel.front() ? 1.2 : -1.2), -6.0, 6.0),
                    clamp(finite(wheel.radiusMeters(), 0.32), 0.18, 0.85),
                    clamp(finite(wheel.widthMeters(), 0.20), 0.08, 0.65)));
            index++;
        }
        return result.isEmpty() ? sanitizeWheels(List.of()) : List.copyOf(result);
    }

    private static double[] sanitizeRatios(double[] source) {
        double[] fallback = {3.45, 2.16, 1.48, 1.12, 0.88};
        if (source == null || source.length == 0) {
            return fallback;
        }
        return Arrays.stream(source)
                .filter(value -> Double.isFinite(value) && value > 0.2 && value < 7.0)
                .limit(8)
                .toArray().length == 0
                ? fallback
                : Arrays.stream(source)
                        .filter(value -> Double.isFinite(value) && value > 0.2 && value < 7.0)
                        .limit(8)
                        .toArray();
    }

    private static double axleMeanZ(List<ScriptVehicleData.Wheel> wheels, boolean front, double fallback) {
        return wheels.stream().filter(wheel -> wheel.front() == front)
                .mapToDouble(ScriptVehicleData.Wheel::offsetZ).average().orElse(fallback);
    }

    private static double deriveTrack(List<ScriptVehicleData.Wheel> wheels) {
        double min = wheels.stream().mapToDouble(ScriptVehicleData.Wheel::offsetX).min().orElse(-0.78);
        double max = wheels.stream().mapToDouble(ScriptVehicleData.Wheel::offsetX).max().orElse(0.78);
        return clamp(max - min, 1.05, 2.8);
    }

    private static double deriveWheelRadius(List<ScriptVehicleData.Wheel> wheels) {
        return clamp(wheels.stream().mapToDouble(ScriptVehicleData.Wheel::radiusMeters)
                .average().orElse(0.32), 0.18, 0.85);
    }

    static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Engine(double idleRpm, double redlineRpm, double peakTorqueNm, double engineBrakeFraction) {
    }

    public record Transmission(double[] forwardRatios, double reverseRatio, double finalDrive, double efficiency) {
        public Transmission {
            forwardRatios = forwardRatios.clone();
        }

        @Override
        public double[] forwardRatios() {
            return forwardRatios.clone();
        }

        public int gearCount() {
            return forwardRatios.length;
        }

        public double ratio(int oneBasedGear) {
            if (oneBasedGear < 0) {
                return reverseRatio;
            }
            if (oneBasedGear == 0) {
                return 0.0;
            }
            return forwardRatios[Math.max(1, Math.min(forwardRatios.length, oneBasedGear)) - 1];
        }
    }

    public record Chassis(
            double wheelbaseMeters,
            double trackMeters,
            double wheelRadiusMeters,
            double cgToFrontAxleMeters,
            double cgToRearAxleMeters,
            double frontStaticWeightShare,
            double centerOfMassHeightMeters) {
    }

    public record Tires(
            double dryGripCoefficient,
            double frontCorneringStiffnessNPerRad,
            double rearCorneringStiffnessNPerRad,
            double rollingResistanceCoefficient,
            double loadSensitivity) {
    }

    public record Steering(
            double maximumAngleRadians,
            double inputRateRadiansPerSecond,
            double returnRateRadiansPerSecond,
            double speedSensitivityPerMps) {
    }
}
