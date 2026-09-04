package dev.carphysicsimproved.v1.runtime;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Scales only the impulses added by the current pedestrian/corpse call. */
public final class LegacyCollisionHooks {
    private static final ThreadLocal<Capture> CAPTURE = new ThreadLocal<>();
    private static final ThreadLocal<SlowFactorCapture> SLOW_FACTOR_CAPTURE = new ThreadLocal<>();
    private static final ProneImpulseLimiter PRONE_IMPULSE_LIMITER = new ProneImpulseLimiter();
    private static volatile Access access;

    private LegacyCollisionHooks() {
    }

    public static void begin(Object vehicle, double multiplier) {
        if (vehicle == null || !Double.isFinite(multiplier)) {
            return;
        }
        try {
            Access current = access();
            List<?> impulses = current.impulses(vehicle);
            CAPTURE.set(new Capture(vehicle, impulses.size(), Math.max(0.0, multiplier)));
        } catch (ReflectiveOperationException error) {
            CAPTURE.remove();
            report(error);
        }
    }

    public static void end(Object vehicle) {
        Capture capture = CAPTURE.get();
        CAPTURE.remove();
        if (capture == null || capture.vehicle != vehicle || capture.multiplier == 1.0) {
            return;
        }
        try {
            Access current = access();
            List<?> impulses = current.impulses(vehicle);
            for (int index = capture.startIndex; index < impulses.size(); index++) {
                current.scale(impulses.get(index), capture.multiplier);
            }
        } catch (ReflectiveOperationException error) {
            report(error);
        }
    }

    /**
     * Scales the separate vanilla CarSlowFactor speed limiter only while
     * BaseVehicle.updateVelocityMultiplier() reads it. The stored game value is
     * restored immediately afterwards, so object collision bookkeeping remains
     * completely vanilla.
     */
    public static void beginObstacleSlowdown(Object vehicle, double multiplier) {
        SLOW_FACTOR_CAPTURE.remove();
        if (vehicle == null || !Double.isFinite(multiplier) || multiplier == 1.0) {
            return;
        }
        try {
            Access current = access();
            float original = current.breakingSlowFactor(vehicle);
            SLOW_FACTOR_CAPTURE.set(new SlowFactorCapture(vehicle, original));
            current.breakingSlowFactor(vehicle, original * (float) Math.max(0.0, multiplier));
        } catch (ReflectiveOperationException error) {
            SLOW_FACTOR_CAPTURE.remove();
            report(error);
        }
    }

    public static void endObstacleSlowdown(Object vehicle) {
        SlowFactorCapture capture = SLOW_FACTOR_CAPTURE.get();
        SLOW_FACTOR_CAPTURE.remove();
        if (capture == null || capture.vehicle != vehicle) {
            return;
        }
        try {
            access().breakingSlowFactor(vehicle, capture.original);
        } catch (ReflectiveOperationException error) {
            report(error);
        }
    }

    /**
     * Covers the vertical wheel-over-body path, which is separate from the
     * horizontal corpse-hit impulse. The short repeat limiter preserves a
     * visible bump without turning continuous wheel contact into a brake.
     */
    public static void applyProneBump(Object vehicle, double bumpMultiplier) {
        if (vehicle == null || !Double.isFinite(bumpMultiplier)) {
            return;
        }
        try {
            Access current = access();
            if (!current.hasLocalPhysicsAuthority(vehicle)) {
                return;
            }
            Object impulses = current.proneImpulses(vehicle);
            int length = Array.getLength(impulses);
            boolean hasPending = false;
            for (int index = 0; index < length; index++) {
                Object impulse = Array.get(impulses, index);
                if (impulse != null && current.isPending(impulse)) {
                    hasPending = true;
                    break;
                }
            }
            double effectiveMultiplier = PRONE_IMPULSE_LIMITER.scaleFor(
                    vehicle, hasPending, Math.max(0.0, bumpMultiplier));
            if (!hasPending) {
                return;
            }
            if (effectiveMultiplier > 0.0) {
                current.applyCustomProneBump(vehicle, impulses, effectiveMultiplier);
            }
            // The custom force above replaces the vanilla aggregate. Zeroing
            // the pending vectors lets B42 mark them applied without adding
            // its hard-capped force a second time.
            for (int index = 0; index < length; index++) {
                Object impulse = Array.get(impulses, index);
                if (impulse != null && current.isPending(impulse)) {
                    current.scale(impulse, 0.0);
                }
            }
        } catch (ReflectiveOperationException | IllegalArgumentException error) {
            report(error);
        }
    }

