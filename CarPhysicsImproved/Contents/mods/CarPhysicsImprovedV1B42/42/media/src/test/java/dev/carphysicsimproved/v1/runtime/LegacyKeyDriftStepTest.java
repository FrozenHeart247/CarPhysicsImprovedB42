package dev.carphysicsimproved.v1.runtime;

import dev.carphysicsimproved.v1.physics.LegacyKeyDrift;
import dev.carphysicsimproved.v1.physics.LegacySlideDynamics;
import pzmod.carphysicsimproved.v1.CarPhysicsImprovedV1Mod;

/** Real fixed-step dispatch with a mock Bullet sink; not a native driving simulation. */
public final class LegacyKeyDriftStepTest {
    private static final double TORQUE = 4704;
    private LegacyKeyDriftStepTest() { }

    public static void main(String[] args) throws Exception {
        PzLegacyAccess access = fixture();
        LegacyMultiplayerRuntimeTest.field(LegacyHooks.class, "access").set(null, access);
        cadence(access);
        releaseAndCountersteer(access);
        safetyAndOwnership(access);
        normalGripIsolation(access);
        invalidRequests();
        check(!(Boolean)LegacyMultiplayerRuntimeTest.field(LegacyHooks.class, "failed").get(null),
                "No swallowed fixed-step failure");
        LegacyHooks.releaseVehicleSessions();
        System.out.println("LegacyKeyDriftStepTest: 20-240 FPS -> identical 100 Hz yaw commands, "
                + "release/countersteer/expiry, safety, SP/MP ownership and normal-grip isolation passed; mock Bullet");
    }

    private static PzLegacyAccess fixture() throws Exception {
        PzLegacyAccess access = LegacyMultiplayerRuntimeTest.fixtureAccess();
        LegacyMultiplayerRuntimeTest.replace(access, "vehicleKeyboardControlled", Car.class.getMethod("isKeyboardControlled"));
        LegacyMultiplayerRuntimeTest.replace(access, "vehicleNativeMass", Car.class.getMethod("getNativeMass"));
        LegacyMultiplayerRuntimeTest.replace(access, "vehicleVelocity", Car.class.getField("velocity"));
        LegacyMultiplayerRuntimeTest.replace(access, "vectorY", Vector.class.getField("y"));
        LegacyMultiplayerRuntimeTest.replace(access, "keyboardDown", Keys.class.getMethod("isDown", int.class));
        LegacyMultiplayerRuntimeTest.replace(access, "bulletTorque",
                Native.class.getMethod("torque", int.class, float.class, float.class, float.class));
        return access;
    }

    private static void reset() {
        LegacyHooks.releaseVehicleSessions();
        LegacyMultiplayerRuntimeTest.Flags.client = false;
        LegacyMultiplayerRuntimeTest.Flags.server = false;
        CarPhysicsImprovedV1Mod.setEnabled(true);
        CarPhysicsImprovedV1Mod.setManualTransmission(false);
        CarPhysicsImprovedV1Mod.configureDriftKey(42, false, false, false);
        CarPhysicsImprovedV1Mod.configureKeyDrift(2000, .35, 1.5, 20);
        Keys.held = true;
        Native.calls = 0;
        Native.sum = 0;
        Native.last = 0;
    }

    private static LegacyHooks.RuntimeState arm(PzLegacyAccess access, Car car, double direction, long now)
            throws Exception {
        LegacyHooks.RuntimeState state = LegacyHooks.prepareSession(access, car);
        check(state != null, "Expected driving session");
        LegacyMultiplayerRuntimeTest.field(state.getClass(), "activeAgeSeconds").setDouble(state, 1);
        LegacyMultiplayerRuntimeTest.field(state.getClass(), "keyDriftMode").setBoolean(state, true);
        double torque = access.keyDriftTorque(car, true, direction, 1, LegacyKeyDrift.Tuning.defaults());
        LegacyMultiplayerRuntimeTest.field(state.getClass(), "slideOutput").set(state,
                LegacyKeyDrift.observation(true, torque, 2000, .35, 20, 3, .5));
        // Same publication path as the controller; no native call is allowed here.
        state.keyDriftCommand.publish(torque, now);
        access.applyKeyDriftFriction(car, 1, .35);
        return state;
    }

