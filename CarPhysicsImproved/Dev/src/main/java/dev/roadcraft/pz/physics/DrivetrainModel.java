package zombie.roadcraft.physics;

import java.util.Objects;

/**
 * Stateless, deterministic drivetrain and longitudinal-force model.
 *
 * <p>The class deliberately operates on snapshots. A future game adapter is
 * responsible for reading game state and applying the returned force command;
 * no game classes or global mutable state belong here.</p>
 */
public final class DrivetrainModel {
    private static final double GRAVITY_MPS2 = 9.80665;
    private static final double AIR_DENSITY_KG_PER_M3 = 1.225;
    private static final double MOVING_EPSILON_MPS = 0.02;
    private static final double DIRECTION_CHANGE_LIMIT_MPS = 0.75;
    private static final double MAX_SANITIZED_SPEED_MPS = 250.0;

    private DrivetrainModel() {
    }

    public static VehicleOutput step(
            PhysicsSettings settings,
            VehicleState previousState,
            VehicleInput rawInput,
            double rawDeltaSeconds) {
        Objects.requireNonNull(settings, "settings");

        VehicleState suppliedState = previousState == null
                ? VehicleState.stopped(settings)
                : previousState;
        VehicleInput suppliedInput = rawInput == null
                ? VehicleInput.idle(true)
                : rawInput;

        double deltaSeconds = sanitizeDelta(settings.timeStep(), rawDeltaSeconds);
        double speedMps = clamp(
                finiteOr(suppliedState.speedMps(), 0.0),
                -MAX_SANITIZED_SPEED_MPS,
                MAX_SANITIZED_SPEED_MPS);
        double engineRpm = clamp(
                finiteOr(suppliedState.engineRpm(), settings.engine().idleRpm()),
                settings.engine().idleRpm(),
                settings.engine().redlineRpm() * 1.25);
        double previousWheelSlip = clamp(finiteOr(suppliedState.wheelSlip(), 0.0), 0.0, 1.0);

        double throttle = unit(finiteOr(suppliedInput.throttle(), 0.0));
        double serviceBrake = unit(finiteOr(suppliedInput.serviceBrake(), 0.0));
        double parkingBrake = unit(finiteOr(suppliedInput.parkingBrake(), 0.0));
        if (serviceBrake > 0.0 || parkingBrake > 0.0) {
            throttle = 0.0;
        }
        double steering = clamp(finiteOr(suppliedInput.steering(), 0.0), -1.0, 1.0);
        double tireCondition = clamp(finiteOr(suppliedInput.tireCondition(), 1.0), 0.0, 2.0);
        double wetness = unit(finiteOr(suppliedInput.wetness(), 0.0));
        double offroadFraction = unit(finiteOr(suppliedInput.offroadFraction(), 0.0));

        int previousGear = sanitizeExistingGear(settings, suppliedState.gear());
        int gear = selectGear(
                settings,
                speedMps,
                previousGear,
                suppliedInput.automaticTransmission(),
                suppliedInput.requestedGear());
        boolean shifted = gear != previousGear;

        double coupledRpm = coupledEngineRpm(settings, speedMps, gear);
        double nextEngineRpm = calculateEngineRpm(
                settings,
                engineRpm,
                coupledRpm,
                gear,
                suppliedInput.automaticTransmission(),
                throttle,
                deltaSeconds);

        double converterSlip = 0.0;
        double converterMultiplier = 1.0;
        if (suppliedInput.automaticTransmission() && gear != 0) {
            converterSlip = unit((nextEngineRpm - coupledRpm)
                    / Math.max(nextEngineRpm, settings.engine().idleRpm()));
            converterMultiplier = 1.0
                    + (settings.converter().stallTorqueMultiplier() - 1.0)
                    * smoothStep(converterSlip);
        }

        double engineTorqueNm = torqueAtRpm(settings, nextEngineRpm) * throttle;
        double rawDriveForceN = calculateRawDriveForce(
                settings,
                gear,
                engineTorqueNm,
                converterMultiplier);
        if (!suppliedInput.automaticTransmission() && gear != 0) {
            rawDriveForceN *= manualClutchCoupling(settings, coupledRpm, gear);
        }

        boolean reverseLimited = false;
        if (gear < 0 && rawDriveForceN < 0.0) {
            double remainingReverseSpeed = settings.reverseSpeedCapMps() - Math.max(0.0, -speedMps);
            double limitingBandMps = Math.max(0.75, settings.reverseSpeedCapMps() * 0.12);
            double limiter = unit(remainingReverseSpeed / limitingBandMps);
            reverseLimited = limiter < 0.999999;
            rawDriveForceN *= limiter;
        }

        double tireMultiplier = lerp(settings.grip().wornTireFloor(), 1.0, tireCondition);
        double weatherMultiplier = lerp(1.0, settings.grip().fullWetMultiplier(), wetness);
        double terrainMultiplier = lerp(1.0, settings.grip().fullOffroadMultiplier(), offroadFraction);
        double effectiveGripCoefficient = settings.grip().dryFrictionCoefficient()
                * tireMultiplier
                * weatherMultiplier
                * terrainMultiplier;

        double tractionLimitN = effectiveGripCoefficient
                * settings.massKg()
                * GRAVITY_MPS2
                * settings.grip().drivenWeightFraction();
        double allWheelTractionLimitN = effectiveGripCoefficient * settings.massKg() * GRAVITY_MPS2;

        double serviceBrakeDemandN = serviceBrake * settings.brakes().serviceBrakeForceN();
        double parkingBrakeDemandN = parkingBrake * settings.brakes().parkingBrakeForceN();
        double rearBrakeDemandN = serviceBrakeDemandN * settings.brakes().serviceRearBias()
                + parkingBrakeDemandN;
        double rearTractionLimitN = effectiveGripCoefficient
                * settings.massKg()
                * GRAVITY_MPS2
                * settings.brakes().rearWeightFraction();
        boolean rearWheelsLocked = rearBrakeDemandN > rearTractionLimitN;

        double excessDriveForceN = Math.max(0.0, Math.abs(rawDriveForceN) - tractionLimitN);
        boolean burnout = gear != 0
                && throttle >= 0.65
                && excessDriveForceN > tractionLimitN * 0.08;
        double slipTarget = burnout
                ? unit(excessDriveForceN / Math.max(tractionLimitN, 1.0))
                : 0.0;
        if (rearWheelsLocked && Math.abs(speedMps) > 0.15) {
            slipTarget = Math.max(slipTarget, 0.60 + 0.35 * parkingBrake);
        }
        double slipRate = slipTarget > previousWheelSlip
                ? settings.grip().slipBuildPerSecond()
                : settings.grip().slipRecoveryPerSecond();
        double nextWheelSlip = moveTowards(previousWheelSlip, slipTarget, slipRate * deltaSeconds);

        double slipEfficiency = 1.0 - 0.18 * nextWheelSlip;
        // Converter multiplication describes torque inside the automatic
        // transmission, not an equal multiplication of road acceleration.
        // While the converter is slipping, part of that energy stays in the
        // rotating driveline (and can exceed the tire cap) instead of pushing
        // the whole vehicle forward. Cancelling the multiplication only on the
        // delivered force keeps the raw force available to the burnout model
        // and makes the locked converter converge smoothly back to 1:1.
        double driveDeliveryFactor = suppliedInput.automaticTransmission()
                ? 1.0 / Math.max(1.0, converterMultiplier)
                : 1.0;
        double appliedDriveForceN = copySignOrZero(
                Math.min(Math.abs(rawDriveForceN), tractionLimitN)
                        * slipEfficiency
                        * driveDeliveryFactor,
                rawDriveForceN);

        double totalBrakeDemandN = serviceBrakeDemandN + parkingBrakeDemandN;
        double brakeScale = totalBrakeDemandN <= 0.0
                ? 0.0
                : Math.min(1.0, allWheelTractionLimitN / totalBrakeDemandN);
        double appliedServiceBrakeN = serviceBrakeDemandN * brakeScale;
        double appliedParkingBrakeN = parkingBrakeDemandN * brakeScale;

        double aerodynamicDragForceN = 0.5
                * AIR_DENSITY_KG_PER_M3
                * settings.resistance().aerodynamicDragAreaM2()
                * speedMps
                * speedMps;
        double rollingDragForceN = (Math.abs(speedMps) > MOVING_EPSILON_MPS
                        || Math.abs(appliedDriveForceN) > 0.0)
                ? settings.resistance().rollingResistanceCoefficient()
                    * settings.massKg()
                    * GRAVITY_MPS2
                : 0.0;

        double engineBrakingForceN = 0.0;
        if (gear != 0 && throttle < 0.02 && Math.abs(speedMps) > MOVING_EPSILON_MPS) {
            double engineSpeed = unit((nextEngineRpm - settings.engine().idleRpm())
                    / (settings.engine().redlineRpm() - settings.engine().idleRpm()));
            double engineBrakingTorqueNm = settings.engine().peakTorqueNm()
                    * 0.024
                    * smoothStep(engineSpeed);
            if (suppliedInput.automaticTransmission()) {
                // A released automatic converter largely decouples the engine
                // while coasting; manual mode remains mechanically coupled.
                engineBrakingTorqueNm *= 0.20;
            }
            engineBrakingForceN = Math.abs(calculateRawDriveForce(
                    settings,
                    gear,
                    engineBrakingTorqueNm,
                    1.0));
            engineBrakingForceN = Math.min(
                    engineBrakingForceN,
                    settings.massKg() * GRAVITY_MPS2 * 0.70);
        }

        double resistanceDirection = Math.abs(speedMps) > MOVING_EPSILON_MPS
                ? Math.signum(speedMps)
                : Math.signum(appliedDriveForceN);
        double opposingForceN = aerodynamicDragForceN
                + rollingDragForceN
                + engineBrakingForceN
                + appliedServiceBrakeN
                + appliedParkingBrakeN;
        double netLongitudinalForceN = appliedDriveForceN - resistanceDirection * opposingForceN;

        if (Math.abs(speedMps) <= MOVING_EPSILON_MPS
                && Math.signum(netLongitudinalForceN) != Math.signum(appliedDriveForceN)) {
            netLongitudinalForceN = 0.0;
        }

        double nextSpeedMps = speedMps + netLongitudinalForceN / settings.massKg() * deltaSeconds;
        if (Math.abs(speedMps) > MOVING_EPSILON_MPS
                && Math.signum(nextSpeedMps) != Math.signum(speedMps)) {
            boolean poweredThroughZero = Math.abs(appliedDriveForceN) > 0.0
                    && Math.signum(appliedDriveForceN) != Math.signum(speedMps);
            if (!poweredThroughZero) {
                nextSpeedMps = 0.0;
            }
        }
        if (gear < 0 && nextSpeedMps < -settings.reverseSpeedCapMps()) {
            nextSpeedMps = -settings.reverseSpeedCapMps();
            reverseLimited = true;
        }
        nextSpeedMps = clamp(finiteOr(nextSpeedMps, 0.0), -MAX_SANITIZED_SPEED_MPS, MAX_SANITIZED_SPEED_MPS);

        double speedRatio = Math.abs(speedMps) / settings.steering().fadeSpeedMps();
        double steeringSpeedFactor = settings.steering().minimumHighSpeedFraction()
                + (1.0 - settings.steering().minimumHighSpeedFraction())
                / (1.0 + speedRatio * speedRatio);
        double steeringSlipFactor = 1.0 - 0.42 * nextWheelSlip;
        double steeringAngleRadians = steering
                * settings.steering().maximumAngleRadians()
                * steeringSpeedFactor
                * steeringSlipFactor;

        VehicleState nextState = new VehicleState(
                nextSpeedMps,
                nextEngineRpm,
                gear,
                nextWheelSlip);
        return new VehicleOutput(
                nextState,
                deltaSeconds,
                engineTorqueNm,
                converterSlip,
                converterMultiplier,
                driveDeliveryFactor,
                rawDriveForceN,
                appliedDriveForceN,
                tractionLimitN,
                effectiveGripCoefficient,
                appliedServiceBrakeN,
                appliedParkingBrakeN,
                aerodynamicDragForceN,
                rollingDragForceN,
                engineBrakingForceN,
                netLongitudinalForceN,
                steeringAngleRadians,
                burnout,
                rearWheelsLocked,
                reverseLimited,
                shifted);
    }

