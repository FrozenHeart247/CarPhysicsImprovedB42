package zombie.roadcraft.physics;

/**
 * Balance helpers for the in-game adapter.
 *
 * <p>The methods are deliberately independent of Project Zomboid classes so
 * their behavior can be tested without starting the game. Values describe the
 * target driving character of the reference behavior; no reference bytecode or
 * game classes are redistributed.</p>
 */
public final class RoadcraftCalibration {
    private static final double GRAVITY_MPS2 = 9.80665;
    private static final double SPEED_TO_RPM_FACTOR = 0.95;
    private static final double DIRECTION_CHANGE_SPEED_MPS = 0.65;
    private static final double BURNOUT_SPEED_GAP_KPH = 3.0;

    private RoadcraftCalibration() {
    }

    /** Returns the balanced forward ratios for the script's number of gears. */
    public static double[] forwardGearRatios(int rawGearCount) {
        int gearCount = Math.max(1, Math.min(8, rawGearCount));
        return switch (gearCount) {
            case 1 -> new double[] {1.0};
            case 2 -> new double[] {2.60, 1.0};
            case 3 -> new double[] {2.60, 1.60, 1.0};
            case 4 -> new double[] {3.00, 1.80, 1.30, 1.0};
            case 5 -> new double[] {3.20, 2.00, 1.50, 1.15, 0.90};
            default -> geometricRatios(gearCount, 3.20, 0.90);
        };
    }

    /** Reverse follows the launch ratio of the matching balanced gearbox. */
    public static double reverseGearRatio(int gearCount) {
        return forwardGearRatios(gearCount)[0];
    }

    /**
     * Chooses a final drive for the balanced base RPM-per-km/h coupling. The
     * selected gear ratio is deliberately applied later by the drivetrain.
     */
    public static double finalDriveRatio(
            double redlineRpm,
            double maximumSpeedKph,
            double wheelRadiusMeters) {
        double redline = positiveOr(redlineRpm, 4_500.0);
        double maximumSpeed = Math.max(10.0, positiveOr(maximumSpeedKph, 120.0));
        double radius = Math.max(0.05, positiveOr(wheelRadiusMeters, 0.32));
        double wheelRpmPerKph = 60.0 / (3.6 * 2.0 * Math.PI * radius);
        double targetRpmPerKph = SPEED_TO_RPM_FACTOR * redline / maximumSpeed;
        return clamp(targetRpmPerKph / wheelRpmPerKph, 1.0, 6.5);
    }

    /** Dry driven-axle load used by the new tire-force model. */
    public static double drivenWeightFraction() {
        return drivenWeightFraction(1);
    }

    /**
     * Preserves the broad vanilla vehicle classes without hard-coding model
     * names. The value is an effective launch-grip cap: Sport and Standard can
     * exceed it under full power, while Heavy remains engine-limited.
     */
    public static double drivenWeightFraction(int mechanicType) {
        return mechanicType == 3 ? 0.43 : mechanicType == 2 ? 0.50 : 0.40;
    }

    /** Stable label shared by Sandbox option lookup and runtime telemetry. */
    public static String powertrainCategory(int mechanicType) {
        return mechanicType == 3 ? "Sport" : mechanicType == 2 ? "Heavy" : "Standard";
    }

    /**
     * Converts PZ's dimensionless engineForce into the torque scale consumed
     * by the SI drivetrain. The old 0.10 conversion made every vehicle hit the
     * tire cap, which erased mass and engine differences. These class factors
     * keep Heavy below that cap while allowing Standard and Sport launches to
     * exceed it and spin their tires with different intensity.
     */
    public static double peakTorqueNm(
            double engineForce,
            int mechanicType,
            double userMultiplier) {
        double sourceForce = clamp(positiveOr(engineForce, 4_000.0), 1_000.0, 12_000.0);
        double classFactor = mechanicType == 3 ? 0.0185 : mechanicType == 2 ? 0.014 : 0.015;
        double multiplier = clamp(finiteOr(userMultiplier, 1.0), 0.0, 5.0);
        return clamp(sourceForce * classFactor, 35.0, 320.0) * multiplier;
    }

    /** Peak-torque location as a fraction of the configured redline. */
    public static double peakTorqueRpmFraction(int mechanicType) {
        return mechanicType == 3 ? 0.58 : mechanicType == 2 ? 0.40 : 0.48;
    }

