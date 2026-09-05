package dev.carphysicsimproved.v1.runtime;

import dev.carphysicsimproved.v1.physics.LegacyPhysics;
import pzmod.carphysicsimproved.v1.CarPhysicsImprovedV1Mod;
import java.lang.reflect.Field;

/** Real CPI adapter/session code with native fields and Bullet calls replaced by fixtures. */
public final class LegacyMultiplayerRuntimeTest {
    private LegacyMultiplayerRuntimeTest() { }

    public static void main(String[] args) throws Exception {
        PzLegacyAccess access = fixtureAccess(); // Resolves installed game ABI first.
        field(LegacyHooks.class, "access").set(null, access);
        CarPhysicsImprovedV1Mod.setEnabled(true);
        speedLimit(access);
        sessions(access);
        check(!(Boolean)field(LegacyHooks.class, "failed").get(null), "No swallowed adapter failure");
        System.out.println("LegacyMultiplayerRuntimeTest: final force gate, SP/MP/server ownership, "
                + "pre-vanilla RPM/gear, owner transitions and friction/effect cleanup passed (mock native runtime)");
    }

    static PzLegacyAccess fixtureAccess() throws Exception {
        PzLegacyAccess a = new PzLegacyAccess();
        replace(a, "gameClientClient", Flags.class.getField("client"));
        replace(a, "gameServerServer", Flags.class.getField("server"));
        replace(a, "serverOptionsInstance", Options.class.getField("instance"));
        replace(a, "serverSpeedLimit", Options.class.getField("speedLimit"));
        replace(a, "serverSpeedLimitValue", Limit.class.getMethod("getValue"));
        replace(a, "vehicleAuthorization", Car.class.getField("authorization"));
        replace(a, "vehicleAuthorizationPlayer", Car.class.getField("ownerId"));
        replace(a, "vehicleId", Car.class.getField("id"));
        replace(a, "vehicleLocalPhysics", Car.class.getMethod("isLocalPhysicSim"));
        replace(a, "vehicleDriver", Car.class.getMethod("getDriver"));
        replace(a, "characterDead", Player.class.getMethod("isDead"));
        replace(a, "playerLocal", Player.class.getMethod("isLocalPlayer"));
        replace(a, "vehicleScript", Car.class.getMethod("getScript"));
        replace(a, "vehicleEngineSpeed", Car.class.getMethod("getEngineSpeed"));
        replace(a, "vehicleTransmission", Car.class.getMethod("getTransmissionNumber"));
        replace(a, "vehicleSpeedKph", Car.class.getMethod("getCurrentSpeedKmHour"));
        replace(a, "vehicleSetEngineSpeed", Car.class.getMethod("setEngineSpeed", double.class));
        replace(a, "vehicleSetSteering", Car.class.getMethod("setCurrentSteering", float.class));
        replace(a, "scriptWheelFriction", Script.class.getField("wheelFriction"));
        replace(a, "scriptToBullet", Script.class.getMethod("toBullet"));
        replace(a, "controllerVehicle", Controller.class.getField("vehicle"));
        replace(a, "controllerEngineForce", Controller.class.getField("engineForce"));
        replace(a, "controllerBrakingForce", Controller.class.getField("brakingForce"));
        replace(a, "controllerSteering", Controller.class.getField("vehicleSteering"));
        replace(a, "bulletControl", Native.class.getMethod("control", int.class, float.class, float.class, float.class));
        replace(a, "bulletForce", Native.class.getMethod("force", int.class, float.class, float.class, float.class));
        return a;
    }

    private static void speedLimit(PzLegacyAccess a) throws Exception {
        Car car = new Car();
        Controller controller = new Controller(car);
        LegacyPhysics.Output output = new LegacyPhysics.Output(2, 1234.5, 7.5, .12, 9, 1, 0, 2500, .8, 1400, 0);
        PzLegacyAccess.Motion motion = new PzLegacyAccess.Motion(20, 0, 0, 1, 0, 20, 0, 0);
        for (boolean client : new boolean[]{false, true}) {
            Flags.client = client;
            Flags.server = false;
            for (double speed : new double[]{59.9, 60, 60.1, 120}) {
                Options.instance.speedLimit.value = 60;
                car.speed = speed;
                Native.forces = 0;
                double applied = a.apply(controller, car, output, motion, .12, 1.0 / 60);
                double expected = client && speed >= 60 ? 0 : output.engineForce();
                near(applied, expected, "Final native force gate");
                near(controller.engineForce, expected, "Controller/network force agrees with Bullet");
                near(Native.engine, expected, "Submitted Bullet force");
                near(Native.brake, 7.5, "No added braking");
                near(Native.steer, .12, "Steering preserved");
                near(car.rpm, 2500, "RPM still updated");
                check(Native.forces == 1, "Passive drag still submitted");
            }
        }
        Options.instance.speedLimit.value = 90;
        car.speed = 70;
        near(a.apply(controller, car, output, motion, .12, 1.0 / 60), 1234.5, "Read changed server limit");
        near(LegacyServerSpeedLimit.limitForce(-100, -60, 60), 0, "Reverse capped");
        near(LegacyServerSpeedLimit.limitForce(-100, 60, 60), -100, "Opposing force retained");
        near(LegacyServerSpeedLimit.limitForce(100, -60, 60), 100, "Reverse opposing force retained");
        near(LegacyServerSpeedLimit.limitForce(100, 0, 60), 100, "Launch unaffected");
        for (double limit : new double[]{Double.POSITIVE_INFINITY, Double.NaN, 0, -1}) {
            near(LegacyServerSpeedLimit.limitForce(100, 300, limit), 100, "No configured limit");
        }
        Flags.client = false;
        Flags.server = true;
        check(a.controlledDriver(car) == null, "Dedicated server never becomes CPI driver");
        Flags.client = true;
        check(a.controlledDriver(car) == null, "Server flag wins if both flags are set");
        Flags.server = false;
    }