    /** Piecewise smooth torque curve with a soft limiter ending at redline. */
    public static double torqueAtRpm(PhysicsSettings settings, double rawRpm) {
        Objects.requireNonNull(settings, "settings");
        PhysicsSettings.Engine engine = settings.engine();
        double rpm = finiteOr(rawRpm, engine.idleRpm());
        if (rpm <= 0.0 || rpm >= engine.redlineRpm()) {
            return 0.0;
        }
        if (rpm < engine.idleRpm()) {
            return engine.idleTorqueNm() * unit(rpm / engine.idleRpm());
        }

        double torque;
        if (rpm <= engine.peakTorqueRpm()) {
            double position = (rpm - engine.idleRpm())
                    / (engine.peakTorqueRpm() - engine.idleRpm());
            torque = lerp(engine.idleTorqueNm(), engine.peakTorqueNm(), smoothStep(position));
        } else {
            double position = (rpm - engine.peakTorqueRpm())
                    / (engine.redlineRpm() - engine.peakTorqueRpm());
            torque = lerp(engine.peakTorqueNm(), engine.redlineTorqueNm(), smoothStep(position));
        }

        double limiterStartRpm = engine.redlineRpm() * 0.965;
        if (rpm > limiterStartRpm) {
            double limiterPosition = (rpm - limiterStartRpm)
                    / (engine.redlineRpm() - limiterStartRpm);
            torque *= 1.0 - smoothStep(unit(limiterPosition));
        }
        return Math.max(0.0, torque);
    }