    /** Low-speed torque character: commercial engines are deliberately fuller. */
    public static double idleTorqueFraction(int mechanicType) {
        return mechanicType == 3 ? 0.34 : mechanicType == 2 ? 0.62 : 0.42;
    }

    /** Torque must fall toward redline instead of producing unlimited power. */
    public static double redlineTorqueFraction(int mechanicType) {
        return mechanicType == 3 ? 0.62 : mechanicType == 2 ? 0.42 : 0.50;
    }

    /**
     * Converts the shared Sandbox converter limit into a class profile. The
     * setting remains relative, but commercial automatics do not receive the
     * same launch multiplication as passenger cars.
     */
    public static double torqueConverterMultiplier(double configuredLimit, int mechanicType) {
        double configured = clamp(finiteOr(configuredLimit, 2.5), 1.0, 4.0);
        double classScale = mechanicType == 2 ? 0.45 : 1.0;
        return 1.0 + (configured - 1.0) * classScale;
    }

    /** Portion of explicit Roadcraft road load retained while coasting on asphalt. */
    public static double automaticCoastDragScale(int mechanicType) {
        return mechanicType == 3 ? 0.25 : mechanicType == 2 ? 0.12 : 0.18;
    }

    /**
     * Small acceleration used to cancel Bullet's built-in drivetrain damping.
     * It is only applied by the runtime while an automatic car is freely
     * coasting, never under throttle, braking, or offroad.
     */
    public static double automaticCoastAssistMps2(int mechanicType) {
        return mechanicType == 3 ? 0.12 : mechanicType == 2 ? 0.22 : 0.16;
    }

    /**
     * Learns the force needed to cancel PZ's vehicle-specific automatic
     * drivetrain damping. This is an integral correction: it adapts to a van,
     * a passenger car, and a sport car without giving the one naturally
     * free-rolling vehicle a permanent forward push.
     */
    public static double automaticCoastAssistStep(
            double currentAssistMps2,
            double observedDecelerationMps2,
            double targetDecelerationMps2,
            int mechanicType,
            double deltaSeconds) {
        double maximumAssist = mechanicType == 3 ? 1.60 : mechanicType == 2 ? 3.00 : 2.20;
        double current = clamp(finiteOr(currentAssistMps2, 0.0), 0.0, maximumAssist);
        double observed = clamp(finiteOr(observedDecelerationMps2, 0.0), -5.0, 8.0);
        double target = clamp(finiteOr(targetDecelerationMps2, 0.0), 0.0, 2.0);
        double delta = clamp(finiteOr(deltaSeconds, 1.0 / 60.0), 1.0 / 240.0, 0.10);
        return clamp(current + (observed - target) * 3.0 * delta, 0.0, maximumAssist);
    }

    /** Fades coast assistance to zero only immediately before a full stop. */
    public static double automaticCoastSpeedBlend(double speedMps) {
        double speed = Math.abs(finiteOr(speedMps, 0.0));
        return smoothStep(clamp((speed - 0.35) / 1.65, 0.0, 1.0));
    }

    /** Class-specific steering-command limit layered on the VehicleScript clamp. */
    public static double steeringClampMultiplier(
            int mechanicType,
            double speedKph,
            double maximumSpeedKph) {
        double blend = clamp(
                Math.abs(finiteOr(speedKph, 0.0))
                        / Math.max(10.0, positiveOr(maximumSpeedKph, 90.0)),
                0.0,
                1.0);
        if (mechanicType == 3) {
            return lerp(1.0, 0.90, blend);
        }
        if (mechanicType == 2) {
            return lerp(0.70, 0.55, blend);
        }
        return 1.0;
    }

    /** Sport stays responsive without amplifying high-speed keyboard taps. */
    public static double steeringResponseMultiplier(
            int mechanicType,
            double speedKph,
            double maximumSpeedKph) {
        double blend = clamp(
                Math.abs(finiteOr(speedKph, 0.0))
                        / Math.max(10.0, positiveOr(maximumSpeedKph, 90.0)),
                0.0,
                1.0);
        if (mechanicType == 3) {
            return lerp(1.0, 1.50, blend);
        }
        if (mechanicType == 2) {
            return lerp(0.55, 0.45, blend);
        }
        return 1.0;
    }

