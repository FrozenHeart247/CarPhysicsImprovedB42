package dev.carphysicsimproved.v2.physics;

import java.util.List;

/** Deterministic unified drivetrain and two-axle tire model. */
public final class VehicleDynamics {
    private static final double GRAVITY = 9.80665;
    private static final double AIR_DENSITY = 1.225;
    private static final double TWO_PI = Math.PI * 2.0;

    private VehicleDynamics() {
    }

    public static DynamicsOutput step(
            VehicleSpec specification,
            DynamicsState previous,
            DriverInput rawInput,
            VehicleMotion rawMotion,
            VehicleCondition rawCondition,
            double rawDeltaSeconds) {
        if (specification == null) {
            throw new IllegalArgumentException("Vehicle specification is required");
        }
        DynamicsState oldState = previous == null ? DynamicsState.stopped(specification) : previous;
        DriverInput input = sanitizeInput(rawInput);
        VehicleMotion motion = sanitizeMotion(rawMotion);
        VehicleCondition condition = rawCondition == null
                ? VehicleCondition.healthy(specification)
                : rawCondition;
        double dt = VehicleSpec.clamp(VehicleSpec.finite(rawDeltaSeconds, 1.0 / 60.0), 1.0 / 240.0, 0.05);

        int oldGear = normalizeGear(specification, oldState.gear());
        int gear = selectGear(specification, oldGear, input, motion);
        boolean shifted = gear != oldGear;
        double throttleTarget = VehicleSpec.clamp(input.throttle(), 0.0, 1.0);
        double throttleRate = throttleTarget > oldState.filteredThrottle() ? 4.5 : 7.0;
        double filteredThrottle = moveTowards(oldState.filteredThrottle(), throttleTarget, throttleRate * dt);

        double speed = motion.longitudinalSpeedMps();
        double absoluteSpeed = Math.abs(speed);
        boolean sustainedPowerTurn = gear > 0
                && input.throttle() >= 0.92
                && Math.abs(input.steering()) >= 0.35
                && absoluteSpeed >= 3.5;
        double driftIntent = VehicleSpec.clamp(
                VehicleSpec.finite(oldState.driftIntentSeconds(), 0.0), 0.0, 2.50);
        driftIntent = moveTowards(
                driftIntent,
                sustainedPowerTurn ? 2.50 : 0.0,
                (sustainedPowerTurn ? 1.0 : 3.0) * dt);
        double powerDriftActivation = smoothStep(
                VehicleSpec.clamp((driftIntent - 1.50) / 0.50, 0.0, 1.0));
        boolean handbrakeDriftRequest = input.handbrake() >= 0.55
                && Math.abs(input.steering()) >= 0.15
                && absoluteSpeed >= 4.0;
        double ratio = specification.transmission().ratio(gear);
        double firstRatio = specification.transmission().ratio(1);
        double driveDirection = Math.signum(gear);
        double wheelRpm = absoluteSpeed / specification.chassis().wheelRadiusMeters() * 60.0 / TWO_PI;
        double coupledRpm = wheelRpm * ratio * specification.transmission().finalDrive();
        double launchRatio = Math.pow(VehicleSpec.clamp(ratio / firstRatio, 0.08, 1.0), 1.35);
        double launchBlend = smoothStep(VehicleSpec.clamp(absoluteSpeed / 5.0, 0.0, 1.0));
        double launchCoupling = lerp(launchRatio, 1.0, launchBlend);
        double coastCoupling = filteredThrottle < 0.03
                ? lerp(0.28, 0.72, smoothStep(VehicleSpec.clamp(absoluteSpeed / 22.0, 0.0, 1.0)))
                : 1.0;
        double clutchCoupling = launchCoupling * coastCoupling;
        if (shifted) {
            clutchCoupling *= 0.72;
        }

        double freeRevRpm = specification.engine().idleRpm()
                + filteredThrottle * (specification.engine().redlineRpm() - specification.engine().idleRpm()) * 0.38;
        double engineRpm = VehicleSpec.clamp(
                Math.max(specification.engine().idleRpm(), lerp(freeRevRpm, coupledRpm, clutchCoupling)),
                specification.engine().idleRpm(),
                specification.engine().redlineRpm());
        double torqueCurve = torqueCurve(engineRpm / specification.engine().redlineRpm());
        // Engine RPM is clamped for the game UI, but coupled wheel RPM may be
        // above redline in a held manual gear. Cut torque instead of allowing
        // first gear to accelerate forever at displayed redline.
        double overRev = coupledRpm / specification.engine().redlineRpm();
        double revLimiter = 1.0 - smoothStep(VehicleSpec.clamp((overRev - 0.94) / 0.08, 0.0, 1.0));
        double engineHealth = 0.35 + 0.65 * condition.sanitizedEngineCondition();
        double engineTorque = specification.engine().peakTorqueNm() * torqueCurve * filteredThrottle
                * engineHealth * revLimiter;
        double rawDriveForce = engineTorque * ratio * specification.transmission().finalDrive()
                * specification.transmission().efficiency() / specification.chassis().wheelRadiusMeters()
                * clutchCoupling * driveDirection;
        // A fully engaged parking brake disconnects propulsion. Previously it
        // was merely subtracted from engine force, so a sufficiently strong
        // second gear could overpower it and drive away normally.
        double handbrakeCoupling = 1.0 - input.handbrake() * input.handbrake();
        rawDriveForce *= handbrakeCoupling;

        AxleCondition frontCondition = axleCondition(specification, condition, true);
        AxleCondition rearCondition = axleCondition(specification, condition, false);
        double rollingMultiplier = weightedMean(frontCondition.rollingResistance(), rearCondition.rollingResistance());
        double effectiveMass = specification.massKg() + condition.sanitizedPayloadKg();
        double normalTotal = effectiveMass * GRAVITY;
        double weightTransfer = effectiveMass * oldState.previousLongitudinalAccelerationMps2()
                * specification.chassis().centerOfMassHeightMeters()
                / specification.chassis().wheelbaseMeters();
        double frontNormal = VehicleSpec.clamp(
                normalTotal * specification.chassis().frontStaticWeightShare() - weightTransfer,
                normalTotal * 0.18,
                normalTotal * 0.82);
        double rearNormal = normalTotal - frontNormal;
        double suspensionFactor = 0.62 + 0.38 * condition.sanitizedSuspensionCondition();
        double surfaceGrip = VehicleSpec.clamp(input.surfaceGripMultiplier(), 0.12, 1.6);
        double frontGrip = axleGrip(specification, frontNormal, normalTotal, frontCondition.grip(), surfaceGrip, suspensionFactor);
        double rearGrip = axleGrip(specification, rearNormal, normalTotal, rearCondition.grip(), surfaceGrip, suspensionFactor);

        double steeringDenominator = 1.0
                + absoluteSpeed * specification.steering().speedSensitivityPerMps()
                + absoluteSpeed * absoluteSpeed * 0.0040;
        double steeringTarget = input.steering() * specification.steering().maximumAngleRadians()
                / steeringDenominator;
        double steeringRate = Math.abs(input.steering()) > 0.001
                ? specification.steering().inputRateRadiansPerSecond()
                : specification.steering().returnRateRadiansPerSecond();
        double steeringAngle = moveTowards(oldState.steeringAngleRadians(), steeringTarget, steeringRate * dt);

        double velocityFloor = Math.max(absoluteSpeed, 0.75);
        double frontSlip = absoluteSpeed < 0.20 ? 0.0 : Math.atan2(
                motion.lateralSpeedMps() + motion.yawRateRadiansPerSecond()
                        * specification.chassis().cgToFrontAxleMeters(),
                velocityFloor) - steeringAngle;
        double rearSlip = absoluteSpeed < 0.20 ? 0.0 : Math.atan2(
                motion.lateralSpeedMps() - motion.yawRateRadiansPerSecond()
                        * specification.chassis().cgToRearAxleMeters(),
                velocityFloor);
        double frontCornering = specification.tires().frontCorneringStiffnessNPerRad()
                * frontCondition.cornering() * suspensionFactor;
        double rearCornering = specification.tires().rearCorneringStiffnessNPerRad()
                * rearCondition.cornering() * suspensionFactor;
        double desiredFrontLateral = -frontCornering * frontSlip;
        double desiredRearLateral = -rearCornering * rearSlip;
        // A linear bicycle model is invalid near standstill. Fade its force in
        // with wheel speed so steering input cannot rotate a parked vehicle.
        double lateralSpeedBlend = smoothStep(VehicleSpec.clamp((absoluteSpeed - 0.20) / 3.0, 0.0, 1.0));
        desiredFrontLateral *= lateralSpeedBlend;
        desiredRearLateral *= lateralSpeedBlend;

        double resistanceMagnitude = absoluteSpeed < 0.08 ? 0.0
                : specification.tires().rollingResistanceCoefficient() * rollingMultiplier * normalTotal
                + 0.5 * AIR_DENSITY * 0.72 * absoluteSpeed * absoluteSpeed;
        double engineBrake = filteredThrottle < 0.02 && absoluteSpeed > 0.15
                ? specification.engine().peakTorqueNm() * specification.engine().engineBrakeFraction()
                        * ratio * specification.transmission().finalDrive()
                        / specification.chassis().wheelRadiusMeters() * clutchCoupling
                : 0.0;
        resistanceMagnitude += Math.min(engineBrake, effectiveMass * 0.55);

        double brakeHealth = 0.28 + 0.72 * condition.sanitizedBrakesCondition();
        double serviceBrake = input.serviceBrake() * normalTotal * 1.05 * brakeHealth;
        double handbrake = input.handbrake() * rearGrip * 1.15 * brakeHealth;
        // At rest a brake has no longitudinal direction. Treating standstill
        // as forward made the brake itself apply a reverse force.
        double direction = absoluteSpeed > 0.08 ? Math.signum(speed) : 0.0;
        double performanceIndex = specification.engine().peakTorqueNm() / specification.massKg()
                * Math.sqrt(VehicleSpec.clamp(specification.maximumSpeedKph() / 120.0, 0.45, 1.80));
        // Low-speed utility scripts need more launch authority to overcome the
        // native chassis losses seen in game. This assist is continuous and
        // maxSpeed-based: it does not identify Van/Sport by script name and it
        // fades out before the sports-car range.
        double baseAccelerationLimit = 2.2 + performanceIndex * 4.67;
        double utilityAssist = 1.0 + 0.38 * smoothStep(VehicleSpec.clamp(
                (95.0 - specification.maximumSpeedKph()) / 40.0, 0.0, 1.0));
        double propulsionAccelerationLimit = VehicleSpec.clamp(
                baseAccelerationLimit * utilityAssist, 2.35, 5.20);
        // The cap uses unladen script mass, so payload still lowers actual
        // acceleration instead of increasing the allowed propulsion force.
        double propulsionForceLimit = specification.massKg() * propulsionAccelerationLimit;
        double bodyDriveDemand = VehicleSpec.clamp(rawDriveForce, -propulsionForceLimit, propulsionForceLimit);
        // Full wheel-torque demand is retained near launch for burnout and
        // power-oversteer, then blended toward the body-force envelope. This
        // prevents high-speed cornering from saturating both axles on every
        // throttle press.
        double wheelspinPotential = VehicleSpec.clamp(
                (performanceIndex - 0.215) / 0.14, 0.0, 1.0);
        int absoluteGear = Math.abs(gear);
        double gearSlipAuthority = absoluteGear <= 1 ? 1.0
                : absoluteGear == 2 ? 0.12 + 0.18 * wheelspinPotential
                : absoluteGear == 3 ? 0.02 + 0.05 * wheelspinPotential
                : 0.01;
        // Second-gear wheelspin remains possible for powerful cars, damaged
        // tires and low-grip surfaces. On dry asphalt the rapidly falling
        // wheel torque should not report continuous burnout in third gear.
        double lowGripSlipAllowance = VehicleSpec.clamp((0.90 - surfaceGrip) / 0.65, 0.0, 1.0);
        gearSlipAuthority = VehicleSpec.clamp(
                gearSlipAuthority + lowGripSlipAllowance * 0.40, 0.0, 1.0);
        double launchSlipBlend = wheelspinPotential * (1.0 - smoothStep(
                VehicleSpec.clamp((absoluteSpeed - 8.0) / 18.0, 0.0, 1.0)))
                * gearSlipAuthority;
        double tireDriveDemand = lerp(bodyDriveDemand, rawDriveForce, launchSlipBlend);

        double frontLongDesired = tireDriveDemand * specification.driveLayout().frontShare()
                - direction * serviceBrake * 0.64;
        double rearLongDesired = tireDriveDemand * specification.driveLayout().rearShare()
                - direction * (serviceBrake * 0.36 + handbrake);

        AxleForces frontForces = saturateAxle(frontLongDesired, desiredFrontLateral, frontGrip);
        AxleForces rearForces = saturateAxle(rearLongDesired, desiredRearLateral, rearGrip);
        double resistanceForce = -Math.signum(speed) * resistanceMagnitude;
        double tireAppliedDrive = driveContribution(frontForces.longitudinal(), frontLongDesired, tireDriveDemand)
                + driveContribution(rearForces.longitudinal(), rearLongDesired, tireDriveDemand);
        double appliedDrive = VehicleSpec.clamp(
                tireAppliedDrive, -propulsionForceLimit, propulsionForceLimit);
        double gradeForce = -effectiveMass * GRAVITY * Math.sin(motion.roadGradeRadians());
        double longitudinal = frontForces.longitudinal() + rearForces.longitudinal()
                + (appliedDrive - tireAppliedDrive) + resistanceForce + gradeForce;
        double staticRollingLimit = specification.tires().rollingResistanceCoefficient()
                * rollingMultiplier * normalTotal;
        if (absoluteSpeed < 0.08 && filteredThrottle < 0.02
                && Math.abs(gradeForce) <= staticRollingLimit) {
            longitudinal = 0.0;
        }
        double rawLateral = frontForces.lateral() + rearForces.lateral();

        double longitudinalImbalance = frontForces.longitudinal() * frontCondition.leftRightImbalance()
                + rearForces.longitudinal() * rearCondition.leftRightImbalance();
        double imbalanceYaw = longitudinalImbalance * specification.chassis().trackMeters() * 0.5;
        double rollingPullYaw = -direction * resistanceMagnitude
                * (frontCondition.leftRightRollingImbalance() + rearCondition.leftRightRollingImbalance())
                * specification.chassis().trackMeters() * 0.25;
        double rawYawTorque = frontForces.lateral() * specification.chassis().cgToFrontAxleMeters()
                - rearForces.lateral() * specification.chassis().cgToRearAxleMeters()
                + imbalanceYaw + rollingPullYaw;

        double drivenGrip = frontGrip * specification.driveLayout().frontShare()
                + rearGrip * specification.driveLayout().rearShare();
        double wheelspinExcess = Math.max(0.0, Math.abs(tireDriveDemand) - drivenGrip);
        boolean tractionExceeded = wheelspinExcess > Math.max(250.0, drivenGrip * 0.03);
        double slipSpeed = drivenGrip <= 1.0 ? 0.0 : wheelspinExcess / drivenGrip * (2.0 + filteredThrottle * 8.0);
        double drivenWheelTarget = (driveDirection == 0.0 ? Math.signum(speed) : driveDirection)
                * (absoluteSpeed + slipSpeed);
        double drivenWheelSpeed = moveTowards(
                oldState.drivenWheelSpeedMps(),
                drivenWheelTarget,
                (tractionExceeded ? 22.0 : 12.0) * dt);
        double wheelSlip = Math.max(0.0, Math.abs(drivenWheelSpeed) - absoluteSpeed);
        boolean burnout = filteredThrottle > 0.58
                && tractionExceeded
                && wheelSlip > Math.max(0.65, absoluteSpeed * 0.06);
        double acceleration = longitudinal / effectiveMass;
        double frontSaturation = frontForces.saturation();
        double rearSaturation = rearForces.saturation();

        // Rear-wheel power oversteer stays entirely parameter-driven. A light,
        // powerful RWD car gains more yaw when its rear axle is overloaded;
        // a heavy or low-powered vehicle naturally receives less assistance.
        double rearDriveLoad = Math.abs(tireDriveDemand) * specification.driveLayout().rearShare()
                / Math.max(1.0, rearGrip);
        double rearOverload = VehicleSpec.clamp((rearDriveLoad - 0.88) / 1.35, 0.0, 1.0);
        double powerToWeight = specification.engine().peakTorqueNm() / effectiveMass;
        double powerFactor = VehicleSpec.clamp((powerToWeight - 0.10) / 0.32, 0.0, 1.0);
        double oversteerSpeedBlend = smoothStep(VehicleSpec.clamp((absoluteSpeed - 3.0) / 6.0, 0.0, 1.0))
                * (1.0 - 0.96 * smoothStep(
                        VehicleSpec.clamp((absoluteSpeed - 12.0) / 12.0, 0.0, 1.0)));
        double turnDirection = Math.signum(steeringAngle);
        double powerOversteerYaw = turnDirection * rearOverload * filteredThrottle * oversteerSpeedBlend
                * powerDriftActivation
                * (0.35 + 0.65 * powerFactor)
                * normalTotal * specification.chassis().wheelbaseMeters() * 0.032;
        rawYawTorque += powerOversteerYaw;

        // At road speeds, damp excess yaw toward a bounded kinematic turn
        // rate. The driver can still corner, but one full keyboard tap cannot
        // request an instant high-g rotation and turn every bend into a drift.
        double stabilityBlend = smoothStep(
                VehicleSpec.clamp((absoluteSpeed - 12.0) / 18.0, 0.0, 1.0));
        double kinematicYawRate = speed / specification.chassis().wheelbaseMeters()
                * Math.tan(steeringAngle);
        double stableYawLimit = 4.2 / Math.max(absoluteSpeed, 4.0);
        double stableYawTarget = VehicleSpec.clamp(
                kinematicYawRate, -stableYawLimit, stableYawLimit);
        double yawInertiaScale = effectiveMass
                * specification.chassis().wheelbaseMeters()
                * specification.chassis().wheelbaseMeters();
        double intentionalDriftBlend = Math.max(
                powerDriftActivation,
                handbrakeDriftRequest ? 1.0 : 0.0);
        double stabilityStrength = lerp(0.42, 0.18, intentionalDriftBlend);
        rawYawTorque += (stableYawTarget - motion.yawRateRadiansPerSecond())
                * yawInertiaScale * stabilityStrength * stabilityBlend;
        // Real tires need time to build lateral force. Without relaxation, a
        // single keyboard tap could jump straight to the friction-circle limit
        // and inject a full yaw impulse before drift intent was even armed.
        double lateralResponsePerSecond = Math.abs(input.steering()) > 0.02 ? 1.80 : 4.20;
        double yawResponsePerSecond = Math.abs(input.steering()) > 0.02 ? 1.45 : 3.80;
        double lateral = moveTowards(
                VehicleSpec.finite(oldState.previousLateralForceN(), 0.0),
                rawLateral,
                normalTotal * lateralResponsePerSecond * dt);
        double yawTorque = moveTowards(
                VehicleSpec.finite(oldState.previousYawTorqueNm(), 0.0),
                rawYawTorque,
                normalTotal * specification.chassis().wheelbaseMeters()
                        * yawResponsePerSecond * dt);
        if (absoluteSpeed < 0.35) {
            lateral = 0.0;
            yawTorque = 0.0;
        }
        boolean rearSlideEvidence = rearSaturation > 1.0
                && (Math.abs(rearSlip) > 0.075 || Math.abs(motion.lateralSpeedMps()) > 1.2);
        boolean drifting = absoluteSpeed > 3.0
                && rearSlideEvidence
                && ((sustainedPowerTurn && powerDriftActivation > 0.05)
                        || handbrakeDriftRequest);
        boolean understeering = absoluteSpeed > 4.0
                && frontSaturation > 1.0
                && frontSaturation > rearSaturation * 1.08;

        DynamicsState state = new DynamicsState(
                gear,
                engineRpm,
                filteredThrottle,
                steeringAngle,
                drivenWheelSpeed,
                VehicleSpec.clamp(acceleration, -20.0, 20.0),
                driftIntent,
                lateral,
                yawTorque);
        return new DynamicsOutput(
                state,
                dt,
                clutchCoupling,
                engineTorque,
                rawDriveForce,
                propulsionForceLimit,
                appliedDrive,
                resistanceForce,
                serviceBrake + handbrake,
                longitudinal,
                lateral,
                yawTorque,
                frontSlip,
                rearSlip,
                frontGrip,
                rearGrip,
                frontCondition.grip(),
                rearCondition.grip(),
                rollingMultiplier,
                frontSaturation,
                rearSaturation,
                wheelSlip,
                burnout,
                drifting,
                understeering,
                shifted);
    }

