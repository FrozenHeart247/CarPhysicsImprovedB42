package dev.carphysicsimproved.v1.runtime;

import dev.carphysicsimproved.v1.physics.LegacyKeyDrift;
import java.lang.reflect.Field;

/** Exercises production adapter bookkeeping with fake native fields/methods, not a Bullet world. */
public final class LegacyKeyDriftAdapterTest {
    private LegacyKeyDriftAdapterTest() { }

    public static void main(String[] args) throws Exception {
        PzLegacyAccess access = withoutWorldAccess(); // Resolve real ABI, forbid world-census dependencies.
        replace(access, "vehicleScript", Car.class.getMethod("getScript"));
        replace(access, "scriptWheelFriction", Script.class.getField("wheelFriction"));
        replace(access, "scriptToBullet", Script.class.getMethod("toBullet"));
        replace(access, "vehicleMass", Car.class.getMethod("getMass"));
        replace(access, "vehicleNativeMass", Car.class.getMethod("getMass"));

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
        Car sameType = new Car(a.script, 1200);
        access.applyWheelFrictionScale(a, .2);
        access.applyWheelFrictionScale(sameType, .5);
        near(a.script.wheelFriction, .7, "Another owner uses original baseline, not previous reduction");
        access.restoreWheelFriction(a);
        near(a.script.wheelFriction, .7, "Old owner cannot undo new owner's reduction");
        access.restoreWheelFriction(sameType);
        near(a.script.wheelFriction, 1.4, "New owner restores original baseline");

        access.applyWheelFrictionScale(a, .2);
        Field states = PzLegacyAccess.class.getDeclaredField("scriptFrictionStates");
        states.setAccessible(true);
        Object frictionState = ((java.util.Map<?, ?>)states.get(access)).get(a.script);
        Field owner = frictionState.getClass().getDeclaredField("owner");
        owner.setAccessible(true);
        ((java.lang.ref.Reference<?>)owner.get(frictionState)).clear();
        access.applyWheelFrictionScale(sameType, .5);
        near(a.script.wheelFriction, .7, "Collected owner cannot turn reduced friction into a new baseline");
        ((java.lang.ref.Reference<?>)owner.get(frictionState)).clear();
        access.restoreAbandonedWheelFriction();
        near(a.script.wheelFriction, 1.4, "Abandoned friction restored without a controller callback");
        a.script.failNextSubmission = true;
        boolean failed = false;
        try { access.applyWheelFrictionScale(a, .2); }
        catch (IllegalStateException expected) { failed = true; }
        check(failed, "Fixture must exercise failed native submission");
        access.restoreAllWheelFriction();
        near(a.script.wheelFriction, 1.4, "Failed entry still restores the tracked baseline");
        near(a.script.nativeFriction, 1.4, "Failed entry restores native definition too");
        access.applyWheelFrictionScale(a, .2);
        a.script.failNextSubmission = true;
        failed = false;
        try { access.restoreWheelFriction(a); }
        catch (IllegalStateException expected) { failed = true; }
        check(failed, "Fixture must exercise failed native restoration");
        near(a.script.wheelFriction, 1.4, "Java baseline reset before failed native publish");
        access.restoreAllWheelFriction();
        near(a.script.nativeFriction, 1.4, "Retry republishes even when Java already has baseline");
        Car b = new Car(new Script(), 2000);
        var tuning = LegacyKeyDrift.Tuning.defaults();
        double originalTorque = access.keyDriftTorque(a, true, 1, 1, tuning);
        near(originalTorque, 4704, "Fixed gain applied to current car only");
        for (int i = 0; i < 20; i++) {
            near(access.keyDriftTorque(a, true, 1, 1, tuning), originalTorque, "No accumulated gain");
        }
        check(b.reads == 0, "Other vehicle masses are never requested");
        b.mass = 6000;
        near(access.keyDriftTorque(a, true, 1, 1, tuning), originalTorque, "Other car weight cannot change yaw");
        near(access.keyDriftTorque(b, true, 1, 1, tuning), 23520, "Each car uses its own body mass");
        near(access.keyDriftTorque(a, true, 1, 1, tuning), originalTorque, "Switching cars cannot leak gain");
        PzLegacyAccess freshAccess = withoutWorldAccess();
        replace(freshAccess, "vehicleNativeMass", Car.class.getMethod("getMass"));
        near(freshAccess.keyDriftTorque(a, true, 1, 1, tuning), originalTorque, "Fresh session has identical yaw");
        near(access.keyDriftTorque(a, true, -1, 1, tuning), -originalTorque, "Immediate countersteer");
        near(access.keyDriftTorque(null, false, 1, 1, tuning), 0, "Inactive drift does not even read a body");
        check(a.mass == 1200 && b.mass == 6000, "Adapter must never rewrite vehicle masses");
        a.mass = 1500;
        near(access.keyDriftTorque(a, true, 1, 1, tuning), 5880, "Actual own-body mass change is still respected");
        System.out.println("LegacyKeyDriftAdapterTest: actual friction adapter, fresh restoration, "
                + "capped/multiplied grip and world-independent own-body yaw passed");
    }

    private static PzLegacyAccess withoutWorldAccess() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        ClassLoader noWorld = new ClassLoader(original) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("zombie.iso.IsoWorld") || name.equals("zombie.iso.IsoCell")) {
                    throw new AssertionError("Drift adapter must not resolve world/cell access: " + name);
                }
                return super.loadClass(name, resolve);
            }
        };
        thread.setContextClassLoader(noWorld);
        try { return new PzLegacyAccess(); }
        finally { thread.setContextClassLoader(original); }
    }

    private static void replace(PzLegacyAccess access, String name, Object value) throws Exception {
        Field field = PzLegacyAccess.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(access, value);
    }

    public static final class Script {
        public float wheelFriction = 1.8f;
        public float nativeFriction = 1.8f;
        public boolean failNextSubmission;
        public int submissions;
        public void toBullet() {
            if (failNextSubmission) {
                failNextSubmission = false;
                throw new IllegalStateException("test native publication failure");
            }
            submissions++;
            nativeFriction = wheelFriction;
        }
    }
    public static final class Car {
        private final Script script;
        private double mass;
        private int reads;
        Car(Script script, double mass) { this.script = script; this.mass = mass; }
        public Script getScript() { return script; }
        public double getMass() { reads++; return mass; }
    }
    private static void near(double actual, double expected, String message) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) < 1e-6, message + ": " + actual + " / " + expected);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
