package dev.carphysicsimproved.v2.runtime;

import dev.carphysicsimproved.v2.physics.DriverInput;
import dev.carphysicsimproved.v2.physics.DynamicsOutput;
import dev.carphysicsimproved.v2.physics.DynamicsState;
import dev.carphysicsimproved.v2.physics.ScriptVehicleData;
import dev.carphysicsimproved.v2.physics.TransmissionMode;
import dev.carphysicsimproved.v2.physics.VehicleCondition;
import dev.carphysicsimproved.v2.physics.VehicleDynamics;
import dev.carphysicsimproved.v2.physics.VehicleMotion;
import dev.carphysicsimproved.v2.physics.VehicleSpec;
import pzmod.carphysicsimproved.CarPhysicsImprovedMod;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Game-facing callbacks. Failure disables V2 cleanly and leaves vanilla available. */
public final class RuntimeHooks {
    private static final Map<Object, RuntimeState> VEHICLES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Object ACCESS_LOCK = new Object();
    private static volatile PzRuntimeAccess access;
    private static volatile boolean accessAttempted;
    private static volatile boolean failed;

    private RuntimeHooks() {
    }

    public static void afterControllerUpdate(Object controller) {
        if (!CarPhysicsImprovedMod.enabled() || failed || controller == null) {
            return;
        }
        PzRuntimeAccess current = access();
        if (current == null) {
            return;
        }
        try {
            Object vehicle = current.vehicle(controller);
            if (vehicle == null || !current.hasAuthority(vehicle) || !current.hasDriver(vehicle)) {
                return;
            }
            RuntimeState runtime = VEHICLES.computeIfAbsent(vehicle, ignored -> new RuntimeState());
            Object scriptIdentity = current.script(vehicle);
            if (runtime.specification == null || runtime.scriptIdentity != scriptIdentity) {
                runtime.scriptIdentity = scriptIdentity;
                runtime.snapshot = current.snapshot(vehicle);
                runtime.snapshot = withDriveLayout(
                        runtime.snapshot,
                        CarPhysicsImprovedMod.driveLayoutFor(runtime.snapshot.fullType()));
                runtime.specification = VehicleSpec.fromScript(runtime.snapshot);
                int nativeGear = current.currentGear(vehicle);
                int initialGear = nativeGear == 0 && !CarPhysicsImprovedMod.manualMode() ? 1 : nativeGear;
                runtime.dynamics = new DynamicsState(
                        initialGear,
                        Math.max(current.engineRpm(vehicle), runtime.specification.engine().idleRpm()),
                        0.0,
                        0.0,
                        0.0,
                        0.0);
                runtime.desiredGear = normalizeManualGear(
                        initialGear,
                        runtime.specification.transmission().gearCount());
                runtime.condition = VehicleCondition.healthy(runtime.specification);
                runtime.observerInitialized = false;
                runtime.activeAgeSeconds = 0.0;
                System.out.println("[CarPhysicsImproved] V2 attached: vehicle=" + current.vehicleId(vehicle)
                        + " script=" + runtime.specification.fullType()
                        + " mass=" + Math.round(runtime.specification.massKg())
                        + " maxSpeed=" + Math.round(runtime.specification.maximumSpeedKph())
                        + " torque=" + Math.round(runtime.specification.engine().peakTorqueNm())
                        + " gears=" + runtime.specification.transmission().gearCount());
            }

            double delta = current.deltaSeconds();
            runtime.activeAgeSeconds += delta;
            boolean vanillaCollisionWindow = CarPhysicsImprovedMod.vanillaCollisionResponse()
                    && runtime.vanillaCollisionSecondsRemaining > 0.0;
            runtime.vanillaCollisionSecondsRemaining = Math.max(
                    0.0,
                    runtime.vanillaCollisionSecondsRemaining - delta);
            runtime.conditionTimer -= delta;
            if (runtime.conditionTimer <= 0.0) {
                runtime.condition = current.condition(vehicle, runtime.snapshot);
                runtime.conditionTimer = 0.10;
            }
            PzRuntimeAccess.Controls controls = current.controls(controller, vehicle);
            PzRuntimeAccess.MotionSample sample = current.motion(vehicle);
            double yawRate = runtime.observerInitialized
                    ? wrapAngle(sample.headingRadians() - runtime.lastHeading) / delta
                    : 0.0;
            VehicleMotion motion = new VehicleMotion(
                    sample.longitudinalSpeedMps(),
                    sample.lateralSpeedMps(),
                    yawRate,
                    sample.roadGradeRadians());

            // CarController and Bullet can briefly disagree while a character
            // enters and wakes a parked vehicle. Keep that transition vanilla;
            // differentiating its first samples caused an unstable impulse loop.
            if (runtime.activeAgeSeconds < 0.75) {
                runtime.lastLongSpeed = motion.longitudinalSpeedMps();
                runtime.lastLatSpeed = motion.lateralSpeedMps();
                runtime.lastYawRate = yawRate;
                runtime.lastHeading = sample.headingRadians();
                runtime.observerInitialized = true;
                return;
            }

            int id = current.vehicleId(vehicle);
            boolean manual = CarPhysicsImprovedMod.manualMode();
            if (manual) {
                int shiftRequest = CarPhysicsImprovedMod.consumeShiftRequest(id);
                if (shiftRequest != 0) {
                    int previousGear = runtime.desiredGear;
                    runtime.desiredGear = manualGearAfterShift(
                            previousGear,
                            shiftRequest,
                            runtime.specification.transmission().gearCount());
                    System.out.println("[CarPhysicsImproved] Manual shift accepted: vehicle=" + id
                            + " gear=" + gearName(previousGear) + "->" + gearName(runtime.desiredGear));
                }
            } else {
                CarPhysicsImprovedMod.consumeShiftRequest(id);
            }

            boolean forward = controls.forward();
            boolean backward = controls.backward();
            double serviceBrake = 0.0;
            double throttle = 0.0;
            int directionRequest = runtime.desiredGear;
            if (manual) {
                if (forward && backward) {
                    serviceBrake = 1.0;
                } else if (runtime.desiredGear > 0) {
                    if (forward && motion.longitudinalSpeedMps() >= -0.65) {
                        throttle = controls.analogThrottle();
                    } else if (forward || backward) {
                        serviceBrake = 1.0;
                    }
                } else if (runtime.desiredGear < 0) {
                    if (backward && motion.longitudinalSpeedMps() <= 0.65) {
                        throttle = controls.analogThrottle();
                    } else if (forward || backward) {
                        serviceBrake = 1.0;
                    }
                } else {
                    // Neutral permits free-revving, while the backward pedal
                    // remains a brake and never selects reverse by itself.
                    throttle = forward ? controls.analogThrottle() : 0.0;
                    serviceBrake = backward ? 1.0 : 0.0;
                }
            } else if (forward && backward) {
                serviceBrake = 1.0;
            } else if (forward) {
                if (motion.longitudinalSpeedMps() < -0.65) {
                    serviceBrake = 1.0;
                } else {
                    throttle = controls.analogThrottle();
                    directionRequest = 1;
                }
            } else if (backward) {
                if (motion.longitudinalSpeedMps() > 0.65) {
                    serviceBrake = 1.0;
                } else {
                    throttle = controls.analogThrottle();
                    directionRequest = -1;
                }
            }
            boolean engineRunning = current.engineRunning(vehicle);
            if (!engineRunning) {
                throttle = 0.0;
            }

            DriverInput input = new DriverInput(
                    throttle,
                    serviceBrake,
                    controls.handbrake() ? 1.0 : 0.0,
                    controls.steering(),
                    manual ? TransmissionMode.MANUAL : TransmissionMode.AUTOMATIC,
                    directionRequest,
                    current.surfaceGrip(vehicle) * CarPhysicsImprovedMod.tireGripMultiplier());
            DynamicsOutput output = VehicleDynamics.step(
                    runtime.specification,
                    runtime.dynamics,
                    input,
                    motion,
                    runtime.condition,
                    CarPhysicsImprovedMod.physicsTuning(),
                    delta);
            runtime.dynamics = output.state();
            runtime.lastOutput = output;
            runtime.desiredGear = output.state().gear();
            CarPhysicsImprovedMod.updateBurnoutAmount(
                    id,
                    output.burnout() ? output.wheelSlipMps() : 0.0);
            double lateralSkid = output.drifting()
                    ? Math.max(
                            Math.abs(motion.lateralSpeedMps()),
                            Math.abs(output.rearSlipAngleRadians())
                                    * Math.max(3.0, Math.abs(motion.longitudinalSpeedMps())))
                    : 0.0;
            CarPhysicsImprovedMod.updateSkidAmount(
                    id,
                    Math.max(output.burnout() ? output.wheelSlipMps() : 0.0, lateralSkid));
            int nativeGear = current.currentGear(vehicle);
            boolean gearMirrored = nativeGear != output.state().gear();
            if (gearMirrored) {
                current.setGear(vehicle, output.state().gear());
            }
            // V2 keeps its RPM internally for torque and shifting. The native
            // BaseVehicle RPM remains owned by VehicleSounds/CarController so
            // individual vanilla and Workshop engine banks cannot be muted by
            // an out-of-contract synthetic RPM value.
            boolean engineSounding = current.engineSounding(vehicle);

            double effectiveMass = runtime.specification.massKg() + runtime.condition.sanitizedPayloadKg();
            double longLimit = effectiveMass * 9.80665 * 1.50;
            double lateralLimit = effectiveMass * 9.80665
                    * (output.drifting() ? 0.85 : 0.62);
            double torqueLimit = effectiveMass * 9.80665
                    * runtime.specification.chassis().wheelbaseMeters()
                    * (output.drifting() ? 0.40 : 0.24);
            double longCommand = clamp(output.longitudinalForceN(), -longLimit, longLimit);
            double latCommand = clamp(output.lateralForceN(), -lateralLimit, lateralLimit);
            double yawCommand = clamp(output.yawTorqueNm(), -torqueLimit, torqueLimit);
            boolean intentionalSlide = controls.handbrake()
                    || output.drifting()
                    || output.state().driftIntentSeconds()
                            >= CarPhysicsImprovedMod.physicsTuning().driftEntryDelaySeconds();
            StabilityRecovery recovery = recoverUnintendedSlide(
                    motion.longitudinalSpeedMps(),
                    motion.lateralSpeedMps(),
                    yawRate,
                    effectiveMass,
                    runtime.specification.chassis().wheelbaseMeters(),
                    intentionalSlide,
                    CarPhysicsImprovedMod.recoveryStrengthMultiplier(),
                    latCommand,
                    yawCommand);
            latCommand = recovery.lateralCommandN();
            yawCommand = recovery.yawCommandNm();

            // BaseVehicle.crash and Bullet keep complete ownership of the
            // collision. During this short post-impact window, retain the
            // CarController command that already ran and do not layer V2 body
            // forces or emergency recovery over the native response.
            if (vanillaCollisionWindow) {
                longCommand = 0.0;
                latCommand = 0.0;
                yawCommand = 0.0;
                recovery = new StabilityRecovery(0.0, 0.0, 0.0);
            }

            boolean parkedIdle = Math.hypot(
                    motion.longitudinalSpeedMps(), motion.lateralSpeedMps()) < 0.35
                    && throttle < 0.01
                    && serviceBrake <= 0.0
                    && !controls.handbrake();
            if (parkedIdle) {
                longCommand = 0.0;
                latCommand = 0.0;
                yawCommand = 0.0;
            }

            if (!vanillaCollisionWindow) {
                current.overrideController(controller, vehicle, output.state().steeringAngleRadians());
                current.applyBodyForces(vehicle, sample, longCommand, latCommand, yawCommand);
            }

            runtime.lastLongCommand = longCommand;
            runtime.lastLatCommand = latCommand;
            runtime.lastYawCommand = yawCommand;
            runtime.lastLongSpeed = motion.longitudinalSpeedMps();
            runtime.lastLatSpeed = motion.lateralSpeedMps();
            runtime.lastYawRate = yawRate;
            runtime.lastHeading = sample.headingRadians();
            runtime.lastDelta = output.deltaSeconds();
            runtime.observerInitialized = true;

            long now = System.nanoTime();
            if (CarPhysicsImprovedMod.telemetry() && now - runtime.lastTelemetryNanos > 2_000_000_000L) {
                runtime.lastTelemetryNanos = now;
                System.out.println("[CarPhysicsImproved] vehicle=" + id
                        + " mode=" + (manual ? "M" : "A")
                        + " gear=" + gearName(output.state().gear())
                        + " speed=" + round(motion.longitudinalSpeedMps() * 3.6, 1)
                        + " rpm=" + Math.round(output.state().engineRpm())
                        + " nativeRpm=" + Math.round(current.engineRpm(vehicle))
                        + " throttle=" + round(throttle, 2)
                        + " nativeLoad=" + Math.round(current.controllerEngineForce(controller))
                        + " engineSound=" + engineSounding
                        + " drive=" + Math.round(output.rawDriveForceN())
                        + "/" + Math.round(output.propulsionForceLimitN())
                        + " long=" + Math.round(output.longitudinalForceN())
                        + " cmd=" + Math.round(longCommand)
                        + " payload=" + Math.round(runtime.condition.sanitizedPayloadKg())
                        + " engineCond=" + round(runtime.condition.sanitizedEngineCondition(), 2)
                        + " brakeCond=" + round(runtime.condition.sanitizedBrakesCondition(), 2)
                        + " suspCond=" + round(runtime.condition.sanitizedSuspensionCondition(), 2)
                        + " grade=" + round(sample.roadGradeRadians(), 3)
                        + " slip=" + round(output.wheelSlipMps(), 2)
                        + " driftIntent=" + round(output.state().driftIntentSeconds(), 2)
                        + " steer=" + round(output.state().steeringAngleRadians(), 3)
                        + " side=" + round(motion.lateralSpeedMps(), 2)
                        + " yawRate=" + round(yawRate, 3)
                        + " lat=" + Math.round(latCommand)
                        + " yaw=" + Math.round(yawCommand)
                        + " recovery=" + round(recovery.amount(), 2)
                        + " collision=" + (vanillaCollisionWindow ? "vanilla" : "v2")
                        + " impact=" + round(runtime.lastCollisionImpact, 1)
                        + " nativeGear=" + gearName(nativeGear)
                        + " mirrored=" + gearMirrored
                        + " tireGrip=" + round(output.frontTireGripMultiplier(), 2)
                        + "/" + round(output.rearTireGripMultiplier(), 2)
                        + " burnout=" + output.burnout()
                        + " drift=" + output.drifting()
                        + " understeer=" + output.understeering());
            }
            CarPhysicsImprovedMod.updateStatus("active on " + runtime.specification.fullType()
                    + "; gear=" + output.state().gear());
        } catch (Throwable error) {
            fail("controller", error);
        }
    }