    /**
     * Applies a class-specific accelerator response. Release remains quick for
     * control safety, but a keyboard press no longer gives full torque on the
     * very first physics frame.
     */
    public static double throttleStep(
            double currentThrottle,
            double targetThrottle,
            int mechanicType,
            double deltaSeconds) {
        double current = clamp(finiteOr(currentThrottle, 0.0), 0.0, 1.0);
        double target = clamp(finiteOr(targetThrottle, 0.0), 0.0, 1.0);
        double delta = clamp(finiteOr(deltaSeconds, 1.0 / 60.0), 1.0 / 240.0, 0.10);
        double risePerSecond = mechanicType == 3 ? 2.0 : mechanicType == 2 ? 0.85 : 1.35;
        double ratePerSecond = target > current ? risePerSecond : 5.0;
        return moveTowards(current, target, ratePerSecond * delta);
    }

    /**
     * Reduces every locally simulated vehicle by one shared factor based on
     * the heaviest loaded vehicle. A shared factor preserves relative masses.
     */
    public static double dynamicMassScale(double referenceMassKg, double heaviestMassKg) {
        double reference = positiveOr(referenceMassKg, 750.0);
        double heaviest = positiveOr(heaviestMassKg, 1.0);
        return clamp(reference / heaviest, 0.08, 1.0);
    }

    /**
     * Combines the terrain and weather loss used by the balanced tire model.
     * Tire wear and the overall traction option are applied separately by the
     * drivetrain model.
     */
    public static double tractionEnvironmentMultiplier(
            double rainIntensity,
            double snowStrength,
            double tirePressure,
            boolean offroad,
            double offroadEfficiency,
            double wetTraction,
            double snowTraction,
            double offroadTraction) {
        double rain = clamp(finiteOr(rainIntensity, 0.0), 0.0, 1.0);
        double snow = clamp(finiteOr(snowStrength, 0.0), 0.0, 5.0);
        double pressure = clamp(finiteOr(tirePressure, 0.0), 0.0, 1.0);
        double efficiency = Math.max(0.05, positiveOr(offroadEfficiency, 1.0));
        double wet = clamp(finiteOr(wetTraction, 1.0), 0.0, 1.0);
        double snowSetting = clamp(finiteOr(snowTraction, 1.0), 0.0, 1.0);
        double offroadSetting = clamp(finiteOr(offroadTraction, 1.0), 0.0, 1.0);

        double snowFactor = 1.0;
        if (snow > 0.50) {
            snowFactor = (1.0 - (1.0 - snowSetting) * snow * 0.20) * efficiency;
        }

        double terrainFactor = 1.0;
        if (offroad) {
            terrainFactor = (1.0 - (1.0 - offroadSetting) * (0.50 + pressure * 0.50))
                    * efficiency;
            if (rain > 0.0) {
                terrainFactor *= wet;
            }
        }
        return clamp(Math.min(1.0, Math.min(snowFactor, terrainFactor)), 0.0, 1.0);
    }

    /** Advances the native steering command with the balanced low/high-speed rates. */
    public static double steeringStep(
            double currentSteering,
            double rawInput,
            double speedKph,
            double steeringClamp,
            double lowSpeedTurnRate,
            double highSpeedTurnRate,
            double lowSpeedCenterRate,
            double highSpeedCenterRate,
            double snapbackMultiplier,
            double highSpeedReferenceKph,
            double deltaSeconds) {
        double limit = clamp(Math.abs(finiteOr(steeringClamp, 0.50)), 0.01, Math.PI / 2.0);
        double current = clamp(finiteOr(currentSteering, 0.0), -limit, limit);
        double input = clamp(finiteOr(rawInput, 0.0), -1.0, 1.0);
        double reference = Math.max(1.0, positiveOr(highSpeedReferenceKph, 75.0));
        double blend = clamp(Math.abs(finiteOr(speedKph, 0.0)) / reference, 0.0, 1.0);
        double turn = lerp(
                Math.max(0.0, finiteOr(lowSpeedTurnRate, 1.0)),
                Math.max(0.0, finiteOr(highSpeedTurnRate, 0.10)),
                blend);
        double center = lerp(
                Math.max(0.0, finiteOr(lowSpeedCenterRate, 1.0)),
                Math.max(0.0, finiteOr(highSpeedCenterRate, 0.10)),
                blend);
        double delta = clamp(finiteOr(deltaSeconds, 1.0 / 60.0), 1.0 / 240.0, 0.10);

        if (Math.abs(input) > 0.10) {
            if (Math.abs(current) > 0.01 && Math.signum(input) == Math.signum(current)) {
                turn *= Math.max(1.0, finiteOr(snapbackMultiplier, 3.0));
            }
            // The options remain relative sensitivity multipliers, but a
            // square-root response keeps small high-speed values perceptible.
            double response = clamp(4.0 * delta * Math.sqrt(turn), 0.0, 1.0);
            current -= (input + current) * response;
        } else {
            current = moveTowards(current, 0.0, center * 4.0 * delta);
        }
        return clamp(current, -limit, limit);
    }

