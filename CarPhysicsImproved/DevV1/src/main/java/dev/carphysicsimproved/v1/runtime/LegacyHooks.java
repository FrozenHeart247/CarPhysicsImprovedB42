package dev.carphysicsimproved.v1.runtime;

import dev.carphysicsimproved.v1.physics.LegacyPhysics;
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

    public static void afterControllerUpdate(Object controller) {
        if (controller == null || !CarPhysicsImprovedV1Mod.enabled() || failed) {
            return;
        }
        PzLegacyAccess current = access();
        if (current == null) {
            return;
        }
        try {
            Object vehicle = current.vehicle(controller);
            if (vehicle == null || !current.hasAuthority(vehicle) || !current.hasDriver(vehicle)) {
                return;
            }
            RuntimeState runtime = VEHICLES.computeIfAbsent(vehicle, ignored -> new RuntimeState());
            PzLegacyAccess.Snapshot snapshot = current.snapshot(vehicle);
            if (runtime.scriptIdentity != snapshot.scriptIdentity()) {
                runtime.scriptIdentity = snapshot.scriptIdentity();
                runtime.snapshot = snapshot;
                runtime.physics = new LegacyPhysics.State();
                runtime.physics.engineRpm = Math.max(snapshot.spec().idleRpm(), current.engineRpm(vehicle));
                int nativeGear = current.currentGear(vehicle);
                runtime.physics.gear = nativeGear == 0 ? 1 : nativeGear;
                runtime.conditionTimer = 0.0;
            } else {
                runtime.snapshot = snapshot;
            }

            double dt = current.deltaSeconds();
            runtime.conditionTimer -= dt;
            if (runtime.conditions == null || runtime.conditionTimer <= 0.0) {
                runtime.conditions = current.conditions(vehicle, snapshot);
                runtime.conditionTimer = 0.25;
            }

            PzLegacyAccess.Controls controls = current.controls(controller, vehicle);
            PzLegacyAccess.Motion motion = current.motion(vehicle);
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
                    requestedGear);
            LegacyPhysics.Output output = LegacyPhysics.step(
                    snapshot.spec(), runtime.conditions, CarPhysicsImprovedV1Mod.settings(), input, runtime.physics, dt);
            current.setGear(vehicle, output.gear());
            current.apply(controller, vehicle, output, motion, dt);
            runtime.output = output;
            runtime.lastDelta = dt;
            double skid = Math.max(output.burnoutSpeedKph() * 0.1, current.wheelSkid(vehicle));
            CarPhysicsImprovedV1Mod.updateEffects(current.vehicleId(vehicle), output.burnoutSpeedKph(), skid);

            long now = System.nanoTime();
            if (CarPhysicsImprovedV1Mod.telemetry() && now - runtime.lastTelemetry > 2_000_000_000L) {
                runtime.lastTelemetry = now;
                System.out.println("[CarPhysicsImprovedV1] vehicle=" + snapshot.spec().fullType()
                        + " mode=" + (manual ? "M" : "A")
                        + " gear=" + gearName(output.gear())
                        + " speed=" + round(current.speedKph(vehicle), 1)
                        + " rpm=" + Math.round(output.engineRpm())
                        + " throttle=" + round(output.throttle(), 2)
                        + " force=" + Math.round(output.engineForce())
                        + "/" + Math.round(output.rawDriveForce())
                        + " brake=" + round(output.brakingForce(), 1)
                        + " traction=" + round(output.tireTraction(), 2)
                        + " burnout=" + round(output.burnoutSpeedKph(), 1)
                        + " drag=" + Math.round(output.dragMagnitude())
                        + " steer=" + round(output.steeringRadians(), 3));
            }
            CarPhysicsImprovedV1Mod.updateStatus("active on " + snapshot.spec().fullType()
                    + "; gear=" + gearName(output.gear()));
        } catch (Throwable error) {
            fail("controller", error);
        }
    }

    public static void afterVehiclePhysics() {
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
                    if (vehicle == null || runtime == null || runtime.output == null
                            || !current.hasAuthority(vehicle)) {
                        continue;
                    }
                    current.applyBurnoutVisual(
                            vehicle, runtime.output.burnoutSpeedKph(), runtime.lastDelta);
                }
            }
        } catch (Throwable error) {
            fail("wheel visual", error);
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

    private static PzLegacyAccess access() {
        if (access != null || accessAttempted) {
            return access;
        }
        synchronized (ACCESS_LOCK) {
            if (!accessAttempted) {
                accessAttempted = true;
                try {
                    access = new PzLegacyAccess();
                    CarPhysicsImprovedV1Mod.updateStatus("B42.20.4 legacy adapter ready");
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

    private static final class RuntimeState {
        private Object scriptIdentity;
        private PzLegacyAccess.Snapshot snapshot;
        private LegacyPhysics.Conditions conditions;
        private LegacyPhysics.State physics = new LegacyPhysics.State();
        private LegacyPhysics.Output output;
        private double conditionTimer;
        private double lastDelta = 1.0 / 60.0;
        private long lastTelemetry;
    }
}