    public static void afterVehiclePhysics() {
        if (!CarPhysicsImprovedMod.enabled() || failed) {
            return;
        }
        PzRuntimeAccess current = access();
        if (current == null) {
            return;
        }
        try {
            synchronized (VEHICLES) {
                for (Map.Entry<Object, RuntimeState> entry : VEHICLES.entrySet()) {
                    Object vehicle = entry.getKey();
                    RuntimeState runtime = entry.getValue();
                    if (vehicle == null || runtime == null || runtime.lastOutput == null
                            || !current.hasAuthority(vehicle)
                            || (CarPhysicsImprovedMod.vanillaCollisionResponse()
                                    && runtime.vanillaCollisionSecondsRemaining > 0.0)) {
                        continue;
                    }
                    current.applyWheelSlip(
                            vehicle,
                            runtime.lastOutput.state().drivenWheelSpeedMps(),
                            runtime.lastLongSpeed,
                            runtime.lastOutput.burnout(),
                            runtime.lastOutput.drifting(),
                            runtime.specification.driveLayout(),
                            runtime.lastDelta);
                }
            }
        } catch (Throwable error) {
            fail("wheel-physics", error);
        }
    }

    /** Receives BaseVehicle.crash as an observation only; the vanilla method still executes. */
    public static void onVehicleCrash(Object vehicle, float impactDelta) {
        if (vehicle == null || !CarPhysicsImprovedMod.enabled()
                || !CarPhysicsImprovedMod.vanillaCollisionResponse()) {
            return;
        }
        PzRuntimeAccess current = access();
        if (current == null) {
            return;
        }
        try {
            // Ignore remote/client-observed and dedicated-server crashes. The
            // local physics owner is the only runtime that can submit commands.
            if (!current.hasAuthority(vehicle)) {
                return;
            }
            RuntimeState runtime = VEHICLES.computeIfAbsent(vehicle, ignored -> new RuntimeState());
            runtime.vanillaCollisionSecondsRemaining = Math.max(
                    runtime.vanillaCollisionSecondsRemaining,
                    collisionGraceSeconds(impactDelta));
            runtime.lastCollisionImpact = Math.abs(Double.isFinite(impactDelta) ? impactDelta : 0.0);
        } catch (Throwable error) {
            fail("collision-observer", error);
        }
    }