    private static void cadence(PzLegacyAccess access) throws Exception {
        for (boolean client : new boolean[]{false, true}) {
            for (int fps : new int[]{20, 30, 40, 60, 75, 100, 144, 240}) {
                reset();
                LegacyMultiplayerRuntimeTest.Flags.client = client;
                Car car = new Car();
                int controllerFrame = 0;
                int writes = car.writes;
                int controls = LegacyMultiplayerRuntimeTest.Native.controls;
                for (int step = 1; step <= 300; step++) {
                    long physicsTime = step * 10_000_000L;
                    while (Math.round(controllerFrame * 1_000_000_000.0 / fps) <= physicsTime) {
                        int before = Native.calls;
                        arm(access, car, 1, Math.round(controllerFrame * 1_000_000_000.0 / fps));
                        check(Native.calls == before, "Controller publication cannot submit extra yaw");
                        controllerFrame++;
                    }
                    LegacyHooks.afterVehiclePhysics(physicsTime);
                    check(Native.calls == step, "Exactly one yaw command per native step at " + fps + " FPS");
                }
                near(Native.sum * .01, TORQUE * 3, "Same three-second angular impulse budget at " + fps + " FPS");
                check(car.writes == writes && controls == LegacyMultiplayerRuntimeTest.Native.controls,
                        "Fixed-step yaw cannot write RPM, transmission, steering or drive controls");
                near(car.script.wheelFriction, .63, "Existing drift grip preserved");
            }
        }
    }

    private static void releaseAndCountersteer(PzLegacyAccess access) throws Exception {
        reset();
        Car car = new Car();
        LegacyHooks.RuntimeState state = arm(access, car, 1, 0);
        LegacyHooks.afterVehiclePhysics(10_000_000L);
        near(Native.last, TORQUE, "Initial turn");
        arm(access, car, -1, 11_000_000L);
        LegacyHooks.afterVehiclePhysics(20_000_000L);
        near(Native.last, -TORQUE, "Countersteer replaces rather than accumulates old torque");
        Keys.held = false;
        LegacyHooks.afterVehiclePhysics(30_000_000L);
        check(Native.calls == 2, "Release stops before the next physics step");
        near(car.script.wheelFriction, 1.8, "Release restores grip");
        near(state.keyDriftCommand.torqueForPhysicsStep(30_000_000L), 0, "Release clears cached intent");
        Keys.held = true;
        LegacyHooks.afterVehiclePhysics(40_000_000L);
        check(Native.calls == 2, "Re-press cannot resurrect old intent without controller update");
        arm(access, car, 1, 41_000_000L);
        LegacyHooks.afterVehiclePhysics(50_000_000L);
        check(Native.calls == 3, "Fresh input resumes normally");
        LegacyHooks.afterVehiclePhysics(41_000_000L + LegacyKeyDriftCommand.MAX_AGE_NANOS + 1);
        check(Native.calls == 3, "Paused/stalled controller request expires");
        near(car.script.wheelFriction, 1.8, "Expired active drift restores grip");
        arm(access, car, -1, 400_000_000L);
        LegacyHooks.afterVehiclePhysics(410_000_000L);
        near(Native.last, -TORQUE, "Fresh input after expiry works");
    }

