package dev.carphysicsimproved.v1.runtime;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Scales only the impulses added by the current pedestrian/corpse call. */
public final class LegacyCollisionHooks {
    private static final ThreadLocal<Capture> CAPTURE = new ThreadLocal<>();
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

    private static final class Access {
        private final Field impulseList;
        private final Field impulseVector;
        private final Method vectorMultiply;

        private Access() throws ReflectiveOperationException {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> vehicleClass = Class.forName("zombie.vehicles.BaseVehicle", false, loader);
            Class<?> impulseClass = Class.forName("zombie.vehicles.BaseVehicle$VehicleImpulse", false, loader);
            Class<?> vectorClass = Class.forName("org.joml.Vector3f", false, loader);
            impulseList = field(vehicleClass, "impulsesFromHitObjects");
            impulseVector = field(impulseClass, "impulse");
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

        private void scale(Object impulse, double multiplier) throws ReflectiveOperationException {
            Object vector = impulseVector.get(impulse);
            vectorMultiply.invoke(vector, (float) multiplier);
        }

        private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
            Field value = owner.getDeclaredField(name);
            value.setAccessible(true);
            return value;
        }
    }
}