    private static int selectGear(
            PhysicsSettings settings,
            double speedMps,
            int currentGear,
            boolean automatic,
            int requestedGear) {
        int maximumGear = settings.transmission().forwardGearCount();
        int request = Math.max(-1, Math.min(maximumGear, requestedGear));

        if (request < 0 && speedMps > DIRECTION_CHANGE_LIMIT_MPS) {
            return 0;
        }
        if (request > 0 && speedMps < -DIRECTION_CHANGE_LIMIT_MPS) {
            return 0;
        }
        if (!automatic || request <= 0) {
            return request;
        }

        int gear = currentGear > 0 ? currentGear : 1;
        gear = Math.max(1, Math.min(maximumGear, gear));
        double coupledRpm = coupledEngineRpm(settings, speedMps, gear);
        // Automatic selection is road-speed based. The persistent engine RPM
        // may still be unwinding after a shift and must not cause a second,
        // unrelated shift on the next controller tick.
        double shiftRpm = coupledRpm;
        if (shiftRpm >= settings.transmission().automaticUpshiftRpm() && gear < maximumGear) {
            return gear + 1;
        }
        if (shiftRpm <= settings.transmission().automaticDownshiftRpm() && gear > 1) {
            double lowerGearRpm = coupledEngineRpm(settings, speedMps, gear - 1);
            if (lowerGearRpm < settings.engine().redlineRpm() * 0.92) {
                return gear - 1;
            }
        }
        return gear;
    }

