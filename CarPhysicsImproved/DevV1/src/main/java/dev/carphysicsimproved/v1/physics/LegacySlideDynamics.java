package dev.carphysicsimproved.v1.physics;

/**
 * Bounded slide controller layered over the native PZ tire simulation.
 * Normal cornering deliberately produces zero body-force commands; this class
 * only observes the grip budget and intervenes after a plausible slide begins.
 */
public final class LegacySlideDynamics {
    private static final double GRAVITY = 9.80665;

    private LegacySlideDynamics() {
    }

    public static Output step(Spec rawSpec, AxleGrip rawGrip, Tuning rawTuning,
            Input rawInput, State state, double rawDeltaSeconds) {
        Spec spec = rawSpec == null ? Spec.defaults() : rawSpec;
        AxleGrip grip = rawGrip == null ? AxleGrip.defaults() : rawGrip;
        Tuning tuning = rawTuning == null ? Tuning.defaults() : rawTuning;
        Input input = rawInput == null ? Input.idle() : rawInput;
        double dt = clamp(rawDeltaSeconds, 1.0 / 240.0, 0.05);

        if (!tuning.enabled()) {
            state.reset();
            return Output.inactive();
        }

        double speed = finite(input.longitudinalSpeedMps(), 0.0);
        double absoluteSpeed = Math.abs(speed);
        double lateralSpeed = clamp(finite(input.lateralSpeedMps(), 0.0), -60.0, 60.0);
        double yawRate = clamp(finite(input.yawRateRadiansPerSecond(), 0.0), -8.0, 8.0);
        double steering = clamp(finite(input.steeringAngleRadians(), 0.0), -1.2, 1.2);
        double throttle = clamp(finite(input.throttle(), 0.0), 0.0, 1.0);
        double serviceBrake = clamp(finite(input.serviceBrake(), 0.0), 0.0, 1.0);
        double handbrake = clamp(finite(input.handbrake(), 0.0), 0.0, 1.0);
        double clutchKick = tuning.clutchKickEnabled()
                ? clamp(finite(input.clutchKickIntensity(), 0.0), 0.0, 1.0)
                : 0.0;
        double beta = absoluteSpeed < 0.25
                ? 0.0
                : Math.atan2(lateralSpeed, Math.max(1.5, absoluteSpeed));

        double acceleration = 0.0;
        if (state.motionInitialized) {
            acceleration = clamp((speed - state.previousLongitudinalSpeedMps) / dt, -10.0, 10.0);
        }
        state.filteredLongitudinalAccelerationMps2 = approach(
                state.filteredLongitudinalAccelerationMps2,
                acceleration,
                8.0 * dt);

        if ((state.mode == Mode.GRIP || state.mode == Mode.LIMIT)
                && input.gear() > 0 && absoluteSpeed >= 4.0
                && Math.abs(steering) >= 0.06 && Math.abs(yawRate) >= 0.025
                && Math.abs(beta) < Math.toRadians(10.0)) {
            double observedSign = Math.signum(yawRate * steering * Math.signum(speed));
            if (observedSign != 0.0) {
                state.steeringYawCorrelation = approach(
                        state.steeringYawCorrelation, observedSign, 1.6 * dt);
                state.yawCalibrationSeconds = clamp(state.yawCalibrationSeconds + dt, 0.0, 2.0);
            }
        }
        double steeringYawSign = state.yawCalibrationSeconds >= 0.12
                && Math.abs(state.steeringYawCorrelation) >= 0.10
                        ? Math.signum(state.steeringYawCorrelation)
                        : -1.0;
        boolean yawSignCalibrated = state.yawCalibrationSeconds >= 0.12
                && Math.abs(state.steeringYawCorrelation) >= 0.10;

        double kinematicYaw = steeringYawSign * speed / spec.wheelbaseMeters() * Math.tan(steering);
        double minimumGrip = Math.min(grip.frontCoefficient(), grip.rearCoefficient());
        double stableYawLimit = minimumGrip * GRAVITY / Math.max(absoluteSpeed, 4.0);
        double expectedYaw = clamp(kinematicYaw, -stableYawLimit, stableYawLimit);
        double yawError = yawRate - expectedYaw;

        double frontStatic = spec.frontStaticWeightShare();
        double rearStatic = 1.0 - frontStatic;
        double weightTransferShare = state.filteredLongitudinalAccelerationMps2
                * spec.centerOfMassHeightMeters() / (GRAVITY * spec.wheelbaseMeters());
        double frontLoadShare = clamp(frontStatic - weightTransferShare, 0.20, 0.80);
        double rearLoadShare = 1.0 - frontLoadShare;

        double throttleDropRate = state.motionInitialized
                ? Math.max(0.0, (state.previousThrottle - throttle) / dt)
                : 0.0;
        boolean liftOff = absoluteSpeed >= 5.0 && Math.abs(steering) >= 0.07
                && throttleDropRate >= 1.4;
        double liftOffAmount = liftOff ? smoothStep(clamp((throttleDropRate - 1.4) / 3.0, 0.0, 1.0)) : 0.0;
        rearLoadShare = clamp(rearLoadShare * (1.0 - 0.08 * liftOffAmount), 0.18, 0.80);

        double requestedLateralMu = Math.abs(speed * kinematicYaw) / GRAVITY;
        double frontLateralUse = requestedLateralMu * frontStatic
                / Math.max(0.05, grip.frontCoefficient() * frontLoadShare);
        double rearLateralUse = requestedLateralMu * rearStatic
                / Math.max(0.05, grip.rearCoefficient() * rearLoadShare);

        double driveUse = Math.abs(finite(input.rawDriveForce(), 0.0))
                / Math.max(1.0, finite(input.tractionLimit(), 1.0));
        double rearDriveShare = spec.rearDriveShare();
        double frontLongitudinalUse = driveUse * (1.0 - rearDriveShare) + serviceBrake * 0.68;
        double rearLongitudinalUse = driveUse * rearDriveShare + serviceBrake * 0.32
                + handbrake * 1.10 + clutchKick * rearDriveShare * 0.72;
        double frontUse = Math.hypot(frontLateralUse, frontLongitudinalUse);
        double rearUse = Math.hypot(rearLateralUse, rearLongitudinalUse);
        rearUse += liftOffAmount * rearLateralUse * 0.16;

        boolean turning = Math.abs(steering) >= 0.075;
        boolean yawOversteerEvidence = Math.abs(yawRate) >= Math.abs(expectedYaw) + 0.14
                || yawSignCalibrated && expectedYaw != 0.0
                        && Math.signum(yawRate) != Math.signum(expectedYaw)
                        && Math.abs(yawRate) >= 0.14;
        boolean actualLateralEvidence = Math.abs(beta) >= Math.toRadians(2.5)
                || yawOversteerEvidence;
        boolean actualSlideEvidence = actualLateralEvidence
                || turning && input.rearNativeSkid() >= 0.18;
        boolean powerRequest = input.gear() > 0 && input.gear() <= 2
                && absoluteSpeed >= 4.5 && absoluteSpeed <= 22.0
                && throttle >= 0.85 && Math.abs(steering) >= 0.16
                && rearUse >= 0.88;
        state.powerIntentSeconds = approach(
                state.powerIntentSeconds,
                powerRequest ? tuning.powerDriftEntryDelaySeconds() + 0.35 : 0.0,
                (powerRequest ? 1.0 : 3.5) * dt);
        boolean powerReady = powerRequest
                && state.powerIntentSeconds >= tuning.powerDriftEntryDelaySeconds();

        boolean handbrakeRequest = handbrake >= 0.55 && turning
                && absoluteSpeed >= 4.0 && absoluteSpeed <= 28.0;
        state.handbrakeIntentSeconds = approach(
                state.handbrakeIntentSeconds,
                handbrakeRequest ? 0.30 : 0.0,
                (handbrakeRequest ? 1.0 : 4.0) * dt);
        boolean handbrakeReady = handbrakeRequest && state.handbrakeIntentSeconds >= 0.12;
        boolean clutchReady = clutchKick >= 0.20 && turning && absoluteSpeed >= 3.5 && absoluteSpeed <= 24.0;

        boolean naturalRearOverload = rearUse >= 1.0 && rearUse >= frontUse + 0.05;
        boolean severeRearOverload = rearUse >= 1.32 && turning;
        boolean naturalEvidence = naturalRearOverload && actualSlideEvidence;
        state.naturalOverloadSeconds = approach(
                state.naturalOverloadSeconds,
                naturalEvidence ? 0.40 : 0.0,
                (naturalEvidence ? 1.0 : 3.5) * dt);
        boolean naturalReady = state.naturalOverloadSeconds >= (severeRearOverload ? 0.06 : 0.16);
        boolean impactReady = input.impactDisturbance() && absoluteSpeed >= 3.0 && actualSlideEvidence;
        boolean intentionalTrigger = handbrakeReady || clutchReady || powerReady;
        boolean trigger = intentionalTrigger || naturalReady || impactReady;

        Cause triggerCause = Cause.NONE;
        if (handbrakeReady) {
            triggerCause = Cause.HANDBRAKE;
        } else if (clutchReady) {
            triggerCause = Cause.CLUTCH_KICK;
        } else if (powerReady) {
            triggerCause = Cause.POWER;
        } else if (impactReady) {
            triggerCause = Cause.IMPACT;
        } else if (naturalReady) {
            triggerCause = naturalCause(liftOff, serviceBrake, grip, frontUse, rearUse);
        }

        boolean wasSliding = state.mode == Mode.SLIDE || state.mode == Mode.CONTROLLED;
        if (absoluteSpeed < 2.5) {
            state.mode = Mode.GRIP;
            state.cause = Cause.NONE;
            state.slideBlend = 0.0;
            state.controlBlend = 0.0;
            state.slideAgeSeconds = 0.0;
            state.driftGripHoldSeconds = 0.0;
            state.driftGripCause = Cause.NONE;
            state.previousLateralForce = 0.0;
            state.previousBulletYawTorque = 0.0;
        } else {
            updateMode(state, trigger, triggerCause, intentionalTrigger, actualSlideEvidence,
                    frontUse, rearUse, beta, expectedYaw, yawError, steering, dt);
        }

        boolean sliding = state.mode == Mode.SLIDE || state.mode == Mode.CONTROLLED;
        if (sliding) {
            if (!wasSliding) {
                state.slideAgeSeconds = 0.0;
            }
            state.slideAgeSeconds = clamp(state.slideAgeSeconds + dt, 0.0, 5.0);
        } else {
            state.slideAgeSeconds = 0.0;
        }
        state.slideBlend = approach(state.slideBlend, sliding ? 1.0 : 0.0,
                (sliding ? 2.2 : 1.8) * dt);

        double desiredLateralForce = 0.0;
        double desiredBulletYawTorque = 0.0;
        double normalForce = spec.massKg() * GRAVITY;
        double yawInertia = spec.massKg() * spec.wheelbaseMeters() * spec.wheelbaseMeters() * 0.16;
        boolean intentionalSlide = sliding && (
                state.cause == Cause.POWER && powerRequest
                        || state.cause == Cause.HANDBRAKE && handbrakeRequest
                        || state.cause == Cause.CLUTCH_KICK && clutchReady);
        if (intentionalSlide) {
            state.driftGripHoldSeconds = 0.35;
            state.driftGripCause = state.cause;
        } else {
            state.driftGripHoldSeconds = Math.max(0.0, state.driftGripHoldSeconds - dt);
        }
        boolean driftGripActive = (sliding || state.mode == Mode.RECOVERY)
                && state.driftGripHoldSeconds > 0.0 && isIntentionalCause(state.driftGripCause);
        double naturalRecoveryBlend = sliding && !intentionalSlide
                ? smoothStep(clamp((state.slideAgeSeconds - 0.30) / 0.55, 0.0, 1.0))
                : state.mode == Mode.RECOVERY ? 1.0 : 0.0;

        if (sliding && intentionalSlide && yawSignCalibrated) {
            double speedFade = 1.0 - smoothStep(clamp((absoluteSpeed - 18.0) / 12.0, 0.0, 1.0));
            if (state.cause == Cause.HANDBRAKE) {
                speedFade = Math.max(0.42, speedFade);
            }
            double direction = state.slideYawDirection == 0.0
                    ? Math.signum(expectedYaw) : state.slideYawDirection;
            double speedBlend = smoothStep(clamp((absoluteSpeed - 6.0) / 14.0, 0.0, 1.0));
            double targetBetaDegrees = state.cause == Cause.HANDBRAKE
                    ? lerp(18.0, 12.0, speedBlend)
                    : lerp(14.0, 9.0, speedBlend);
            double targetBetaMagnitude = Math.toRadians(targetBetaDegrees);
            double targetBeta = -direction * targetBetaMagnitude;
            double betaProgress = clamp(Math.abs(beta) / Math.max(Math.toRadians(3.0), targetBetaMagnitude),
                    0.0, 1.0);
            double rotationRetention = lerp(1.0, 0.18, smoothStep(betaProgress));
            double currentTurnDirection = Math.signum(expectedYaw);
            if (currentTurnDirection == 0.0) {
                currentTurnDirection = direction;
            }
            double rotationFeedForward = -currentTurnDirection
                    * normalForce * spec.wheelbaseMeters()
                    * (state.cause == Cause.HANDBRAKE ? 0.080 : 0.055)
                    * clamp(Math.abs(steering) / 0.45, 0.0, 1.0)
                    * state.slideBlend * tuning.driftIntensity() * speedFade * rotationRetention;
            double betaCorrection = clamp(
                    (targetBeta - beta) / Math.max(Math.toRadians(3.0), targetBetaMagnitude),
                    -1.25, 1.25)
                    * normalForce * spec.wheelbaseMeters() * 0.045
                    * state.slideBlend * tuning.driftIntensity() * speedFade;
            desiredBulletYawTorque = rotationFeedForward + betaCorrection;
        } else if ((sliding || state.mode == Mode.RECOVERY)
                && (yawSignCalibrated || Math.abs(steering) < 0.03)) {
            double recoverySeconds = sliding ? 1.15 : 0.62;
            double headingAcceleration = (expectedYaw - yawRate) / recoverySeconds;
            desiredBulletYawTorque = -headingAcceleration * yawInertia
                    * tuning.stabilityAssist() * Math.max(0.25, state.slideBlend)
                    * naturalRecoveryBlend;
        }

        double maximumBeta = lerp(Math.toRadians(18.0), Math.toRadians(8.0),
                smoothStep(clamp((absoluteSpeed - 8.0) / 20.0, 0.0, 1.0)));
        double allowedLateralSpeed = Math.tan(maximumBeta) * Math.max(absoluteSpeed, 1.0);
        double lateralExcess = Math.max(0.0, Math.abs(lateralSpeed) - allowedLateralSpeed);
        if (lateralExcess > 0.0) {
            desiredLateralForce = -Math.signum(lateralSpeed) * spec.massKg() * lateralExcess / 0.45;
        } else if (state.mode == Mode.RECOVERY || sliding && !intentionalSlide) {
            double recoverySeconds = state.mode == Mode.RECOVERY ? 0.68 : 1.25;
            desiredLateralForce = -lateralSpeed * spec.massKg() / recoverySeconds
                    * tuning.stabilityAssist() * naturalRecoveryBlend;
        }

        double lateralLimit = normalForce * (sliding ? 0.11 : 0.14)
                * Math.max(0.25, tuning.driftIntensity());
        double intentionalYawLimit = state.cause == Cause.HANDBRAKE ? 0.12 : 0.10;
        double yawLimit = normalForce * spec.wheelbaseMeters()
                * (intentionalSlide ? intentionalYawLimit : 0.050)
                * Math.max(0.25, intentionalSlide ? tuning.driftIntensity() : tuning.stabilityAssist());
        desiredLateralForce = clamp(desiredLateralForce, -lateralLimit, lateralLimit);
        desiredBulletYawTorque = clamp(desiredBulletYawTorque, -yawLimit, yawLimit);

        state.previousLateralForce = approach(state.previousLateralForce, desiredLateralForce,
                normalForce * (intentionalSlide ? 1.20 : 0.45) * dt);
        state.previousBulletYawTorque = approach(state.previousBulletYawTorque, desiredBulletYawTorque,
                normalForce * spec.wheelbaseMeters() * (intentionalSlide ? 0.95 : 0.38) * dt);
        if (state.mode == Mode.GRIP || state.mode == Mode.LIMIT) {
            state.previousLateralForce = 0.0;
            state.previousBulletYawTorque = 0.0;
        }

        double skidSpeed = sliding || state.mode == Mode.RECOVERY
                ? Math.max(Math.abs(lateralSpeed), Math.abs(beta) * absoluteSpeed)
                : 0.0;
        skidSpeed = Math.max(skidSpeed, input.rearNativeSkid() * 3.0);

        double wheelFrictionScale = 1.0;
        if (driftGripActive) {
            double baseScale = switch (state.driftGripCause) {
                case HANDBRAKE -> 0.38;
                case CLUTCH_KICK -> 0.42;
                case POWER -> 0.42;
                default -> 1.0;
            };
            double intensity = clamp(tuning.driftIntensity(), 0.0, 2.0);
            wheelFrictionScale = 1.0 - (1.0 - baseScale) * Math.min(1.0, intensity)
                    - 0.10 * Math.max(0.0, intensity - 1.0);
            wheelFrictionScale = clamp(wheelFrictionScale, 0.25, 1.0);
        }

        state.previousLongitudinalSpeedMps = speed;
        state.previousThrottle = throttle;
        state.motionInitialized = true;
        return new Output(state.mode, state.cause, beta, expectedYaw, yawError,
                frontUse, rearUse, state.slideBlend, state.controlBlend,
                state.previousLateralForce, state.previousBulletYawTorque,
                skidSpeed, yawSignCalibrated, intentionalSlide, driftGripActive, wheelFrictionScale);
    }

