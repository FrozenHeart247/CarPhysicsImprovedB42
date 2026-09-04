package dev.carphysicsimproved.v1.runtime;

import dev.carphysicsimproved.v1.physics.LegacyPhysics;
import dev.carphysicsimproved.v1.physics.LegacyKeyDrift;
import dev.carphysicsimproved.v1.physics.LegacyDriverTraits;
import dev.carphysicsimproved.v1.physics.LegacySlideDynamics;
import dev.carphysicsimproved.v1.physics.LegacyTireCondition;
import dev.carphysicsimproved.v1.physics.LegacyTireEffects;
import pzmod.carphysicsimproved.v1.CarPhysicsImprovedV1Mod;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** ZombieBuddy hook targets. Vanilla runs first; V1 submits the final control request. */
public final class LegacyHooks {
    private static final Map<Object, RuntimeState> VEHICLES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Object ACCESS_LOCK = new Object();
    private static volatile PzLegacyAccess access;
    private static volatile boolean accessAttempted;
    private static volatile boolean failed;

    private LegacyHooks() {
    }

    public static void afterControllerControls(Object controller) {
        if (controller == null || failed || !CarPhysicsImprovedV1Mod.enabled()) {
            return;
        }
        PzLegacyAccess current = access();
        if (current == null) {
            return;
        }
        try {
            Object vehicle = current.vehicle(controller);
            if (vehicle != null && current.hasAuthority(vehicle) && current.driftKeyHeld(vehicle)) {
                current.suppressDriftKeyBrake(controller);
            }
        } catch (Throwable error) {
            fail("drift key controls", error);
        }
    }