    private static Access access() throws ReflectiveOperationException {
        Access current = access;
        if (current == null) {
            synchronized (LegacyCollisionHooks.class) {
                current = access;
                if (current == null) {
                    current = new Access();
                    access = current;
                }
            }
        }
        return current;
    }

    private static void report(Throwable error) {
        System.err.println("[CarPhysicsImprovedV1] collision impulse adapter failed: " + error);
    }

    private record Capture(Object vehicle, int startIndex, double multiplier) {
    }

    private record SlowFactorCapture(Object vehicle, float original) {
    }

    private static final class Access {
        private final Field impulseList;
        private final Field proneImpulseArray;
        private final Field obstacleSlowFactor;
        private final Field impulseVector;
        private final Field impulseRelativePosition;
        private final Field impulseEnabled;
        private final Field impulseApplied;
        private final Field vehicleId;
        private final Field gameClientClient;
        private final Field gameServerServer;
        private final Field vectorX;
        private final Field vectorY;
        private final Field vectorZ;
        private final Method vehicleLocalPhysics;
        private final Method vehicleFudgedMass;
        private final Method bulletCentralForce;
        private final Method bulletTorque;
        private final Method vectorMultiply;

        private Access() throws ReflectiveOperationException {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> vehicleClass = Class.forName("zombie.vehicles.BaseVehicle", false, loader);
            Class<?> impulseClass = Class.forName("zombie.vehicles.BaseVehicle$VehicleImpulse", false, loader);
            Class<?> vectorClass = Class.forName("org.joml.Vector3f", false, loader);
            Class<?> bulletClass = Class.forName("zombie.core.physics.Bullet", false, loader);
            Class<?> clientClass = Class.forName("zombie.network.GameClient", false, loader);
            Class<?> serverClass = Class.forName("zombie.network.GameServer", false, loader);
            impulseList = field(vehicleClass, "impulsesFromHitObjects");
            proneImpulseArray = field(vehicleClass, "impulsesFromSquishedBodies");
            obstacleSlowFactor = field(vehicleClass, "breakingSlowFactor");
            impulseVector = field(impulseClass, "impulse");
            impulseRelativePosition = field(impulseClass, "relPos");
            impulseEnabled = field(impulseClass, "enable");
            impulseApplied = field(impulseClass, "applied");
            vehicleId = field(vehicleClass, "vehicleId");
            gameClientClient = field(clientClass, "client");
            gameServerServer = field(serverClass, "server");
            vectorX = field(vectorClass, "x");
            vectorY = field(vectorClass, "y");
            vectorZ = field(vectorClass, "z");
            vehicleLocalPhysics = vehicleClass.getDeclaredMethod("isLocalPhysicSim");
            vehicleFudgedMass = vehicleClass.getDeclaredMethod("getFudgedMass");
            bulletCentralForce = bulletClass.getDeclaredMethod(
                    "applyCentralForceToVehicle", int.class, float.class, float.class, float.class);
            bulletTorque = bulletClass.getDeclaredMethod(
                    "applyTorqueToVehicle", int.class, float.class, float.class, float.class);
            vectorMultiply = vectorClass.getMethod("mul", float.class);
        }