    static double collisionGraceSeconds(double impactDelta) {
        double severity = clamp(Math.abs(impactDelta), 0.0, 30.0) / 30.0;
        return 0.30 + smoothStep(severity) * 0.30;
    }

    private static PzRuntimeAccess access() {
        if (access != null || accessAttempted) {
            return access;
        }
        synchronized (ACCESS_LOCK) {
            if (!accessAttempted) {
                accessAttempted = true;
                try {
                    access = new PzRuntimeAccess();
                    CarPhysicsImprovedMod.updateStatus("B42.20.4 reflection adapter ready");
                } catch (Throwable error) {
                    fail("ABI initialization", error);
                }
            }
            return access;
        }
    }

    private static void fail(String stage, Throwable error) {
        if (!failed) {
            failed = true;
            String message = stage + " failed: " + error.getClass().getSimpleName() + ": " + error.getMessage();
            CarPhysicsImprovedMod.updateStatus(message + "; V2 disabled, vanilla controller retained");
            System.err.println("[CarPhysicsImproved] " + message);
            error.printStackTrace(System.err);
        }
    }

    private static double wrapAngle(double angle) {
        double value = angle;
        while (value > Math.PI) {
            value -= Math.PI * 2.0;
        }
        while (value < -Math.PI) {
            value += Math.PI * 2.0;
        }
        return value;
    }