    public static void afterControllerUpdate(Object controller) {
        if (controller == null || failed) {
            return;
        }
        PzLegacyAccess current = access();
        if (current == null) {
            return;
        }
        try {
            Object vehicle = current.vehicle(controller);
            if (vehicle == null || !current.hasAuthority(vehicle)) {
                return;
            }
            if (!CarPhysicsImprovedV1Mod.enabled() || !current.hasDriver(vehicle)) {
                LegacyAxleDriftHooks.stop(vehicle);
                current.restoreWheelFriction(vehicle);
                return;
            }
            RuntimeState runtime = VEHICLES.computeIfAbsent(vehicle, ignored -> new RuntimeState());
            PzLegacyAccess.Snapshot snapshot = current.snapshot(vehicle);
            if (runtime.scriptIdentity != snapshot.scriptIdentity()) {
                LegacyAxleDriftHooks.stop(vehicle);
                runtime.scriptIdentity = snapshot.scriptIdentity();
                runtime.snapshot = snapshot;
                runtime.physics = new LegacyPhysics.State();
                runtime.physics.engineRpm = Math.max(snapshot.spec().idleRpm(), current.engineRpm(vehicle));
                int nativeGear = current.currentGear(vehicle);
                runtime.physics.gear = nativeGear == 0 ? 1 : nativeGear;
                runtime.physics.lastStepGear = runtime.physics.gear;
                runtime.slide = new LegacySlideDynamics.State();
                runtime.tireEffects = new LegacyTireEffects.State();
                runtime.conditionTimer = 0.0;
                runtime.activeAgeSeconds = 0.0;
                runtime.observerInitialized = false;
                runtime.keyDriftMode = false;
            } else {
                runtime.snapshot = snapshot;
            }

            double dt = current.deltaSeconds();
            runtime.activeAgeSeconds += dt;
            runtime.collisionCooldownSeconds = Math.max(0.0, runtime.collisionCooldownSeconds - dt);
            runtime.impactDisturbanceSeconds = Math.max(0.0, runtime.impactDisturbanceSeconds - dt);
            runtime.conditionTimer -= dt;
            if (runtime.conditions == null || runtime.conditionTimer <= 0.0) {
                runtime.conditions = current.conditions(vehicle, snapshot);
                runtime.conditionTimer = 0.25;
            }

            PzLegacyAccess.Controls controls = current.controls(controller, vehicle);
            PzLegacyAccess.Motion motion = current.motion(vehicle);
            runtime.lastVerticalSpeed = motion.velocityY();
            double yawRate = runtime.observerInitialized
                    ? wrapAngle(motion.headingRadians() - runtime.lastHeadingRadians) / dt
                    : 0.0;
            boolean manual = CarPhysicsImprovedV1Mod.manualTransmission();
            if (manual) {
                int shift = CarPhysicsImprovedV1Mod.consumeShiftRequest(current.vehicleId(vehicle));
                runtime.physics.gear = manualGearAfterShift(
                        runtime.physics.gear, shift, snapshot.spec().forwardRatios().length);
            }
            int requestedGear = manual ? runtime.physics.gear : current.currentGear(vehicle);
            boolean forward = controls.forward();
            boolean backward = controls.backward();
            boolean serviceBrake = manual && backward;
            double analogThrottle = forward || (!manual && backward) ? controls.throttle() : 0.0;
            LegacySlideDynamics.Tuning slideTuning = CarPhysicsImprovedV1Mod.slideTuning();
            boolean slideForcesSafe = runtime.activeAgeSeconds >= 0.75
                    && runtime.collisionCooldownSeconds <= 0.0
                    && Math.abs(motion.velocityY()) <= 1.25;
            boolean driftKeyHeld = current.driftKeyHeld(vehicle);
            LegacyKeyDrift.Tuning keyTuning = CarPhysicsImprovedV1Mod.keyDriftTuning();
            boolean keyDriftMode = slideForcesSafe && driftKeyHeld && !controls.handbrake()
                    && slideTuning.enabled() && slideTuning.driftIntensity() > 0;
            double nativeSpeed = current.speedKph(vehicle);
            boolean keyDriftActive = LegacyKeyDrift.active(keyDriftMode, nativeSpeed, controls.steering(), keyTuning);
            double previousSteering = runtime.physics.steering;
            double tireHardwareGrip = LegacyTireCondition.hardwareGrip(
                    runtime.conditions.longitudinal().tirePressure(),
                    runtime.conditions.longitudinal().tireConditionGrip());
            LegacyDriverTraits.Modifiers driverModifiers = current.driverModifiers(vehicle);
            LegacyPhysics.Input input = new LegacyPhysics.Input(
                    motion.longitudinalSpeedMps(),
                    current.engineRunning(vehicle),
                    forward,
                    !manual && backward,
                    serviceBrake,
                    controls.handbrake(),
                    controls.steering(),
                    analogThrottle,
                    manual,
                    requestedGear,
                    slideTuning.clutchKickEnabled(),
                    driverModifiers);
            LegacyPhysics.Output output = LegacyPhysics.step(
                    snapshot.spec(), runtime.conditions.longitudinal(),
                    CarPhysicsImprovedV1Mod.settings(), input, runtime.physics, dt);
            if (keyDriftMode) {
                // Replace only this tick's steering; do not recompute drivetrain
                // or use the already-updated angle as a second steering step.
                runtime.physics.steering = LegacyKeyDrift.steering(previousSteering, controls.steering(),
                        nativeSpeed, current.speedSteeringClamp(vehicle, nativeSpeed), tireHardwareGrip,
                        current.realDeltaSeconds(), CarPhysicsImprovedV1Mod.settings(), keyTuning);
                output = LegacyKeyDrift.withSteering(output, runtime.physics.steering);
            }

            PzLegacyAccess.AxleSkid nativeAxleSkid = current.axleSkid(vehicle);
            double tractionLimit = snapshot.spec().massKg() * 2.0 * output.tireTraction()
                    * CarPhysicsImprovedV1Mod.settings().accelerationTraction();
            boolean clutchKickStarted = output.clutchKickIntensity() >= 0.05
                    && runtime.lastClutchKickIntensity < 0.05;
            if (CarPhysicsImprovedV1Mod.telemetry() && clutchKickStarted) {
                System.out.println("[CarPhysicsImprovedV1] clutch-kick vehicle=" + snapshot.spec().fullType()
                        + " gear=" + gearName(output.gear())
                        + " rpm=" + Math.round(output.engineRpm())
                        + " intensity=" + round(output.clutchKickIntensity(), 2)
                        + " force=" + Math.round(output.engineForce())
                        + "/" + Math.round(output.rawDriveForce())
                        + " limit=" + Math.round(tractionLimit)
                        + " wheelspin=" + round(output.burnoutSpeedKph(), 1));
            }
            runtime.lastClutchKickIntensity = output.clutchKickIntensity();
            LegacySlideDynamics.Output slideOutput;
            if (keyDriftMode || runtime.keyDriftMode) {
                // The reference key path has no force history or slide-entry
                // delay. Never let old recovery/power commands compete with it.
                runtime.slide.reset();
            }
            double referenceMassScale = keyDriftActive ? current.referenceDriftMassScale(vehicle) : 1;
            double keyGrip = LegacyKeyDrift.gripMultiplier(slideTuning.driftIntensity(), keyTuning);
            if (keyDriftMode) {
                double keyTorque = LegacyKeyDrift.torque(keyDriftActive, controls.steering(),
                        current.nativeMass(vehicle), referenceMassScale, slideTuning.driftIntensity(), keyTuning);
                slideOutput = LegacyKeyDrift.observation(keyDriftActive, keyTorque, keyTuning.rotation(), keyGrip,
                        motion.longitudinalSpeedMps(), motion.lateralSpeedMps(), nativeAxleSkid.rear());
            } else if (slideForcesSafe) {
                slideOutput = LegacySlideDynamics.step(
                        snapshot.slideSpec(),
                        runtime.conditions.lateral(),
                        slideTuning,
                        new LegacySlideDynamics.Input(
                                motion.longitudinalSpeedMps(),
                                motion.lateralSpeedMps(),
                                yawRate,
                                output.steeringRadians(),
                                controls.steering(),
                                output.throttle(),
                                clamp(output.brakingForce() / Math.max(0.1, snapshot.spec().brakingForce()), 0.0, 1.0),
                                controls.handbrake() ? 1.0 : 0.0,
                                output.gear(),
                                output.rawDriveForce(),
                                tractionLimit,
                                output.clutchKickIntensity(),
                                nativeAxleSkid.rear(),
                                runtime.impactDisturbanceSeconds > 0.0,
                                false),
                        runtime.slide,
                        dt);
            } else {
                runtime.slide.reset();
                slideOutput = LegacySlideDynamics.Output.inactive();
            }
            LegacyAxleDriftHooks.stop(vehicle); // Retired model can never run alongside reference key drift.
            double tireWheelFrictionScale = LegacyTireCondition.nativeFrictionScale(tireHardwareGrip);
            double appliedWheelFrictionScale = Math.min(slideOutput.wheelFrictionScale(),
                    Math.min(runtime.conditions.terrain().nativeWheelFrictionScale(), tireWheelFrictionScale));
            if (keyDriftMode) {
                // Reference order: torque, friction submission, then drive controls.
                // Only this force owner may submit commands during dedicated drift.
                current.applySlideForces(vehicle, motion, slideOutput);
            }
            if (keyDriftActive) {
                current.applyKeyDriftFriction(vehicle, output.tireTraction(), keyGrip);
            } else {
                current.applyWheelFrictionScale(vehicle, appliedWheelFrictionScale);
            }
            double driftSteeringMultiplier = slideOutput.intentionalSlide()
                    && slideOutput.cause() != LegacySlideDynamics.Cause.DRIFT_KEY
                    ? slideTuning.driftSteeringMultiplier() : 1.0;
            double appliedSteering = clamp(
                    output.steeringRadians() * driftSteeringMultiplier,
                    -snapshot.spec().steeringClampRadians(),
                    snapshot.spec().steeringClampRadians());
            current.setGear(vehicle, output.gear());
            current.apply(controller, vehicle, output, motion, appliedSteering, dt);
            if (slideForcesSafe && !keyDriftMode) {
                current.applySlideForces(vehicle, motion, slideOutput);
            }
            runtime.keyDriftMode = keyDriftMode;
            if (keyDriftActive && !runtime.keyDriftAnnounced) {
                runtime.keyDriftAnnounced = true;
                System.out.println("[CarPhysicsImprovedV1] Dedicated-key drift active on "
                        + snapshot.spec().fullType() + "; yaw=" + Math.round(slideOutput.bulletYawTorque())
                        + ", refMassScale=" + round(referenceMassScale, 3)
                        + ", grip=" + round(keyGrip, 2) + "; native mass unchanged.");
            }
            runtime.output = output;
            runtime.slideOutput = slideOutput;
            runtime.lastDelta = dt;
            runtime.lastHeadingRadians = motion.headingRadians();
            runtime.observerInitialized = true;
            double serviceBrakeAmount = clamp(
                    output.brakingForce() / Math.max(0.1, snapshot.spec().brakingForce()), 0.0, 1.0);
            boolean sliding = slideOutput.mode() == LegacySlideDynamics.Mode.SLIDE
                    || slideOutput.mode() == LegacySlideDynamics.Mode.CONTROLLED;
            LegacyTireEffects.Output tireEffects = LegacyTireEffects.step(
                    new LegacyTireEffects.Input(
                            output.burnoutSpeedKph(),
                            motion.longitudinalSpeedMps(),
                            motion.lateralSpeedMps(),
                            slideOutput.sideSlipAngleRadians(),
                            slideOutput.slideBlend(),
                            slideOutput.intentionalSlide(),
                            sliding,
                            serviceBrakeAmount,
                            controls.handbrake() ? 1.0 : 0.0,
                            nativeAxleSkid.rear()),
                    runtime.tireEffects,
                    dt);
            CarPhysicsImprovedV1Mod.updateEffects(
                    current.vehicleId(vehicle), output.burnoutSpeedKph(), tireEffects.intensity());

            long now = System.nanoTime();
            if (CarPhysicsImprovedV1Mod.telemetry() && now - runtime.lastTelemetry > 2_000_000_000L) {
                runtime.lastTelemetry = now;
                System.out.println("[CarPhysicsImprovedV1] vehicle=" + snapshot.spec().fullType()
                        + " mode=" + (manual ? "M" : "A")
                        + " driver=" + driverModifiers.telemetryName()
                        + " gear=" + gearName(output.gear())
                        + " speed=" + round(current.speedKph(vehicle), 1)
                        + " rpm=" + Math.round(output.engineRpm())
                        + " throttle=" + round(output.throttle(), 2)
                        + " force=" + Math.round(output.engineForce())
                        + "/" + Math.round(output.rawDriveForce())
                        + " brake=" + round(output.brakingForce(), 1)
                        + " traction=" + round(output.tireTraction(), 2)
                        + " terrain=" + runtime.conditions.terrain().profile()
                        + " envGrip=" + round(runtime.conditions.terrain().surfaceGrip(), 2)
                        + " envDrag=" + round(runtime.conditions.terrain().offroadResistanceScale(), 2)
                        + " envWheelGrip=" + round(runtime.conditions.terrain().nativeWheelFrictionScale(), 2)
                        + " rain=" + round(runtime.conditions.terrain().rainIntensity(), 2)
                        + " snow=" + round(runtime.conditions.terrain().snowIntensity(), 2)
                        + " offroad=" + runtime.conditions.terrain().offroad()
                        + " burnout=" + round(output.burnoutSpeedKph(), 1)
                        + " drag=" + Math.round(output.dragMagnitude())
                        + " steer=" + round(output.steeringRadians(), 3)
                        + " steerApplied=" + round(appliedSteering, 3)
                        + " driftKey=" + driftKeyHeld
                        + " driftModel=" + (keyDriftMode ? "key-drift" : "standard")
                        + " refMassScale=" + round(referenceMassScale, 3)
                        + " keyGrip=" + (keyDriftActive ? round(keyGrip, 2) : 1.0)
                        + " side=" + round(motion.lateralSpeedMps(), 2)
                        + " beta=" + round(Math.toDegrees(slideOutput.sideSlipAngleRadians()), 1)
                        + " rot=" + Math.round(slideOutput.driftRotation())
                        + " yaw=" + round(yawRate, 3)
                        + "/" + round(slideOutput.expectedYawRateRadiansPerSecond(), 3)
                        + " grip=" + round(slideOutput.frontGripUse(), 2)
                        + "/" + round(slideOutput.rearGripUse(), 2)
                        + " slide=" + slideOutput.mode()
                        + ":" + slideOutput.cause()
                        + " active=" + slideOutput.intentionalSlide()
                        + " blend=" + round(slideOutput.slideBlend(), 2)
                        + " wheelGrip=" + round(appliedWheelFrictionScale, 2)
                        + " scriptFriction=" + round(current.wheelFriction(vehicle), 3)
                        + " tireHardware=" + round(tireHardwareGrip, 2)
                        + " lat=" + Math.round(slideOutput.lateralForce())
                        + " yawCmd=" + Math.round(slideOutput.bulletYawTorque())
                        + " clutch=" + round(output.clutchKickIntensity(), 2)
                        + " tireFx=" + round(tireEffects.intensity(), 2)
                        + " tracks={" + LegacyTireTrackRenderer.status() + "}"
                        + " calibrated=" + slideOutput.yawSignCalibrated());
            }
            CarPhysicsImprovedV1Mod.updateStatus("active on " + snapshot.spec().fullType()
                    + "; gear=" + gearName(output.gear()));
        } catch (Throwable error) {
            fail("controller", error);
        }
    }