    private static int sanitizeExistingGear(PhysicsSettings settings, int gear) {
        return Math.max(-1, Math.min(settings.transmission().forwardGearCount(), gear));
    }

    private static double calculateEngineRpm(
            PhysicsSettings settings,
            double engineRpm,
            double coupledRpm,
            int gear,
            boolean automaticTransmission,
            double throttle,
            double deltaSeconds) {
        double desiredRpm;
        if (gear == 0) {
            desiredRpm = settings.engine().idleRpm()
                    + throttle * (settings.engine().redlineRpm() - settings.engine().idleRpm()) * 0.86;
        } else if (automaticTransmission) {
            double slipAllowance = settings.converter().maximumSlipRpm()
                    * throttle;
            double slipFade = 1.0 - unit(coupledRpm / (settings.engine().redlineRpm() * 0.78));
            desiredRpm = Math.max(
                    settings.engine().idleRpm(),
                    coupledRpm + slipAllowance * slipFade);
        } else {
            // Manual mode is rigidly coupled once a gear is selected. The
            // idle clamp stands in for clutch slip only during launch.
            desiredRpm = Math.max(settings.engine().idleRpm(), coupledRpm);
        }
        desiredRpm = clamp(
                desiredRpm,
                settings.engine().idleRpm(),
                settings.engine().redlineRpm() * 1.25);
        return moveTowards(
                engineRpm,
                desiredRpm,
                settings.engine().rpmResponsePerSecond() * deltaSeconds);
    }

