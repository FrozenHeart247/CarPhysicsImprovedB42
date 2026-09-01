package zombie.roadcraft.physics;

import java.util.Objects;

/**
 * Immutable configuration for the standalone longitudinal vehicle model.
 *
 * <p>Units are SI unless a component name explicitly says otherwise. The
 * settings intentionally contain no Project Zomboid types, so the model can be
 * tested and replayed without starting the game.</p>
 */
public record PhysicsSettings(
        double massKg,
        double wheelRadiusMeters,
        double reverseSpeedCapMps,
        Engine engine,
        Transmission transmission,
        Converter converter,
        Grip grip,
        Brakes brakes,
        Resistance resistance,
        Steering steering,
        TimeStep timeStep) {

    public PhysicsSettings {
        requirePositive("massKg", massKg);
        requirePositive("wheelRadiusMeters", wheelRadiusMeters);
        requirePositive("reverseSpeedCapMps", reverseSpeedCapMps);
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(transmission, "transmission");
        Objects.requireNonNull(converter, "converter");
        Objects.requireNonNull(grip, "grip");
        Objects.requireNonNull(brakes, "brakes");
        Objects.requireNonNull(resistance, "resistance");
        Objects.requireNonNull(steering, "steering");
        Objects.requireNonNull(timeStep, "timeStep");
    }

    /**
     * A neutral, newly authored baseline for an ordinary passenger car. These
     * values are tuning defaults, not measurements copied from another mod.
     */
    public static PhysicsSettings standard() {
        return new PhysicsSettings(
                1_325.0,
                0.318,
                11.5,
                new Engine(825.0, 4_100.0, 6_750.0, 128.0, 258.0, 172.0, 6_200.0),
                new Transmission(
                        new double[] {3.35, 2.06, 1.39, 1.04, 0.81},
                        3.12,
                        3.72,
                        0.88,
                        1_850.0,
                        6_100.0),
                new Converter(1.85, 1_850.0),
                new Grip(0.96, 0.69, 0.54, 0.42, 0.58, 4.6, 2.1),
                new Brakes(14_200.0, 7_600.0, 0.40, 0.46),
                new Resistance(0.68, 0.0145),
                new Steering(Math.toRadians(32.5), 17.5, 0.26),
                new TimeStep(1.0 / 240.0, 0.1, 1.0 / 60.0));
    }

    public record Engine(
            double idleRpm,
            double peakTorqueRpm,
            double redlineRpm,
            double idleTorqueNm,
            double peakTorqueNm,
            double redlineTorqueNm,
            double rpmResponsePerSecond) {

        public Engine {
            requirePositive("idleRpm", idleRpm);
            requirePositive("peakTorqueRpm", peakTorqueRpm);
            requirePositive("redlineRpm", redlineRpm);
            if (!(idleRpm < peakTorqueRpm && peakTorqueRpm < redlineRpm)) {
                throw new IllegalArgumentException("engine RPM points must be strictly increasing");
            }
            requireNonNegative("idleTorqueNm", idleTorqueNm);
            requireNonNegative("peakTorqueNm", peakTorqueNm);
            requireNonNegative("redlineTorqueNm", redlineTorqueNm);
            requirePositive("rpmResponsePerSecond", rpmResponsePerSecond);
        }
    }

    public record Transmission(
            double[] forwardGearRatios,
            double reverseGearRatio,
            double finalDriveRatio,
            double efficiency,
            double automaticDownshiftRpm,
            double automaticUpshiftRpm) {

        public Transmission {
            Objects.requireNonNull(forwardGearRatios, "forwardGearRatios");
            if (forwardGearRatios.length == 0) {
                throw new IllegalArgumentException("at least one forward gear is required");
            }
            forwardGearRatios = forwardGearRatios.clone();
            double previous = Double.POSITIVE_INFINITY;
            for (double ratio : forwardGearRatios) {
                requirePositive("forward gear ratio", ratio);
                if (ratio >= previous) {
                    throw new IllegalArgumentException("forward gear ratios must decrease by gear");
                }
                previous = ratio;
            }
            requirePositive("reverseGearRatio", reverseGearRatio);
            requirePositive("finalDriveRatio", finalDriveRatio);
            requireRange("efficiency", efficiency, 0.0, 1.0);
            requirePositive("automaticDownshiftRpm", automaticDownshiftRpm);
            requirePositive("automaticUpshiftRpm", automaticUpshiftRpm);
            if (automaticDownshiftRpm >= automaticUpshiftRpm) {
                throw new IllegalArgumentException("downshift RPM must be lower than upshift RPM");
            }
        }

        @Override
        public double[] forwardGearRatios() {
            return forwardGearRatios.clone();
        }

        public int forwardGearCount() {
            return forwardGearRatios.length;
        }

        public double ratioForGear(int gear) {
            if (gear == -1) {
                return reverseGearRatio;
            }
            if (gear < 1 || gear > forwardGearRatios.length) {
                return 0.0;
            }
            return forwardGearRatios[gear - 1];
        }
    }

    public record Converter(double stallTorqueMultiplier, double maximumSlipRpm) {
        public Converter {
            if (!Double.isFinite(stallTorqueMultiplier) || stallTorqueMultiplier < 1.0) {
                throw new IllegalArgumentException("stallTorqueMultiplier must be finite and >= 1");
            }
            requirePositive("maximumSlipRpm", maximumSlipRpm);
        }
    }

    public record Grip(
            double dryFrictionCoefficient,
            double fullWetMultiplier,
            double fullOffroadMultiplier,
            double wornTireFloor,
            double drivenWeightFraction,
            double slipBuildPerSecond,
            double slipRecoveryPerSecond) {

        public Grip {
            requireNonNegative("dryFrictionCoefficient", dryFrictionCoefficient);
            requireRange("fullWetMultiplier", fullWetMultiplier, 0.0, 1.0);
            requireRange("fullOffroadMultiplier", fullOffroadMultiplier, 0.0, 1.0);
            requireRange("wornTireFloor", wornTireFloor, 0.0, 1.0);
            requireNonNegative("drivenWeightFraction", drivenWeightFraction);
            requirePositive("slipBuildPerSecond", slipBuildPerSecond);
            requirePositive("slipRecoveryPerSecond", slipRecoveryPerSecond);
        }
    }

    public record Brakes(
            double serviceBrakeForceN,
            double parkingBrakeForceN,
            double serviceRearBias,
            double rearWeightFraction) {

        public Brakes {
            requireNonNegative("serviceBrakeForceN", serviceBrakeForceN);
            requireNonNegative("parkingBrakeForceN", parkingBrakeForceN);
            requireRange("serviceRearBias", serviceRearBias, 0.0, 1.0);
            requireRange("rearWeightFraction", rearWeightFraction, 0.0, 1.0);
        }
    }

    public record Resistance(double aerodynamicDragAreaM2, double rollingResistanceCoefficient) {
        public Resistance {
            requireNonNegative("aerodynamicDragAreaM2", aerodynamicDragAreaM2);
            requireNonNegative("rollingResistanceCoefficient", rollingResistanceCoefficient);
        }
    }

    public record Steering(
            double maximumAngleRadians,
            double fadeSpeedMps,
            double minimumHighSpeedFraction) {

        public Steering {
            requirePositive("maximumAngleRadians", maximumAngleRadians);
            if (maximumAngleRadians > Math.PI / 2.0) {
                throw new IllegalArgumentException("maximumAngleRadians must be <= pi/2");
            }
            requirePositive("fadeSpeedMps", fadeSpeedMps);
            requireRange("minimumHighSpeedFraction", minimumHighSpeedFraction, 0.0, 1.0);
        }
    }

    public record TimeStep(double minimumSeconds, double maximumSeconds, double fallbackSeconds) {
        public TimeStep {
            requirePositive("minimumSeconds", minimumSeconds);
            requirePositive("maximumSeconds", maximumSeconds);
            requirePositive("fallbackSeconds", fallbackSeconds);
            if (minimumSeconds > fallbackSeconds || fallbackSeconds > maximumSeconds) {
                throw new IllegalArgumentException("time step must satisfy minimum <= fallback <= maximum");
            }
        }
    }

    private static void requirePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and > 0");
        }
    }

    private static void requireNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and >= 0");
        }
    }

    private static void requireRange(String name, double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be finite and in [" + minimum + ", " + maximum + "]");
        }
    }
}