    private static void safetyAndOwnership(PzLegacyAccess access) throws Exception {
        for (int reason = 0; reason < 16; reason++) {
            reset();
            LegacyMultiplayerRuntimeTest.Flags.client = true;
            Car car = new Car();
            var originalScript = car.script;
            LegacyHooks.RuntimeState state = arm(access, car, 1, 0);
            switch (reason) {
                case 0 -> car.speed = 20;
                case 1 -> car.speed = Double.NaN;
                case 2 -> car.velocity.y = 2;
                case 3 -> car.velocity.y = Float.NaN;
                case 4 -> LegacyMultiplayerRuntimeTest.field(state.getClass(), "collisionCooldownSeconds").setDouble(state, .5);
                case 5 -> car.local = false;
                case 6 -> car.driver = null;
                case 7 -> car.driver.dead = true;
                case 8 -> car.ownerId++;
                case 9 -> car.script = new LegacyMultiplayerRuntimeTest.Script();
                case 10 -> CarPhysicsImprovedV1Mod.setEnabled(false);
                case 11 -> LegacyMultiplayerRuntimeTest.Flags.server = true;
                case 12 -> { car.authorization = "LocalCollide"; car.driver.local = false; }
                case 13 -> car.keyboard = false;
                case 14 -> LegacyMultiplayerRuntimeTest.field(state.getClass(), "activeAgeSeconds").setDouble(state, .5);
                case 15 -> CarPhysicsImprovedV1Mod.configureKeyDrift(0, .35, 1.5, 20);
                default -> throw new AssertionError();
            }
            LegacyHooks.afterVehiclePhysics(10_000_000L);
            check(Native.calls == 0, "Unsafe or unowned command rejected, reason " + reason);
            near(originalScript.wheelFriction, 1.8, "Old grip restored, reason " + reason);
            near(state.keyDriftCommand.torqueForPhysicsStep(10_000_000L), 0, "Queue cleared, reason " + reason);
        }
        reset();
        LegacyMultiplayerRuntimeTest.Flags.client = true;
        Car car = new Car();
        LegacyHooks.RuntimeState state = arm(access, car, 1, 0);
        car.ownerId++;
        LegacyHooks.onVehicleAuthorizationChanged(car);
        car.ownerId--;
        LegacyHooks.onVehicleAuthorizationChanged(car);
        LegacyHooks.afterVehiclePhysics(10_000_000L);
        check(Native.calls == 0, "Ownership round trip between steps cannot replay a command");
        near(state.keyDriftCommand.torqueForPhysicsStep(10_000_000L), 0, "Immediate ownership callback clears queue");
    }

    private static void normalGripIsolation(PzLegacyAccess access) throws Exception {
        reset();
        Car car = new Car();
        LegacyHooks.RuntimeState state = LegacyHooks.prepareSession(access, car);
        access.applyWheelFrictionScale(car, .5);
        state.keyDriftCommand.publish(TORQUE, 0);
        LegacyHooks.afterVehiclePhysics(10_000_000L);
        check(Native.calls == 0, "Non-key mode cannot replay a request");
        near(car.script.wheelFriction, .9, "Normal tire/weather grip is not restored by yaw dispatch");
        LegacyMultiplayerRuntimeTest.field(state.getClass(), "keyDriftMode").setBoolean(state, true);
        LegacyMultiplayerRuntimeTest.field(state.getClass(), "slideOutput").set(state,
                LegacySlideDynamics.Output.inactive());
        car.speed = 10;
        LegacyHooks.afterVehiclePhysics(20_000_000L);
        near(car.script.wheelFriction, .9, "Held key below entry threshold does not overwrite normal grip");
        check(Native.calls == 0, "Below-threshold steering-only mode has no yaw");
    }

    private static void invalidRequests() {
        LegacyKeyDriftCommand command = new LegacyKeyDriftCommand();
        for (double value : new double[]{0, Double.NaN, Double.POSITIVE_INFINITY}) {
            command.publish(value, 10);
            near(command.torqueForPhysicsStep(20), 0, "Invalid/zero request cannot reach Bullet");
        }
        command.publish(TORQUE, 100);
        near(command.torqueForPhysicsStep(99), 0, "Future timestamp fails closed");
    }

    private static void near(double actual, double expected, String message) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) < 1e-5, message + ": " + actual);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    public static final class Car extends LegacyMultiplayerRuntimeTest.Car {
        public Vector velocity = new Vector();
        public boolean keyboard = true;
        public Car() { speed = 70; }
        public boolean isKeyboardControlled() { return keyboard; }
        public double getNativeMass() { return 1200; }
    }
    public static final class Vector { public float y; }
    public static final class Keys {
        public static boolean held;
        public static boolean isDown(int key) { return held && key == 42; }
    }
    public static final class Native {
        public static int calls;
        public static double sum, last;
        public static void torque(int id, float x, float y, float z) {
            check(x == 0 && z == 0, "Yaw must not tilt the car");
            calls++;
            last = y;
            sum += y;
        }
    }
}
