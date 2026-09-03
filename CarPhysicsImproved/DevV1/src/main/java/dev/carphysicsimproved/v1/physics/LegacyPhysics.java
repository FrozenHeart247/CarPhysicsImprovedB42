package dev.carphysicsimproved.v1.physics;

import java.util.Arrays;

/**
 * Independent implementation of the legacy RCP-style drivetrain. It keeps the
 * old model's characteristic gear sets, torque-converter slip, traction cap,
 * speed-squared drag and speed-sensitive steering without loading old classes.
 */
public final class LegacyPhysics {
    private LegacyPhysics() {
    }

    public static Output step(Spec spec, Conditions conditions, Settings settings, Input input, State state,
            double deltaSeconds) {
        double dt = clamp(deltaSeconds, 1.0 / 240.0, 0.05);
        int previousGear = state.lastStepGear;
        double speedKph = input.longitudinalSpeedMps() * 3.6;
        double absoluteSpeedKph = Math.abs(speedKph);
        boolean forwardPropulsion = input.manualTransmission()
                ? input.forwardDemand()
                : input.forwardDemand() && speedKph >= -0.8;
        boolean reversePropulsion = !input.manualTransmission()
                && input.reverseDemand() && speedKph <= 0.8;
        double throttleTarget = forwardPropulsion || reversePropulsion
                ? clamp(input.analogThrottle(), 0.0, 1.0)
                : 0.0;
        state.throttle = approach(state.throttle, throttleTarget, dt * 4.0);
        state.fullThrottleSeconds = state.throttle >= 0.995
                ? clamp(state.fullThrottleSeconds + dt, 0.0, 1.0)
                : clamp(state.fullThrottleSeconds - dt, 0.0, 1.0);

        int gear = input.requestedGear();
        if (!input.manualTransmission()) {
            if (input.reverseDemand() && speedKph <= 0.8) {
                gear = -1;
            } else if (input.forwardDemand() || speedKph > 0.8) {
                gear = automaticGear(spec, state, absoluteSpeedKph);
            } else {
                gear = state.gear == -1 && absoluteSpeedKph < 0.8 ? -1 : Math.max(1, state.gear);
            }
        }
        gear = clampGear(gear, spec.forwardRatios.length);
        state.gear = gear;

        double serviceBrake = input.serviceBrake() ? 1.0 : 0.0;
        if (!input.manualTransmission()) {
            if (input.reverseDemand() && speedKph > 0.8) {
                serviceBrake = 1.0;
            }
            if (input.forwardDemand() && speedKph < -0.8) {
                serviceBrake = 1.0;
            }
        }
        if (input.handbrake()) {
            serviceBrake = Math.max(serviceBrake, 1.0);
        }

        double pressure = clamp(conditions.tirePressure(), 0.0, 1.35);
        double conditionGrip = clamp(conditions.tireCondition(), 0.0, 1.25) * 0.5 + 0.5;
        double tireTraction = conditionGrip * settings.overallTraction();
        tireTraction *= clamp(conditions.surfaceGrip(), 0.1, 1.0);
        tireTraction = clamp(tireTraction, 0.05, 1.8);

        double steeringSpeedRatio = clamp(absoluteSpeedKph / settings.steeringHighSpeedKph(), 0.0, 1.0);
        double steeringFactor = lerp(settings.steeringFactorLowSpeed(), settings.steeringFactorHighSpeed(),
                steeringSpeedRatio);
        double centeringFactor = lerp(settings.steeringCenteringLowSpeed(), settings.steeringCenteringHighSpeed(),
                steeringSpeedRatio);
        double requestedSteering = clamp(input.steeringInput(), -1.0, 1.0);
        if (Math.abs(requestedSteering) > 0.1) {
            if (Math.signum(requestedSteering) != Math.signum(state.steering) && Math.abs(state.steering) > 0.001) {
                steeringFactor *= settings.steeringSnapback();
            }
            state.steering += (requestedSteering - state.steering) * 3.0 * dt * steeringFactor;
        } else if (Math.abs(state.steering) <= 0.04) {
            state.steering = 0.0;
        } else {
            state.steering = approach(state.steering, 0.0, centeringFactor * 4.0 * dt);
        }
        state.steering = clamp(state.steering, -spec.steeringClampRadians, spec.steeringClampRadians);

        double redline = spec.redlineRpm;
        double finalDrive = 0.95 * redline / Math.max(10.0, spec.maximumSpeedKph);
        double ratio = ratioFor(spec, gear);
        double wheelInputRpm = absoluteSpeedKph * Math.abs(ratio) * finalDrive;
        double rpm = Math.max(spec.idleRpm, state.engineRpm);
        if (input.manualTransmission() && input.clutchKickEnabled()
                && previousGear == 0 && gear != 0) {
            double rpmMismatch = Math.max(0.0, rpm - Math.max(spec.idleRpm, wheelInputRpm));
            double mismatch = clamp(rpmMismatch / Math.max(500.0, redline - spec.idleRpm), 0.0, 1.0);
            double firstRatio = spec.forwardRatios[0];
            double ratioAuthority = clamp(Math.abs(ratio) / Math.max(0.2, firstRatio), 0.0, 1.0);
            if (mismatch >= 0.12) {
                state.clutchKick = Math.max(state.clutchKick,
                        mismatch * Math.pow(ratioAuthority, 1.25));
            }
        }
        if (!input.engineRunning()) {
            rpm = Math.max(0.0, rpm - dt * 1600.0);
        } else if (gear == 0) {
            double freeRevTarget = spec.idleRpm + state.throttle * (redline - spec.idleRpm);
            rpm = approach(rpm, freeRevTarget, dt * (1800.0 + state.throttle * 4200.0));
        }

        double driveForce = 0.0;
        double rawDriveForce = 0.0;
        double burnoutSpeedKph = 0.0;
        if (input.engineRunning() && gear != 0 && state.throttle > 0.0) {
            double torqueScale = switch (spec.mechanicType) {
                case 3 -> settings.sportTorqueMultiplier();
                case 2 -> settings.heavyTorqueMultiplier();
                default -> settings.standardTorqueMultiplier();
            };
            double baseTorque = spec.enginePower * torqueScale * 4500.0 / redline;
            double curve = clamp(1.0 - (rpm - redline) / 1000.0, 0.0, 1.0)
                    * clamp(rpm / redline * 2.0, 0.2, 1.0);
            double engineTorque = state.throttle * curve * baseTorque - baseTorque * 0.35 * rpm / redline;

            double speedRatio = clamp(wheelInputRpm / Math.max(rpm, 1.0), 0.0, 1.0);
            double converter = clamp((rpm - (settings.converterLockupRpm() - settings.converterLockupRangeRpm()))
                    / settings.converterLockupRangeRpm(), 0.0, 3.0);
            converter -= speedRatio;
            converter *= clamp((1.0 - speedRatio) * 5.0, 0.0, 1.0);
            converter = clamp(converter * 1.2, 0.0, 1.2);
            double torqueMultiplier = lerp(settings.torqueMultiplierLimit(), 1.0,
                    clamp(speedRatio * 1.1, 0.0, 1.0));

            rawDriveForce = engineTorque * converter * torqueMultiplier * Math.abs(ratio) * finalDrive * 0.05;
            double tractionLimit = spec.massKg * 2.0 * tireTraction * settings.accelerationTraction();
            if (state.clutchKick > 0.0) {
                double clutchForce = baseTorque * Math.abs(ratio) * finalDrive * 0.05
                        * state.clutchKick * (0.45 + 0.85 * state.throttle);
                rawDriveForce += clutchForce;

                // A clutch dump is a short wheel-torque overload, not a body-force
                // multiplier. Demand may exceed available grip, while the force sent
                // to the vehicle remains clipped below. The excess becomes wheelspin.
                double wheelTorqueAuthority = clamp(
                        baseTorque * Math.abs(ratio) * finalDrive * 0.05
                                / Math.max(1.0, tractionLimit),
                        0.0, 1.5);
                double clutchDemandRatio = 0.72 + state.clutchKick
                        * (0.68 + 0.45 * wheelTorqueAuthority)
                        * (0.55 + 0.45 * state.throttle);
                rawDriveForce = Math.max(rawDriveForce, tractionLimit * clutchDemandRatio);

                double clutchTargetRpm = Math.max(spec.idleRpm, wheelInputRpm);
                rpm = approach(rpm, clutchTargetRpm,
                        dt * (1_800.0 + state.clutchKick * 4_200.0));
            }
            driveForce = Math.min(Math.max(0.0, rawDriveForce), tractionLimit);
            burnoutSpeedKph = Math.max(0.0, (rawDriveForce - tractionLimit) * dt * 0.02 - 3.0);
            if (state.clutchKick > 0.0) {
                double clutchSlipSpeedKph = Math.max(0.0, rawDriveForce - tractionLimit)
                        / Math.max(100.0, spec.massKg) * 3.6 * state.clutchKick;
                burnoutSpeedKph = Math.max(burnoutSpeedKph, clutchSlipSpeedKph);
            }
            double coupledTarget = Math.max(spec.idleRpm, wheelInputRpm);
            double couplingRate = clamp(converter, 0.0, 1.0) * dt * 9.0;
            rpm = lerp(rpm, coupledTarget, clamp(couplingRate, 0.0, 1.0));
            rpm += engineTorque / Math.max(0.001, baseTorque * 1.0E-4) * dt;
        } else if (input.engineRunning() && gear != 0) {
            // Keep the engine-speed value coupled while coasting. Previously RPM was
            // only updated under throttle, so the value (and vanilla engine audio)
            // could remain frozen at the last acceleration RPM all the way to rest.
            double coastTargetRpm = clamp(Math.max(spec.idleRpm, wheelInputRpm), spec.idleRpm, redline);
            double rpmDifference = Math.abs(rpm - coastTargetRpm);
            double coastRateRpmPerSecond = 900.0 + Math.min(2_700.0, rpmDifference * 1.5);
            rpm = approach(rpm, coastTargetRpm, coastRateRpmPerSecond * dt);
        }

        if (gear < 0) {
            driveForce = -driveForce;
        }
        if (gear < 0 && absoluteSpeedKph >= settings.reverseSpeedLimitKph()) {
            driveForce = Math.min(0.0, driveForce) * clamp(
                    (settings.reverseSpeedLimitKph() + 5.0 - absoluteSpeedKph) / 5.0, 0.0, 1.0);
        }

        boolean movingHandbrakeTurn = input.handbrake()
                && absoluteSpeedKph >= 14.0 && Math.abs(input.steeringInput()) >= 0.075;
        double handbrakeMultiplier = movingHandbrakeTurn ? 0.85 : input.handbrake() ? 3.0 : 1.0;
        double brakeForce = serviceBrake * spec.brakingForce * handbrakeMultiplier;
        if (rawDriveForce < 0.0 && gear != 0) {
            brakeForce += -rawDriveForce / 50.0;
        }

        double aeroMultiplier = switch (spec.mechanicType) {
            case 3 -> settings.aerodynamicDragSport();
            case 2 -> settings.aerodynamicDragHeavy();
            default -> settings.aerodynamicDragStandard();
        };
        double drag = absoluteSpeedKph * absoluteSpeedKph * 0.05 * aeroMultiplier;
        if (conditions.offroad()) {
            double rolling = settings.offroadRollingResistance()
                    + 0.01 * absoluteSpeedKph * settings.offroadRollingResistanceSpeed();
            rolling *= spec.massKg * (1.0 + pressure * 0.6) * conditions.offroadResistanceScale();
            drag += rolling;
        } else {
            double rolling = settings.rollingResistance()
                    + 0.01 * absoluteSpeedKph * settings.rollingResistanceSpeed();
            rolling *= spec.massKg * (2.0 - pressure);
            drag += rolling;
        }

        if (!Double.isFinite(rpm)) {
            rpm = spec.idleRpm;
        }
        state.engineRpm = clamp(rpm, 0.0, redline + 1200.0);
        state.burnout = burnoutSpeedKph;
        double clutchKickIntensity = state.clutchKick;
        state.clutchKick = approach(state.clutchKick, 0.0, dt * 1.6);
        state.lastStepGear = gear;
        return new Output(gear, driveForce, brakeForce, state.steering, drag, tireTraction,
                burnoutSpeedKph, state.engineRpm, state.throttle, rawDriveForce, clutchKickIntensity);
    }