    public static void afterVehiclePhysics() {
        LegacyAxleDriftHooks.cleanup();
        if (!CarPhysicsImprovedV1Mod.enabled() || failed) {
            return;
        }
        PzLegacyAccess current = access();
        if (current == null) {
            return;
        }
        try {
            synchronized (VEHICLES) {
                for (Map.Entry<Object, RuntimeState> entry : VEHICLES.entrySet()) {
                    Object vehicle = entry.getKey();
                    RuntimeState runtime = entry.getValue();
                    if (vehicle != null && runtime != null && runtime.keyDriftMode
                            && (!current.hasAuthority(vehicle) || !current.hasDriver(vehicle)
                                    || !current.driftKeyHeld(vehicle))) {
                        current.restoreWheelFriction(vehicle);
                        runtime.keyDriftMode = false;
                        runtime.slide.reset();
                    }
                    if (vehicle == null || runtime == null || runtime.output == null
                            || !current.hasAuthority(vehicle)) {
                        continue;
                    }
                    current.applyBurnoutVisual(
                            vehicle, runtime.output.burnoutSpeedKph(), runtime.lastDelta);
                    if (runtime.slideOutput != null) {
                        current.applySlideVisual(vehicle, runtime.slideOutput.slideBlend());
                    }
                }
            }
        } catch (Throwable error) {
            fail("wheel visual", error);
        }
    }