    static int manualGearAfterShift(int currentGear, int request, int gearCount) {
        int gear = normalizeManualGear(currentGear, gearCount);
        if (request > 0) {
            if (gear < 0) {
                return 0;
            }
            if (gear == 0) {
                return 1;
            }
            return Math.min(Math.max(1, gearCount), gear + 1);
        }
        if (request < 0) {
            if (gear > 1) {
                return gear - 1;
            }
            if (gear == 1) {
                return 0;
            }
            return -1;
        }
        return gear;
    }

    static StabilityRecovery recoverUnintendedSlide(
            double longitudinalSpeed,
            double lateralSpeed,
            double yawRate,
            double effectiveMass,
            double wheelbase,
            boolean intentionalSlide,
            double recoveryStrength,
            double lateralCommand,
            double yawCommand) {
        double speed = Math.abs(Double.isFinite(longitudinalSpeed) ? longitudinalSpeed : 0.0);
        double side = Double.isFinite(lateralSpeed) ? lateralSpeed : 0.0;
        double yaw = Double.isFinite(yawRate) ? yawRate : 0.0;
        double mass = clamp(effectiveMass, 100.0, 20_000.0);
        double base = clamp(wheelbase, 1.2, 8.0);
        if (intentionalSlide || speed < 3.0) {
            return new StabilityRecovery(lateralCommand, yawCommand, 0.0);
        }

        // This guard is deliberately outside the normal tire model. It stays
        // inactive throughout ordinary steering and only catches motion that
        // has already exceeded a plausible road-car yaw/sideslip envelope.
        // Existing 0.1.9 steering forces therefore remain byte-for-byte intact
        // until an unintended spin is actually developing.
        double safeYawRate = clamp(8.0 / Math.max(speed, 4.0), 0.75, 1.80);
        double safeLateralSpeed = Math.max(2.0, speed * 0.14);
        double yawExcess = clamp(
                (Math.abs(yaw) - safeYawRate) / Math.max(0.60, safeYawRate), 0.0, 1.0);
        double sideExcess = clamp(
                (Math.abs(side) - safeLateralSpeed) / Math.max(1.0, safeLateralSpeed * 0.75), 0.0, 1.0);
        double amount = smoothStep(Math.max(yawExcess, sideExcess))
                * clamp(recoveryStrength, 0.0, 1.50);
        amount = clamp(amount, 0.0, 1.0);
        if (amount <= 0.0) {
            return new StabilityRecovery(lateralCommand, yawCommand, 0.0);
        }

        double normalForce = mass * 9.80665;
        double recoveryLateral = clamp(-side * mass * 1.8, -normalForce * 0.42, normalForce * 0.42);
        // PZ/Bullet's Y torque uses the opposite sign to the heading delta
        // measured by this adapter. Matching the measured yaw sign here applies
        // damping, but only inside this emergency branch; normal steering keeps
        // the proven 0.1.9 convention.
        double recoveryYaw = clamp(
                yaw * mass * base * base * 1.10,
                -normalForce * base * 0.16,
                normalForce * base * 0.16);
        return new StabilityRecovery(
                lerp(lateralCommand, recoveryLateral, amount),
                lerp(yawCommand, recoveryYaw, amount),
                amount);
    }