        @SuppressWarnings("unchecked")
        private List<?> impulses(Object vehicle) throws IllegalAccessException {
            Object value = impulseList.get(vehicle);
            if (value instanceof List<?> list) {
                return list;
            }
            if (value != null && value.getClass().isArray()) {
                int length = Array.getLength(value);
                return java.util.stream.IntStream.range(0, length).mapToObj(index -> Array.get(value, index)).toList();
            }
            throw new IllegalAccessException("unsupported impulse collection");
        }

        private Object proneImpulses(Object vehicle) throws IllegalAccessException {
            return proneImpulseArray.get(vehicle);
        }

        private boolean isPending(Object impulse) throws IllegalAccessException {
            return impulseEnabled.getBoolean(impulse) && !impulseApplied.getBoolean(impulse);
        }

        private float breakingSlowFactor(Object vehicle) throws IllegalAccessException {
            return obstacleSlowFactor.getFloat(vehicle);
        }

        private void breakingSlowFactor(Object vehicle, float value) throws IllegalAccessException {
            obstacleSlowFactor.setFloat(vehicle, value);
        }

        private void scale(Object impulse, double multiplier) throws ReflectiveOperationException {
            Object vector = impulseVector.get(impulse);
            vectorMultiply.invoke(vector, (float) multiplier);
        }

        private boolean hasLocalPhysicsAuthority(Object vehicle) throws ReflectiveOperationException {
            if (gameServerServer.getBoolean(null)) {
                return false;
            }
            return !gameClientClient.getBoolean(null)
                    || (Boolean) vehicleLocalPhysics.invoke(vehicle);
        }

        private void applyCustomProneBump(Object vehicle, Object impulses, double multiplier)
                throws ReflectiveOperationException {
            float forceX = 0.0f;
            float forceY = 0.0f;
            float forceZ = 0.0f;
            float torqueX = 0.0f;
            float torqueY = 0.0f;
            float torqueZ = 0.0f;
            int length = Array.getLength(impulses);
            for (int index = 0; index < length; index++) {
                Object impulse = Array.get(impulses, index);
                if (impulse == null || !isPending(impulse)) {
                    continue;
                }
                Object vector = impulseVector.get(impulse);
                Object relative = impulseRelativePosition.get(impulse);
                float ix = vectorX.getFloat(vector) * (float) multiplier;
                float iy = vectorY.getFloat(vector) * (float) multiplier;
                float iz = vectorZ.getFloat(vector) * (float) multiplier;
                float rx = vectorX.getFloat(relative);
                float ry = vectorY.getFloat(relative);
                float rz = vectorZ.getFloat(relative);
                forceX += ix;
                forceY += iy;
                forceZ += iz;
                torqueX += ry * iz - rz * iy;
                torqueY += rz * ix - rx * iz;
                torqueZ += rx * iy - ry * ix;
            }

            // B42 caps this precursor at mass*0.15, which makes Sandbox values
            // above 1 nearly indistinguishable at road speed. The isolated bump
            // uses a wider mass-relative cap while still preventing launch bugs.
            float mass = ((Number) vehicleFudgedMass.invoke(vehicle)).floatValue();
            float maximum = Math.max(0.0f, mass * 3.0f);
            float lengthSquared = forceX * forceX + forceY * forceY + forceZ * forceZ;
            if (maximum > 0.0f && lengthSquared > maximum * maximum) {
                float scale = maximum / (float) Math.sqrt(lengthSquared);
                forceX *= scale;
                forceY *= scale;
                forceZ *= scale;
            }
            if (forceX == 0.0f && forceY == 0.0f && forceZ == 0.0f) {
                return;
            }

            int id = vehicleId.getShort(vehicle) & 0xffff;
            bulletCentralForce.invoke(null, id, forceX * 30.0f, forceY * 30.0f, forceZ * 30.0f);
            bulletTorque.invoke(null, id, torqueX * 30.0f, torqueY * 30.0f, torqueZ * 30.0f);
        }

        private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
            Field value = owner.getDeclaredField(name);
            value.setAccessible(true);
            return value;
        }
    }
}