    public static void releaseKeyDrifts() {
        PzLegacyAccess current = access;
        if (current == null) return;
        try {
            synchronized (VEHICLES) {
                for (Map.Entry<Object, RuntimeState> entry : VEHICLES.entrySet()) {
                    RuntimeState state = entry.getValue();
                    if (entry.getKey() != null && state.keyDriftMode) {
                        current.restoreWheelFriction(entry.getKey());
                        state.keyDriftMode = false;
                        state.slide.reset();
                    }
                }
            }
        } catch (Throwable error) {
            fail("key drift restore", error);
        }
    }

    static int manualGearAfterShift(int currentGear, int request, int gearCount) {
        int gear = Math.max(-1, Math.min(Math.max(1, gearCount), currentGear));
        if (request > 0) {
            return gear < 0 ? 0 : gear == 0 ? 1 : Math.min(gearCount, gear + 1);
        }
        if (request < 0) {
            return gear > 1 ? gear - 1 : gear == 1 ? 0 : -1;
        }
        return gear;
    }

    /** Observes the native collision; V1 forces remain suspended during the impact. */
    public static void onVehicleCrash(Object vehicle, float impactDelta) {
        if (vehicle == null || !CarPhysicsImprovedV1Mod.enabled()) {
            return;
        }
        RuntimeState runtime = VEHICLES.computeIfAbsent(vehicle, ignored -> new RuntimeState());
        double severity = clamp(Math.abs(Double.isFinite(impactDelta) ? impactDelta : 0.0), 0.0, 30.0) / 30.0;
        runtime.collisionCooldownSeconds = Math.max(runtime.collisionCooldownSeconds,
                0.35 + severity * 0.25);
        runtime.impactDisturbanceSeconds = Math.max(runtime.impactDisturbanceSeconds,
                1.10 + severity * 0.40);
        runtime.slide.reset();
        runtime.observerInitialized = false;
        LegacyAxleDriftHooks.stop(vehicle);
    }