    private static void sessions(PzLegacyAccess a) throws Exception {
        Car car = new Car();
        Controller controller = new Controller(car);
        car.gear = 0;
        car.rpm = 4600;
        LegacyHooks.beforeControllerUpdate(controller);
        LegacyHooks.RuntimeState first = LegacyHooks.prepareSession(a, car);
        LegacyPhysics.State state = physics(first);
        check(state.gear == 0 && state.lastStepGear == 0, "MP neutral preserved");
        near(state.engineRpm, 4600, "Incoming RPM captured");
        car.gear = 1;
        car.rpm = 900; // Simulate vanilla's update after the pre-hook.
        check(LegacyHooks.prepareSession(a, car) == first, "Stable ownership does not restart simulation");
        near(state.engineRpm, 4600, "Pre-vanilla seed survives first vanilla tick");

        for (int reason = 0; reason < 5; reason++) {
            state = physics(LegacyHooks.prepareSession(a, car));
            state.gear = 5;
            state.clutchKick = .9;
            state.throttle = 1;
            state.fullThrottleSeconds = 1;
            state.steering = .5;
            markStale(car);
            a.applyWheelFrictionScale(car, .2); // Ordinary tire/weather loss, NOT key drift.
            int writes = car.writes;
            int controls = Native.controls;
            Player oldDriver = car.driver;
            switch (reason) {
                case 0 -> car.local = false;
                case 1 -> car.driver = null;
                case 2 -> car.driver.dead = true;
                case 3 -> car.driver = new Player();
                default -> car.ownerId++;
            }
            LegacyHooks.afterVehiclePhysics(); // Must clean up even with no controller callback.
            near(car.script.wheelFriction, 1.8, "All grip paths restored on session boundary " + reason);
            assertClear(car);
            check(car.writes == writes && Native.controls == controls, "Cleanup does not touch native motion/RPM/gear");
            car.local = true;
            car.driver = oldDriver;
            car.driver.dead = false;
            car.gear = reason == 0 ? -1 : reason == 1 ? 0 : 3;
            car.rpm = 1800 + reason * 100;
            LegacyHooks.RuntimeState fresh = LegacyHooks.prepareSession(a, car);
            check(physics(fresh) != state, "New owner/session gets fresh state");
            check(physics(fresh).gear == car.gear && physics(fresh).lastStepGear == car.gear, "R/N/forward inherited without clutch kick");
            near(physics(fresh).engineRpm, car.rpm, "Reacquire current RPM");
            near(physics(fresh).clutchKick, 0, "No old clutch impulse");
            near(physics(fresh).throttle, 0, "No old throttle");
            near(physics(fresh).steering, 0, "No latched steering");
            check(field(fresh.getClass(), "conditions").get(fresh) == null, "Refresh tires/weather on handoff");
            near(field(fresh.getClass(), "activeAgeSeconds").getDouble(fresh), 0, "Re-entry safety warmup restored");
        }

        LegacyHooks.RuntimeState beforeRoundTrip = LegacyHooks.prepareSession(a, car);
        a.applyKeyDriftFriction(car, 1, .35);
        car.authorization = "Remote";
        car.local = false;
        LegacyHooks.onVehicleAuthorizationChanged(car);
        car.authorization = "Local";
        car.local = true;
        LegacyHooks.onVehicleAuthorizationChanged(car);
        near(car.script.wheelFriction, 1.8, "Ownership round trip between physics ticks restores grip");
        check(LegacyHooks.prepareSession(a, car) != beforeRoundTrip, "Immediate ownership callback drops stale state");
        LegacyHooks.RuntimeState unchanged = LegacyHooks.prepareSession(a, car);
        LegacyHooks.onVehicleAuthorizationChanged(car);
        check(LegacyHooks.prepareSession(a, car) == unchanged, "Repeated identical authorization does not reset");
        markStale(car);
        short oldId = car.id;
        car.id++;
        check(LegacyHooks.prepareSession(a, car) != unchanged, "Reassigned vehicle ID starts new session");
        near(CarPhysicsImprovedV1Mod.skidAmountFor(oldId), 0, "Old ID effects removed");
        check(CarPhysicsImprovedV1Mod.consumeShiftRequest(oldId) == 0, "Old ID requests removed");

        car.driver.local = false;
        car.authorization = "LocalCollide";
        check(a.hasAuthority(car), "Local collision physics is a valid native state");
        check(LegacyHooks.prepareSession(a, car) == null, "Remote driver never consumes our control inputs");
        car.driver.local = true;
        car.authorization = "Local";
        LegacyHooks.prepareSession(a, car);
        Script oldScript = car.script;
        a.applyKeyDriftFriction(car, 1, .35);
        car.script = new Script();
        LegacyHooks.afterVehiclePhysics();
        near(oldScript.wheelFriction, 1.8, "Restore OLD script after vehicle script replacement");
        near(car.script.wheelFriction, 1.8, "New script untouched by cleanup");

        LegacyHooks.prepareSession(a, car);
        markStale(car);
        a.applyWheelFrictionScale(car, .3);
        CarPhysicsImprovedV1Mod.setEnabled(false);
        near(car.script.wheelFriction, 1.8, "Disabling CPI restores non-key-drift grip too");
        assertClear(car);
        check(LegacyHooks.prepareSession(a, car) == null, "Disabled CPI cannot claim a session");
        CarPhysicsImprovedV1Mod.setEnabled(true);

        Flags.client = false;
        car.gear = 0;
        check(physics(LegacyHooks.prepareSession(a, car)).gear == 1, "SP initial neutral mapping unchanged");
        LegacyHooks.releaseVehicleSessions();
        Flags.client = true;
        Flags.server = true;
        int controls = Native.controls;
        check(LegacyHooks.prepareSession(a, car) == null, "Dedicated server cannot create session");
        check(Native.controls == controls, "Dedicated server emits no drive command");
        Flags.server = false;
    }