    public static double coupledEngineRpm(PhysicsSettings settings, double speedMps, int gear) {
        double gearRatio = settings.transmission().ratioForGear(gear);
        if (gearRatio == 0.0) {
            return 0.0;
        }
        double wheelRpm = Math.abs(speedMps)
                / (2.0 * Math.PI * settings.wheelRadiusMeters())
                * 60.0;
        return wheelRpm * gearRatio * settings.transmission().finalDriveRatio();
    }

    private static double calculateRawDriveForce(
            PhysicsSettings settings,
            int gear,
            double engineTorqueNm,
            double converterMultiplier) {
        double gearRatio = settings.transmission().ratioForGear(gear);
        if (gearRatio == 0.0 || engineTorqueNm <= 0.0) {
            return 0.0;
        }
        double forceMagnitude = engineTorqueNm
                * converterMultiplier
                * gearRatio
                * settings.transmission().finalDriveRatio()
                * settings.transmission().efficiency()
                / settings.wheelRadiusMeters();
        return gear < 0 ? -forceMagnitude : forceMagnitude;
    }

    private static double manualClutchCoupling(
            PhysicsSettings settings,
            double coupledRpm,
            int gear) {
        double firstRatio = settings.transmission().ratioForGear(1);
        double selectedRatio = settings.transmission().ratioForGear(gear);
        double ratioFraction = unit(selectedRatio / Math.max(firstRatio, 1.0e-9));

        // With no clutch pedal exposed by PZ, this is a deterministic launch
        // clutch. First/reverse can transmit useful torque immediately, while
        // tall gears bog until road speed couples the engine to the wheels.
        double launchFloor = unit(0.06 + 0.94 * Math.pow(ratioFraction, 3.0));
        double lockRpm = settings.engine().idleRpm() * 1.10;
        double roadCoupling = smoothStep(unit(coupledRpm / lockRpm));
        return lerp(launchFloor, 1.0, roadCoupling);
    }

    private static double sanitizeDelta(PhysicsSettings.TimeStep settings, double rawDeltaSeconds) {
        double deltaSeconds = finiteOr(rawDeltaSeconds, settings.fallbackSeconds());
        if (deltaSeconds <= 0.0) {
            deltaSeconds = settings.fallbackSeconds();
        }
        return clamp(deltaSeconds, settings.minimumSeconds(), settings.maximumSeconds());
    }

    private static double copySignOrZero(double magnitude, double signSource) {
        return signSource == 0.0 ? 0.0 : Math.copySign(magnitude, signSource);
    }

    private static double moveTowards(double current, double target, double maximumDelta) {
        if (current < target) {
            return Math.min(current + maximumDelta, target);
        }
        return Math.max(current - maximumDelta, target);
    }

    private static double smoothStep(double value) {
        double clamped = unit(value);
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private static double lerp(double start, double end, double position) {
        return start + (end - start) * position;
    }

    private static double unit(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
