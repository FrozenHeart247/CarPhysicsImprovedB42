package dev.carphysicsimproved.v1.runtime;

import java.lang.reflect.Field;
import java.util.List;

/** Exercises production adapter bookkeeping with fake native fields/methods, not a Bullet world. */
public final class LegacyKeyDriftAdapterTest {
    private LegacyKeyDriftAdapterTest() { }

    public static void main(String[] args) throws Exception {
        PzLegacyAccess access = new PzLegacyAccess(); // First resolve the real installed ABI.
        replace(access, "vehicleScript", Car.class.getMethod("getScript"));
        replace(access, "scriptWheelFriction", Script.class.getField("wheelFriction"));
        replace(access, "scriptToBullet", Script.class.getMethod("toBullet"));
        replace(access, "vehicleMass", Car.class.getMethod("getMass"));
        replace(access, "vehicleNativeMass", Car.class.getMethod("getMass"));
        replace(access, "worldInstance", World.class.getField("instance"));
        replace(access, "worldCell", World.class.getField("currentCell"));
        replace(access, "cellVehicles", Cell.class.getMethod("getVehicles"));

        Car a = new Car(new Script(), 1200);
        a.script.wheelFriction = 3;
        access.applyKeyDriftFriction(a, 1.125, .35);
        near(a.script.wheelFriction, .63, "Reference 1.8 cap is before drift multiplier");
        for (int i = 0; i < 120; i++) access.applyKeyDriftFriction(a, 1.125, .35);
        near(a.script.wheelFriction, .63, "No repeated multiplication");
        check(a.script.submissions == 1, "Unchanged friction is not resubmitted every tick");
        access.applyKeyDriftFriction(a, .1, .35);
        near(a.script.wheelFriction, .105, "New drift grip may be below legacy .12 scale floor");
        access.applyWheelFrictionScale(a, .7);
        near(a.script.wheelFriction, 2.1, "Release preserves current environmental penalty");
        access.applyWheelFrictionScale(a, 1);
        near(a.script.wheelFriction, 3, "Release on dry road restores original friction");
        a.script.wheelFriction = 1.4f;
        access.applyKeyDriftFriction(a, 1, .35);
        near(a.script.wheelFriction, .49, "New entry reads current baseline");
        access.restoreAllWheelFriction();
        near(a.script.wheelFriction, 1.4, "Disable/failure cleanup restores baseline");
        Car b = new Car(new Script(), 2000);
        World.instance.currentCell = new Cell(List.of(a, b));
        near(access.referenceDriftMassScale(a), .375, "Read maximum loaded mass like reference");
        int reads = b.reads;
        for (int i = 0; i < 20; i++) access.referenceDriftMassScale(a);
        check(b.reads == reads, "Do not rescan vehicles on each controller tick");
        b.mass = 6000;
        Field timer = PzLegacyAccess.class.getDeclaredField("massScaleReadAt");
        timer.setAccessible(true);
        timer.setLong(access, 0);
        near(access.referenceDriftMassScale(a), .125, "Refresh normalization after cache expiry");
        World.instance.currentCell = new Cell(List.of(a));
        near(access.referenceDriftMassScale(a), .625, "World change invalidates cache immediately");
        check(a.mass == 1200 && b.mass == 6000, "Adapter must never rewrite vehicle masses");
        System.out.println("LegacyKeyDriftAdapterTest: actual friction adapter, fresh restoration, "
                + "capped/multiplied grip and read-only cached mass census passed");
    }

    private static void replace(PzLegacyAccess access, String name, Object value) throws Exception {
        Field field = PzLegacyAccess.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(access, value);
    }

    public static final class Script {
        public float wheelFriction = 1.8f;
        public int submissions;
        public void toBullet() { submissions++; }
    }
    public static final class Car {
        private final Script script;
        private double mass;
        private int reads;
        Car(Script script, double mass) { this.script = script; this.mass = mass; }
        public Script getScript() { return script; }
        public double getMass() { reads++; return mass; }
    }
    public static final class World {
        public static final World instance = new World();
        public Cell currentCell;
    }
    public static final class Cell {
        private final List<Car> cars;
        Cell(List<Car> cars) { this.cars = cars; }
        public List<Car> getVehicles() { return cars; }
    }
    private static void near(double actual, double expected, String message) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) < 1e-6, message + ": " + actual + " / " + expected);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
