package dev.carphysicsimproved.v1.runtime;

import dev.carphysicsimproved.v1.physics.LegacyAxleDrift;
import pzmod.carphysicsimproved.v1.CarPhysicsImprovedV1Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/** Optional adapter, deliberately fail-contained: a failure restores wheels and retains V1's old drift. */
public final class LegacyAxleDriftHooks {
    private static final Map<Object, Session> SESSIONS = new WeakHashMap<>();
    private static final Map<Object, Boolean> UNSUPPORTED = new WeakHashMap<>();
    private static Access access;
    private static boolean failed;
    private static boolean hookSeen;
    private static boolean announced;
    private static final long LEASE_NANOS = 250_000_000L;

    private LegacyAxleDriftHooks() { }

    public static synchronized boolean request(Object vehicle, Object script, double target, double dt) {
        if (failed || !hookSeen || !CarPhysicsImprovedV1Mod.rearAxleDrift()
                || UNSUPPORTED.containsKey(script)) return false;
        try {
            if (access == null) access = new Access();
            if (!access.canKeep(vehicle)) return false;
            Session session = SESSIONS.get(vehicle);
            if (session != null && session.script != script) {
                stop(vehicle);
                session = null;
            }
            if (session == null) {
                boolean[] rear = access.rearWheels(script);
                if (rear == null) {
                    UNSUPPORTED.put(script, true);
                    System.err.println("[CarPhysicsImprovedV1] Rear-axle drift: unsupported wheel layout; using legacy drift.");
                    return false;
                }
                session = new Session(script, rear);
                SESSIONS.put(vehicle, session);
            }
            session.scale = LegacyAxleDrift.enter(session.scale, target, dt);
            session.updated = System.nanoTime();
            return true;
        } catch (Throwable error) {
            disable(error);
            return false;
        }
    }

    public static synchronized void afterWheel(Object vehicle, int wheel, float[] data) {
        if (vehicle == null || data == null) return;
        hookSeen = true;
        Session session = SESSIONS.get(vehicle);
        if (session == null || failed) return;
        try {
            if (!live(vehicle, session)) return; // Vanilla's fresh values remain untouched on release.
            if (wheel < 0 || wheel >= session.rear.length || wheel >= data.length / 6) {
                throw new IllegalStateException("Wheel parameter layout changed");
            }
            float baseline = data[wheel * 6 + 2];
            if (LegacyAxleDrift.applyWheel(data, wheel, session.rear[wheel], session.scale)) {
                session.applied = true;
                session.writes++;
                session.lastBefore = baseline;
                session.lastAfter = data[wheel * 6 + 2];
                if (!announced) {
                    announced = true;
                    System.out.println("[CarPhysicsImprovedV1] Rear-axle drift: native per-wheel hook active; no body torque.");
                }
            }
        } catch (Throwable error) {
            disable(error);
        }
    }

    public static synchronized void stop(Object vehicle) {
        Session session = SESSIONS.remove(vehicle);
        if (session == null || !session.applied || access == null) return;
        try {
            // Remove the request BEFORE calling vanilla; its hooks cannot reapply the reduction.
            access.refresh(vehicle);
        } catch (Throwable error) {
            disable(error);
        }
    }

    public static synchronized void cleanup() {
        if (SESSIONS.isEmpty()) return;
        try {
            Iterator<Map.Entry<Object, Session>> iterator = SESSIONS.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Object, Session> entry = iterator.next();
                Object vehicle = entry.getKey();
                Session session = entry.getValue();
                if (!live(vehicle, session)) {
                    iterator.remove();
                    if (session.applied) access.refresh(vehicle);
                }
            }
        } catch (Throwable error) {
            disable(error);
        }
    }

    public static synchronized void restoreAll() {
        Object[] vehicles = SESSIONS.keySet().toArray();
        for (Object vehicle : vehicles) stop(vehicle);
    }

    public static synchronized String status(Object vehicle) {
        Session session = SESSIONS.get(vehicle);
        if (failed) return "unavailable; legacy fallback";
        if (!hookSeen) return "waiting for native wheel hook; legacy fallback";
        if (session == null) return "idle";
        return "rear=" + Math.rint(session.scale * 100.0) / 100.0
                + ", writes=" + session.writes + ", friction=" + session.lastBefore + "/" + session.lastAfter;
    }

    private static boolean live(Object vehicle, Session session) throws ReflectiveOperationException {
        return !failed && CarPhysicsImprovedV1Mod.rearAxleDrift()
                && System.nanoTime() - session.updated <= LEASE_NANOS
                && access.canKeep(vehicle);
    }

    private static void disable(Throwable error) {
        if (!failed) {
            failed = true;
            System.err.println("[CarPhysicsImprovedV1] Rear-axle experiment disabled; legacy physics retained: " + error);
            restoreAll();
        }
    }

    private static final class Session {
        private final Object script;
        private final boolean[] rear;
        private double scale = 1.0;
        private long updated;
        private boolean applied;
        private long writes;
        private float lastBefore;
        private float lastAfter;

        private Session(Object script, boolean[] rear) {
            this.script = script;
            this.rear = rear;
        }
    }

    /** Separate ABI boundary: its failure must not disable the existing main adapter. */
    static class Access {
        private final Method refresh;
        private final Method wheelCount;
        private final Method wheel;
        private final Field front;
        private final Field wheelParams;

        Access() throws ReflectiveOperationException {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> vehicle = Class.forName("zombie.vehicles.BaseVehicle", false, loader);
            Class<?> vector = Class.forName("org.joml.Vector3f", false, loader);
            vehicle.getDeclaredMethod("updateBulletStatsWheel", int.class, float[].class, vector,
                    float.class, int.class, double.class, double.class);
            refresh = vehicle.getDeclaredMethod("updateBulletStats");
            Class<?> script = Class.forName("zombie.scripting.objects.VehicleScript", false, loader);
            Class<?> scriptWheel = Class.forName("zombie.scripting.objects.VehicleScript$Wheel", false, loader);
            wheelCount = script.getDeclaredMethod("getWheelCount");
            wheel = script.getDeclaredMethod("getWheel", int.class);
            front = scriptWheel.getDeclaredField("front");
            wheelParams = vehicle.getDeclaredField("wheelParams");
            front.setAccessible(true);
            wheelParams.setAccessible(true);
        }

        boolean[] rearWheels(Object script) throws ReflectiveOperationException {
            int count = ((Number) wheelCount.invoke(script)).intValue();
            float[] buffer = (float[]) wheelParams.get(null);
            if (buffer == null || count < 2 || count > buffer.length / 6) return null;
            boolean[] rear = new boolean[count];
            int rears = 0;
            for (int index = 0; index < count; index++) {
                rear[index] = !front.getBoolean(wheel.invoke(script, index));
                if (rear[index]) rears++;
            }
            return rears == 0 || rears == count ? null : rear;
        }

        void refresh(Object vehicle) throws ReflectiveOperationException {
            refresh.invoke(vehicle);
        }

        boolean canKeep(Object vehicle) throws ReflectiveOperationException {
            return LegacyHooks.canKeepAxleDrift(vehicle);
        }
    }
}