    private static void updateMode(State state, boolean trigger, Cause triggerCause,
            boolean intentionalTrigger, boolean actualSlideEvidence, double frontUse, double rearUse,
            double beta, double expectedYaw, double yawError, double steering, double dt) {
        if ((state.mode == Mode.SLIDE || state.mode == Mode.CONTROLLED)
                && intentionalTrigger && triggerCause != Cause.NONE) {
            Cause previousCause = state.cause;
            state.cause = triggerCause;
            if (!isIntentionalCause(previousCause) || previousCause != triggerCause) {
                double requestedDirection = Math.signum(expectedYaw);
                if (requestedDirection != 0.0) {
                    state.slideYawDirection = requestedDirection;
                }
            }
        }
        if ((state.mode == Mode.GRIP || state.mode == Mode.LIMIT || state.mode == Mode.RECOVERY) && trigger) {
            state.mode = Mode.SLIDE;
            state.cause = triggerCause;
            state.entrySteeringSign = Math.signum(steering);
            state.slideYawDirection = intentionalTrigger
                    ? Math.signum(expectedYaw) : Math.signum(yawError);
            if (state.slideYawDirection == 0.0) {
                state.slideYawDirection = -Math.signum(steering);
            }
        } else if (state.mode == Mode.GRIP && Math.max(frontUse, rearUse) >= 0.82) {
            state.mode = Mode.LIMIT;
        } else if (state.mode == Mode.LIMIT && Math.max(frontUse, rearUse) < 0.74) {
            state.mode = Mode.GRIP;
        }

        if (state.mode == Mode.SLIDE || state.mode == Mode.CONTROLLED) {
            boolean counterSteering = state.entrySteeringSign != 0.0
                    && Math.signum(steering) == -state.entrySteeringSign
                    && Math.abs(steering) >= 0.04;
            boolean controllableAngle = Math.abs(beta) <= Math.toRadians(20.0)
                    && Math.abs(yawError) <= 1.15;
            state.controlBlend = approach(state.controlBlend,
                    intentionalTrigger && actualSlideEvidence && counterSteering && controllableAngle ? 1.0 : 0.0,
                    (counterSteering ? 2.0 : 2.8) * dt);
            state.mode = state.controlBlend >= 0.35 ? Mode.CONTROLLED : Mode.SLIDE;

            boolean slideSettled = !trigger && rearUse < 0.88
                    && Math.abs(beta) < Math.toRadians(3.0) && Math.abs(yawError) < 0.16;
            if (slideSettled) {
                state.mode = Mode.RECOVERY;
                state.cause = Cause.NONE;
            }
        } else if (state.mode == Mode.RECOVERY
                && Math.abs(beta) < Math.toRadians(1.5) && Math.abs(yawError) < 0.08
                && state.slideBlend <= 0.02) {
            state.mode = Math.max(frontUse, rearUse) >= 0.82 ? Mode.LIMIT : Mode.GRIP;
        }
    }