    public static DynamicsOutput step(
            VehicleSpec specification,
            DynamicsState previous,
            DriverInput input,
            VehicleMotion motion,
            double deltaSeconds) {
        return step(specification, previous, input, motion, VehicleCondition.healthy(specification), deltaSeconds);
    }

    private static DriverInput sanitizeInput(DriverInput input) {
        if (input == null) {
            return DriverInput.idle(TransmissionMode.AUTOMATIC, 1);
        }
        return new DriverInput(
                VehicleSpec.clamp(VehicleSpec.finite(input.throttle(), 0.0), 0.0, 1.0),
                VehicleSpec.clamp(VehicleSpec.finite(input.serviceBrake(), 0.0), 0.0, 1.0),
                VehicleSpec.clamp(VehicleSpec.finite(input.handbrake(), 0.0), 0.0, 1.0),
                VehicleSpec.clamp(VehicleSpec.finite(input.steering(), 0.0), -1.0, 1.0),
                input.transmissionMode() == null ? TransmissionMode.AUTOMATIC : input.transmissionMode(),
                input.requestedGear(),
                VehicleSpec.clamp(VehicleSpec.finite(input.surfaceGripMultiplier(), 1.0), 0.12, 1.6));
    }

    private static VehicleMotion sanitizeMotion(VehicleMotion motion) {
        if (motion == null) {
            return VehicleMotion.stopped();
        }
        return new VehicleMotion(
                VehicleSpec.clamp(VehicleSpec.finite(motion.longitudinalSpeedMps(), 0.0), -120.0, 120.0),
                VehicleSpec.clamp(VehicleSpec.finite(motion.lateralSpeedMps(), 0.0), -80.0, 80.0),
                VehicleSpec.clamp(VehicleSpec.finite(motion.yawRateRadiansPerSecond(), 0.0), -12.0, 12.0),
                VehicleSpec.clamp(VehicleSpec.finite(motion.roadGradeRadians(), 0.0), -0.70, 0.70));
    }