    static boolean canKeepAxleDrift(Object vehicle) throws ReflectiveOperationException {
        if (failed || access == null || !CarPhysicsImprovedV1Mod.enabled()) return false;
        RuntimeState runtime = VEHICLES.get(vehicle);
        return runtime != null && runtime.activeAgeSeconds >= 0.75
                && runtime.collisionCooldownSeconds <= 0.0
                && Double.isFinite(runtime.lastVerticalSpeed) && Math.abs(runtime.lastVerticalSpeed) <= 1.25
                && access.hasAuthority(vehicle) && access.engineRunning(vehicle) && access.driftKeyHeld(vehicle);
    }

    private static PzLegacyAccess access() {
        if (access != null || accessAttempted) {
            return access;
        }
        synchronized (ACCESS_LOCK) {
            if (!accessAttempted) {
                accessAttempted = true;
                try {
                    access = new PzLegacyAccess();
                    CarPhysicsImprovedV1Mod.updateStatus("B42.20.4 physics adapter ready");
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
            LegacyAxleDriftHooks.restoreAll();
            try {
                if (access != null) {
                    access.restoreAllWheelFriction();
                }
            } catch (Throwable restoreError) {
                System.err.println("[CarPhysicsImprovedV1] wheel-friction restore failed: "
                        + restoreError.getClass().getSimpleName() + ": " + restoreError.getMessage());
            }
            String message = stage + " failed: " + error.getClass().getSimpleName() + ": " + error.getMessage();
            CarPhysicsImprovedV1Mod.updateStatus(message + "; V1 disabled, vanilla controller retained");
            System.err.println("[CarPhysicsImprovedV1] " + message);
            error.printStackTrace(System.err);
        }
    }

    private static String gearName(int gear) {
        return gear < 0 ? "R" : gear == 0 ? "N" : Integer.toString(gear);
    }

    private static double round(double value, int decimals) {
        double scale = Math.pow(10.0, decimals);
        return Math.rint(value * scale) / scale;
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

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class RuntimeState {
        private Object scriptIdentity;
        private PzLegacyAccess.Snapshot snapshot;
        private PzLegacyAccess.ConditionSnapshot conditions;
        private LegacyPhysics.State physics = new LegacyPhysics.State();
        private LegacySlideDynamics.State slide = new LegacySlideDynamics.State();
        private LegacyTireEffects.State tireEffects = new LegacyTireEffects.State();
        private LegacyPhysics.Output output;
        private LegacySlideDynamics.Output slideOutput;
        private double conditionTimer;
        private double lastDelta = 1.0 / 60.0;
        private double activeAgeSeconds;
        private double collisionCooldownSeconds;
        private double impactDisturbanceSeconds;
        private double lastClutchKickIntensity;
        private double lastHeadingRadians;
        private double lastVerticalSpeed;
        private boolean observerInitialized;
        private boolean keyDriftMode;
        private boolean keyDriftAnnounced;
        private long lastTelemetry;
    }
}