    /**
     * Resolves vehicle-control ownership without relying on the multiplayer-only
     * authorization enum in single-player. In SP both network flags are false,
     * so the local process always owns the vehicle simulation.
     */
    public static boolean hasControlAuthority(
            boolean gameClient,
            boolean gameServer,
            boolean localPhysics) {
        return (!gameClient && !gameServer) || localPhysics;
    }

    /**
     * Maps the actual direction controls to the service brake. Vanilla's
     * no-input controller supplies an artificial brake value of 10 or 15;
     * that value must not enter the custom physics or the vehicle cannot coast.
     */
    public static double serviceBrakeDemand(
            boolean forward,
            boolean backward,
            double speedMps) {
        double speed = clamp(finiteOr(speedMps, 0.0), -250.0, 250.0);
        if (forward && backward) {
            return 1.0;
        }
        if (!forward && !backward) {
            return 0.0;
        }
        if ((backward && speed > DIRECTION_CHANGE_SPEED_MPS)
                || (forward && speed < -DIRECTION_CHANGE_SPEED_MPS)) {
            return 1.0;
        }
        return 0.0;
    }

    /** Advances the signed driven-wheel speed used to model wheelspin. */
    public static double updateTireSpeedKph(
            double currentTireSpeedKph,
            double actualSpeedKph,
            double rawDriveForce,
            double tractionLimit,
            int gear,
            double deltaSeconds) {
        double actual = clamp(finiteOr(actualSpeedKph, 0.0), -900.0, 900.0);
        if (gear == 0) {
            return actual;
        }
        double tire = clamp(finiteOr(currentTireSpeedKph, actual), -900.0, 900.0);
        double limit = Math.max(1.0, finiteOr(tractionLimit, 0.0));
        double excess = Math.max(0.0, Math.abs(finiteOr(rawDriveForce, 0.0)) - limit);
        double delta = clamp(finiteOr(deltaSeconds, 1.0 / 60.0), 1.0 / 240.0, 0.10);
        double wheelspinGap = Math.min(80.0, excess / limit * 20.0);
        double target = gear < 0 ? actual - wheelspinGap : actual + wheelspinGap;
        double rateKphPerSecond = excess > 0.0 ? 60.0 : 20.0;
        tire = moveTowards(tire, target, rateKphPerSecond * delta);
        return clamp(tire, -900.0, 900.0);
    }

    /** Returns signed wheelspin in km/h; reverse burnout is negative. */
    public static double burnoutAmountKph(
            double tireSpeedKph,
            double actualSpeedKph,
            int gear) {
        double tire = clamp(finiteOr(tireSpeedKph, 0.0), -900.0, 900.0);
        double actual = clamp(finiteOr(actualSpeedKph, 0.0), -900.0, 900.0);
        if (gear < 0) {
            return -Math.max(actual - tire - BURNOUT_SPEED_GAP_KPH, 0.0);
        }
        if (gear > 0) {
            return Math.max(tire - actual - BURNOUT_SPEED_GAP_KPH, 0.0);
        }
        return 0.0;
    }

    private static double[] geometricRatios(int count, double first, double top) {
        double[] ratios = new double[count];
        for (int index = 0; index < count; index++) {
            ratios[index] = first * Math.pow(top / first, index / (double) (count - 1));
        }
        return ratios;
    }

    private static double positiveOr(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double lerp(double start, double end, double position) {
        return start + (end - start) * position;
    }

    private static double moveTowards(double current, double target, double maximumDelta) {
        if (current < target) {
            return Math.min(current + maximumDelta, target);
        }
        return Math.max(current - maximumDelta, target);
    }

    private static double smoothStep(double value) {
        double clamped = clamp(value, 0.0, 1.0);
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

}