    private static int selectGear(VehicleSpec spec, int current, DriverInput input, VehicleMotion motion) {
        if (input.transmissionMode() == TransmissionMode.MANUAL) {
            return normalizeGear(spec, input.requestedGear());
        }
        double speed = Math.abs(motion.longitudinalSpeedMps());
        if (input.requestedGear() < 0 && speed < 0.80) {
            return -1;
        }
        if (input.requestedGear() > 0 && current <= 0 && speed < 0.80) {
            return 1;
        }
        if (current < 0) {
            return -1;
        }
        double rpm = speed / spec.chassis().wheelRadiusMeters() * 60.0 / TWO_PI
                * spec.transmission().ratio(current) * spec.transmission().finalDrive();
        double upshiftRpm = spec.engine().redlineRpm() * (0.72 + input.throttle() * 0.18);
        double downshiftRpm = spec.engine().idleRpm() * (1.45 + input.throttle() * 0.70);
        if (rpm > upshiftRpm && current < spec.transmission().gearCount()) {
            return current + 1;
        }
        if (rpm < downshiftRpm && current > 1) {
            return current - 1;
        }
        return current;
    }

    private static AxleCondition axleCondition(VehicleSpec spec, VehicleCondition condition, boolean front) {
        List<ScriptVehicleData.Wheel> axleWheels = spec.wheels().stream()
                .filter(wheel -> wheel.front() == front).toList();
        if (axleWheels.isEmpty()) {
            return new AxleCondition(1.0, 1.0, 1.0, 0.0, 0.0);
        }
        double grip = 0.0;
        double cornering = 0.0;
        double rolling = 0.0;
        double leftGrip = 0.0;
        double rightGrip = 0.0;
        double leftRolling = 0.0;
        double rightRolling = 0.0;
        int leftCount = 0;
        int rightCount = 0;
        for (ScriptVehicleData.Wheel wheel : axleWheels) {
            VehicleCondition.TireCondition tire = condition.tire(wheel.id());
            double tireGrip = tire.gripMultiplier();
            grip += tireGrip;
            cornering += tire.corneringMultiplier();
            rolling += tire.rollingResistanceMultiplier();
            if (wheel.offsetX() < 0.0) {
                leftGrip += tireGrip;
                leftRolling += tire.rollingResistanceMultiplier();
                leftCount++;
            } else {
                rightGrip += tireGrip;
                rightRolling += tire.rollingResistanceMultiplier();
                rightCount++;
            }
        }
        double count = axleWheels.size();
        double left = leftCount == 0 ? grip / count : leftGrip / leftCount;
        double right = rightCount == 0 ? grip / count : rightGrip / rightCount;
        double imbalance = VehicleSpec.clamp((right - left) / Math.max(0.10, left + right), -0.80, 0.80);
        double leftRoll = leftCount == 0 ? rolling / count : leftRolling / leftCount;
        double rightRoll = rightCount == 0 ? rolling / count : rightRolling / rightCount;
        double rollingImbalance = VehicleSpec.clamp(
                (rightRoll - leftRoll) / Math.max(0.10, leftRoll + rightRoll), -0.90, 0.90);
        return new AxleCondition(grip / count, cornering / count, rolling / count, imbalance, rollingImbalance);
    }