    static StabilityRecovery recoverUnintendedSlide(
            double longitudinalSpeed,
            double lateralSpeed,
            double yawRate,
            double effectiveMass,
            double wheelbase,
            boolean intentionalSlide,
            double lateralCommand,
            double yawCommand) {
        return recoverUnintendedSlide(
                longitudinalSpeed,
                lateralSpeed,
                yawRate,
                effectiveMass,
                wheelbase,
                intentionalSlide,
                1.0,
                lateralCommand,
                yawCommand);
    }

    private static int normalizeManualGear(int gear, int gearCount) {
        if (gear < 0) {
            return -1;
        }
        if (gear == 0) {
            return 0;
        }
        return Math.min(Math.max(1, gearCount), gear);
    }

    private static String gearName(int gear) {
        if (gear < 0) {
            return "R";
        }
        if (gear == 0) {
            return "N";
        }
        return Integer.toString(gear);
    }

    private static ScriptVehicleData withDriveLayout(
            ScriptVehicleData source,
            dev.carphysicsimproved.v2.physics.DriveLayout layout) {
        return new ScriptVehicleData(
                source.fullType(),
                source.massKg(),
                source.engineForce(),
                source.engineIdleRpm(),
                source.maximumSpeedKph(),
                source.wheelFriction(),
                source.steeringClampRadians(),
                source.centerOfMassForwardMeters(),
                layout,
                source.reverseGearRatio(),
                source.forwardGearRatios(),
                source.wheels());
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double smoothStep(double value) {
        double clamped = clamp(value, 0.0, 1.0);
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private static double lerp(double a, double b, double amount) {
        return a + (b - a) * amount;
    }

    private static double round(double value, int decimals) {
        double scale = Math.pow(10.0, decimals);
        return Math.rint(value * scale) / scale;
    }

    private static final class RuntimeState {
        private Object scriptIdentity;
        private ScriptVehicleData snapshot;
        private VehicleSpec specification;
        private VehicleCondition condition;
        private DynamicsState dynamics;
        private DynamicsOutput lastOutput;
        private int desiredGear = 1;
        private double conditionTimer;
        private double activeAgeSeconds;
        private boolean observerInitialized;
        private double lastLongSpeed;
        private double lastLatSpeed;
        private double lastYawRate;
        private double lastHeading;
        private double lastLongCommand;
        private double lastLatCommand;
        private double lastYawCommand;
        private double vanillaCollisionSecondsRemaining;
        private double lastCollisionImpact;
        private double lastDelta = 1.0 / 60.0;
        private long lastTelemetryNanos;
    }

    record StabilityRecovery(double lateralCommandN, double yawCommandNm, double amount) {
    }
}