    private static void markStale(Car car) {
        CarPhysicsImprovedV1Mod.updateEffects(car.id, 20, .8);
        CarPhysicsImprovedV1Mod.requestShiftFor(car.id, 1);
    }
    private static void assertClear(Car car) {
        near(CarPhysicsImprovedV1Mod.burnoutAmountFor(car.id), 0, "No stale burnout");
        near(CarPhysicsImprovedV1Mod.skidAmountFor(car.id), 0, "No stale skid");
        check(CarPhysicsImprovedV1Mod.consumeShiftRequest(car.id) == 0, "No old shift request");
    }
    private static LegacyPhysics.State physics(LegacyHooks.RuntimeState state) throws Exception {
        check(state != null, "Expected local session");
        return (LegacyPhysics.State)field(state.getClass(), "physics").get(state);
    }
    static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    static void replace(PzLegacyAccess a, String name, Object value) throws Exception {
        field(PzLegacyAccess.class, name).set(a, value);
    }
    private static void near(double value, double expected, String message) {
        check(Double.isFinite(value) && Math.abs(value - expected) < 1e-5, message + ": " + value);
    }
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static final class Flags { public static boolean client = true; public static boolean server; }
    public static final class Options { public static final Options instance = new Options(); public final Limit speedLimit = new Limit(); }
    public static final class Limit { public int value = 60; public int getValue() { return value; } }
    public static final class Player {
        public boolean dead;
        public boolean local = true;
        public boolean isDead() { return dead; }
        public boolean isLocalPlayer() { return local; }
    }
    public static final class Script {
        public float wheelFriction = 1.8f;
        public int submissions;
        public void toBullet() { submissions++; }
    }
    public static class Car {
        public short id = 123;
        public short ownerId = 1;
        public Object authorization = "Local";
        public boolean local = true;
        public Player driver = new Player();
        public Script script = new Script();
        public double speed;
        public double rpm = 1700;
        public int gear = 2;
        public int writes;
        public boolean isLocalPhysicSim() { return local; }
        public Player getDriver() { return driver; }
        public Script getScript() { return script; }
        public double getEngineSpeed() { return rpm; }
        public int getTransmissionNumber() { return gear; }
        public double getCurrentSpeedKmHour() { return speed; }
        public void setEngineSpeed(double value) { rpm = value; writes++; }
        public void setCurrentSteering(float value) { writes++; }
    }
    public static final class Controller {
        public Car vehicle;
        public float engineForce;
        public float brakingForce;
        public float vehicleSteering;
        public Controller(Car car) { vehicle = car; }
    }
    public static final class Native {
        public static float engine, brake, steer;
        public static int controls, forces;
        public static void control(int id, float e, float b, float s) { engine = e; brake = b; steer = s; controls++; }
        public static void force(int id, float x, float y, float z) { forces++; }
    }
}
