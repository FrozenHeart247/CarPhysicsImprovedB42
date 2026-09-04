package dev.carphysicsimproved.v1.runtime;

import pzmod.carphysicsimproved.v1.CarPhysicsImprovedV1Mod;
import pzmod.carphysicsimproved.v1.Patch_BaseVehicleWheelGrip;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;

/** Exercises real hook/session code with a fake native vehicle boundary; no Bullet world is started. */
public final class LegacyAxleDriftLifecycleTest {
    private LegacyAxleDriftLifecycleTest() { }

    public static void main(String[] args) throws Exception {
        FakeAccess nativeApi = new FakeAccess(); // Also resolves the installed game ABI.
        field(LegacyAxleDriftHooks.class, "access").set(null, nativeApi);
        CarPhysicsImprovedV1Mod.setEnabled(true);
        CarPhysicsImprovedV1Mod.configureRearAxleDrift(true, .45);
        Object sameScript = new Object();
        Car a = new Car();
        Car b = new Car();
        nativeApi.refresh(a); // Confirms that the target hook is callable before any takeover.
        check(LegacyAxleDriftHooks.request(a, sameScript, .45, .05), "Activate A");
        nativeApi.refresh(a);
        check(a.values[14] < a.baseline[14] && a.values[2] == a.baseline[2], "Only rear wheels changed");
        check(Arrays.equals(b.values, b.baseline), "Same script must not affect B");
        check(LegacyAxleDriftHooks.request(b, sameScript, .45, .05), "Activate B independently");
        nativeApi.refresh(b);
        a.keep = false; // Equivalent boundary result for released key/death/no driver/lost authority.
        LegacyAxleDriftHooks.cleanup();
        check(Arrays.equals(a.values, a.baseline), "Release/authority loss restores fresh native baseline");
        check(b.values[14] < b.baseline[14], "Restoring A must not restore B");
        check(sessions().size() == 1, "Only active owner stays tracked");

        Object bSession = sessions().get(b);
        field(bSession.getClass(), "updated").setLong(bSession, System.nanoTime() - 1_000_000_000L);
        LegacyAxleDriftHooks.cleanup();
        check(Arrays.equals(b.values, b.baseline) && sessions().isEmpty(), "Stale request expires and restores");

        a.keep = true;
        LegacyAxleDriftHooks.request(a, sameScript, .45, .05);
        nativeApi.refresh(a);
        CarPhysicsImprovedV1Mod.configureRearAxleDrift(false, .45);
        check(Arrays.equals(a.values, a.baseline) && sessions().isEmpty(), "Switch to legacy restores immediately");
        check(!LegacyAxleDriftHooks.request(a, sameScript, .45, .05), "Disabled experiment cannot take over");

        CarPhysicsImprovedV1Mod.configureRearAxleDrift(true, .45);
        LegacyAxleDriftHooks.request(a, sameScript, .45, .05);
        nativeApi.refresh(a);
        a.baseline[14] = .1f; // A tire was damaged since entry: do not restore its OLD healthy value.
        CarPhysicsImprovedV1Mod.setEnabled(false);
        check(a.values[14] == .1f && sessions().isEmpty(), "V1 disable restores current, not cached, condition");

        CarPhysicsImprovedV1Mod.setEnabled(true);
        LegacyAxleDriftHooks.request(a, sameScript, .45, .05);
        nativeApi.refresh(a);
        nativeApi.invalidLayout = true;
        Patch_BaseVehicleWheelGrip.exit(a, 99, a.values);
        check(LegacyAxleDriftHooks.status(a).contains("unavailable") && sessions().isEmpty(),
                "Bad ABI must disable only the experiment and clear its requests");
        check(CarPhysicsImprovedV1Mod.enabled() && Arrays.equals(a.values, a.baseline),
                "Main V1 stays enabled and native wheels are restored after failure");
        System.out.println("LegacyAxleDriftLifecycleTest: owner isolation, expiry, switches, fresh restore and fail-contained fallback passed");
    }

    private static Map<?, ?> sessions() throws Exception {
        return (Map<?, ?>) field(LegacyAxleDriftHooks.class, "SESSIONS").get(null);
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field value = owner.getDeclaredField(name);
        value.setAccessible(true);
        return value;
    }

    private static final class Car {
        private final float[] baseline = { 1, .9f, 1.8f, 2, 3, .01f, 1, .9f, 1.8f, 2, 3, .01f,
                1, .7f, .8f, 2, 3, .02f, 1, .7f, .8f, 2, 3, .02f };
        private final float[] values = baseline.clone();
        private boolean keep = true;
    }

    private static final class FakeAccess extends LegacyAxleDriftHooks.Access {
        private boolean invalidLayout;
        FakeAccess() throws ReflectiveOperationException { super(); }
        @Override boolean[] rearWheels(Object script) { return new boolean[] { false, false, true, true }; }
        @Override boolean canKeep(Object vehicle) { return ((Car) vehicle).keep; }
        @Override void refresh(Object vehicle) {
            Car car = (Car) vehicle;
            System.arraycopy(car.baseline, 0, car.values, 0, car.baseline.length);
            if (!invalidLayout) {
                for (int wheel = 0; wheel < 4; wheel++) Patch_BaseVehicleWheelGrip.exit(car, wheel, car.values);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