    private static int automaticGear(Spec spec, State state, double speedKph) {
        double finalDrive = 0.95 * spec.redlineRpm / Math.max(10.0, spec.maximumSpeedKph);
        int gear = 1;
        for (; gear < spec.forwardRatios.length; gear++) {
            double hysteresis = state.gear > gear ? 500.0 : 0.0;
            if (speedKph * spec.forwardRatios[gear - 1] * finalDrive < spec.shiftRpm - hysteresis) {
                break;
            }
        }
        gear = Math.min(gear, spec.forwardRatios.length);
        if (gear < spec.forwardRatios.length
                && speedKph * spec.forwardRatios[gear] * finalDrive > spec.shiftRpm * 0.5
                && state.fullThrottleSeconds < 0.5) {
            gear++;
        }
        return gear;
    }

    private static double ratioFor(Spec spec, int gear) {
        if (gear < 0) {
            return spec.reverseRatio;
        }
        if (gear == 0) {
            return 0.0;
        }
        return spec.forwardRatios[Math.min(spec.forwardRatios.length, gear) - 1];
    }

    private static int clampGear(int gear, int count) {
        return Math.max(-1, Math.min(Math.max(1, count), gear));
    }

    public static double[] legacyRatios(int requestedGearCount) {
        return switch (requestedGearCount) {
            case 3 -> new double[]{2.6, 1.6, 1.0};
            case 5 -> new double[]{3.2, 2.0, 1.5, 1.15, 0.9};
            default -> new double[]{3.0, 1.8, 1.3, 1.0};
        };
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

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record Spec(String fullType, double massKg, double enginePower, double maximumSpeedKph,
            double idleRpm, double redlineRpm, double shiftRpm, double reverseRatio, double[] forwardRatios,
            double brakingForce, double steeringClampRadians, double offroadEfficiency, int mechanicType) {
        public Spec {
            fullType = fullType == null ? "unknown" : fullType;
            massKg = clamp(massKg, 100.0, 20_000.0);
            enginePower = clamp(enginePower, 1.0, 5_000.0);
            maximumSpeedKph = clamp(maximumSpeedKph, 10.0, 120.0);
            idleRpm = clamp(idleRpm, 300.0, 2_000.0);
            redlineRpm = clamp(redlineRpm, 2_000.0, 10_000.0);
            shiftRpm = clamp(shiftRpm, idleRpm + 500.0, redlineRpm);
            reverseRatio = clamp(Math.abs(reverseRatio), 0.2, 8.0);
            forwardRatios = forwardRatios == null || forwardRatios.length == 0
                    ? legacyRatios(4) : Arrays.copyOf(forwardRatios, Math.min(8, forwardRatios.length));
            for (int index = 0; index < forwardRatios.length; index++) {
                forwardRatios[index] = clamp(Math.abs(forwardRatios[index]), 0.2, 8.0);
            }
            brakingForce = clamp(brakingForce, 0.1, 100.0);
            steeringClampRadians = clamp(Math.abs(steeringClampRadians), 0.05, 1.5);
            offroadEfficiency = clamp(offroadEfficiency, 0.15, 4.0);
            mechanicType = Math.max(1, Math.min(3, mechanicType));
        }
    }

    public record Conditions(double tirePressure, double tireCondition, double surfaceGrip,
            boolean offroad, double offroadResistanceScale) {
        public Conditions {
            offroadResistanceScale = clamp(offroadResistanceScale, 0.10, 1.0);
        }

        public Conditions(double tirePressure, double tireCondition, double surfaceGrip, boolean offroad) {
            this(tirePressure, tireCondition, surfaceGrip, offroad, 1.0);
        }
    }

    public record Input(double longitudinalSpeedMps, boolean engineRunning, boolean forwardDemand,
            boolean reverseDemand, boolean serviceBrake, boolean handbrake, double steeringInput,
            double analogThrottle, boolean manualTransmission, int requestedGear, boolean clutchKickEnabled) {
        public Input(double longitudinalSpeedMps, boolean engineRunning, boolean forwardDemand,
                boolean reverseDemand, boolean serviceBrake, boolean handbrake, double steeringInput,
                double analogThrottle, boolean manualTransmission, int requestedGear) {
            this(longitudinalSpeedMps, engineRunning, forwardDemand, reverseDemand,
                    serviceBrake, handbrake, steeringInput, analogThrottle,
                    manualTransmission, requestedGear, true);
        }
    }

    public record Settings(double sportTorqueMultiplier, double standardTorqueMultiplier,
            double heavyTorqueMultiplier, double torqueMultiplierLimit, double reverseSpeedLimitKph,
            double aerodynamicDragSport, double aerodynamicDragStandard, double aerodynamicDragHeavy,
            double rollingResistance, double rollingResistanceSpeed, double offroadRollingResistance,
            double offroadRollingResistanceSpeed, double overallTraction, double accelerationTraction,
            double steeringFactorLowSpeed, double steeringFactorHighSpeed,
            double steeringCenteringLowSpeed, double steeringCenteringHighSpeed,
            double steeringSnapback, double steeringHighSpeedKph,
            double converterLockupRpm, double converterLockupRangeRpm) {
        public static Settings defaults() {
            return new Settings(1.0, 1.0, 1.0, 2.5, 40.0,
                    0.70, 1.0, 1.5, 0.05, 0.1, 0.2, 1.0, 1.0, 1.0,
                    1.0, 0.1, 1.0, 0.1, 3.0, 75.0, 2_000.0, 800.0);
        }
    }

    public static final class State {
        public int gear = 1;
        public double engineRpm = 800.0;
        public double throttle;
        public double fullThrottleSeconds;
        public double steering;
        public double burnout;
        public int lastStepGear = 1;
        public double clutchKick;
    }

    public record Output(int gear, double engineForce, double brakingForce, double steeringRadians,
            double dragMagnitude, double tireTraction, double burnoutSpeedKph, double engineRpm,
            double throttle, double rawDriveForce, double clutchKickIntensity) {
    }
}