    private static double axleGrip(
            VehicleSpec spec,
            double axleNormal,
            double totalNormal,
            double conditionGrip,
            double surfaceGrip,
            double suspensionFactor) {
        double referenceShare = axleNormal / totalNormal;
        double loadFactor = Math.pow(Math.max(0.25, referenceShare / 0.5), -spec.tires().loadSensitivity());
        return axleNormal * spec.tires().dryGripCoefficient() * conditionGrip
                * surfaceGrip * suspensionFactor * loadFactor;
    }

    private static AxleForces saturateAxle(double longitudinal, double lateral, double grip) {
        double requested = Math.hypot(longitudinal, lateral);
        double saturation = requested / Math.max(1.0, grip);
        if (requested <= grip || requested <= 0.0001) {
            return new AxleForces(longitudinal, lateral, saturation);
        }
        double scale = grip / requested;
        return new AxleForces(longitudinal * scale, lateral * scale, saturation);
    }

    private static double driveContribution(double applied, double desired, double rawDrive) {
        if (rawDrive > 0.0 && desired > 0.0) {
            return Math.max(0.0, applied);
        }
        if (rawDrive < 0.0 && desired < 0.0) {
            return Math.min(0.0, applied);
        }
        return 0.0;
    }

    private static double torqueCurve(double normalizedRpm) {
        double rpm = VehicleSpec.clamp(normalizedRpm, 0.0, 1.0);
        if (rpm < 0.18) {
            return lerp(0.58, 0.82, rpm / 0.18);
        }
        if (rpm < 0.55) {
            return lerp(0.82, 1.0, (rpm - 0.18) / 0.37);
        }
        return lerp(1.0, 0.68, (rpm - 0.55) / 0.45);
    }

    private static int normalizeGear(VehicleSpec spec, int gear) {
        if (gear < 0) {
            return -1;
        }
        if (gear == 0) {
            return 0;
        }
        return Math.min(spec.transmission().gearCount(), gear);
    }

    private static double weightedMean(double a, double b) {
        return (a + b) * 0.5;
    }

    private static double moveTowards(double current, double target, double maximumDelta) {
        double delta = target - current;
        if (Math.abs(delta) <= maximumDelta) {
            return target;
        }
        return current + Math.copySign(maximumDelta, delta);
    }

    private static double smoothStep(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double lerp(double a, double b, double amount) {
        return a + (b - a) * amount;
    }

    private record AxleCondition(
            double grip,
            double cornering,
            double rollingResistance,
            double leftRightImbalance,
            double leftRightRollingImbalance) {
    }

    private record AxleForces(double longitudinal, double lateral, double saturation) {
    }
}