    private static Cause naturalCause(boolean liftOff, double serviceBrake, AxleGrip grip,
            double frontUse, double rearUse) {
        if (liftOff) {
            return Cause.LIFT_OFF;
        }
        if (serviceBrake >= 0.15) {
            return Cause.BRAKING;
        }
        if (grip.rearCoefficient() < grip.frontCoefficient() * 0.88) {
            return Cause.TIRE_OR_SURFACE;
        }
        return rearUse > frontUse ? Cause.CORNERING : Cause.NONE;
    }

    private static boolean isIntentionalCause(Cause cause) {
        return cause == Cause.POWER || cause == Cause.HANDBRAKE || cause == Cause.CLUTCH_KICK;
    }

    private static double approach(double value, double target, double maximumDelta) {
        if (value < target) {
            return Math.min(target, value + maximumDelta);
        }
        return Math.max(target, value - maximumDelta);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static double smoothStep(double value) {
        double x = clamp(value, 0.0, 1.0);
        return x * x * (3.0 - 2.0 * x);
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    public enum Mode {
        GRIP,
        LIMIT,
        SLIDE,
        CONTROLLED,
        RECOVERY
    }

    public enum Cause {
        NONE,
        POWER,
        HANDBRAKE,
        CLUTCH_KICK,
        LIFT_OFF,
        BRAKING,
        TIRE_OR_SURFACE,
        IMPACT,
        CORNERING
    }

    public record Spec(double massKg, double wheelbaseMeters, double frontStaticWeightShare,
            double centerOfMassHeightMeters, double rearDriveShare) {
        public Spec {
            massKg = clamp(massKg, 100.0, 20_000.0);
            wheelbaseMeters = clamp(wheelbaseMeters, 1.2, 8.0);
            frontStaticWeightShare = clamp(frontStaticWeightShare, 0.30, 0.75);
            centerOfMassHeightMeters = clamp(centerOfMassHeightMeters, 0.25, 1.50);
            rearDriveShare = clamp(rearDriveShare, 0.0, 1.0);
        }

        public static Spec defaults() {
            return new Spec(1_200.0, 2.4, 0.52, 0.55, 1.0);
        }
    }

    public record AxleGrip(double frontCoefficient, double rearCoefficient) {
        public AxleGrip {
            frontCoefficient = clamp(frontCoefficient, 0.08, 1.8);
            rearCoefficient = clamp(rearCoefficient, 0.08, 1.8);
        }

        public static AxleGrip defaults() {
            return new AxleGrip(1.0, 1.0);
        }
    }

    public record Tuning(boolean enabled, double driftIntensity, double stabilityAssist,
            double powerDriftEntryDelaySeconds, boolean clutchKickEnabled) {
        public Tuning {
            driftIntensity = clamp(driftIntensity, 0.0, 2.0);
            stabilityAssist = clamp(stabilityAssist, 0.0, 2.0);
            powerDriftEntryDelaySeconds = clamp(powerDriftEntryDelaySeconds, 0.30, 2.0);
        }

        public static Tuning defaults() {
            return new Tuning(true, 1.0, 1.0, 0.80, true);
        }
    }

    public record Input(double longitudinalSpeedMps, double lateralSpeedMps,
            double yawRateRadiansPerSecond, double steeringAngleRadians, double throttle,
            double serviceBrake, double handbrake, int gear, double rawDriveForce,
            double tractionLimit, double clutchKickIntensity, double rearNativeSkid,
            boolean impactDisturbance) {
        public static Input idle() {
            return new Input(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    1, 0.0, 1.0, 0.0, 0.0, false);
        }
    }

    public static final class State {
        public Mode mode = Mode.GRIP;
        public Cause cause = Cause.NONE;
        public boolean motionInitialized;
        public double previousLongitudinalSpeedMps;
        public double previousThrottle;
        public double filteredLongitudinalAccelerationMps2;
        public double steeringYawCorrelation;
        public double yawCalibrationSeconds;
        public double powerIntentSeconds;
        public double handbrakeIntentSeconds;
        public double naturalOverloadSeconds;
        public double slideAgeSeconds;
        public double driftGripHoldSeconds;
        public Cause driftGripCause = Cause.NONE;
        public double slideBlend;
        public double controlBlend;
        public double entrySteeringSign;
        public double slideYawDirection;
        public double previousLateralForce;
        public double previousBulletYawTorque;

        public void reset() {
            mode = Mode.GRIP;
            cause = Cause.NONE;
            motionInitialized = false;
            previousLongitudinalSpeedMps = 0.0;
            previousThrottle = 0.0;
            filteredLongitudinalAccelerationMps2 = 0.0;
            powerIntentSeconds = 0.0;
            handbrakeIntentSeconds = 0.0;
            naturalOverloadSeconds = 0.0;
            slideAgeSeconds = 0.0;
            driftGripHoldSeconds = 0.0;
            driftGripCause = Cause.NONE;
            slideBlend = 0.0;
            controlBlend = 0.0;
            entrySteeringSign = 0.0;
            slideYawDirection = 0.0;
            previousLateralForce = 0.0;
            previousBulletYawTorque = 0.0;
        }
    }

    public record Output(Mode mode, Cause cause, double sideSlipAngleRadians,
            double expectedYawRateRadiansPerSecond, double yawErrorRadiansPerSecond,
            double frontGripUse, double rearGripUse, double slideBlend, double controlBlend,
            double lateralForce, double bulletYawTorque, double skidSpeedMps,
            boolean yawSignCalibrated, boolean intentionalSlide, boolean driftGripActive,
            double wheelFrictionScale) {
        public static Output inactive() {
            return new Output(Mode.GRIP, Cause.NONE, 0.0, 0.0, 0.0,
                    0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    false, false, false, 1.0);
        }
    }
}
