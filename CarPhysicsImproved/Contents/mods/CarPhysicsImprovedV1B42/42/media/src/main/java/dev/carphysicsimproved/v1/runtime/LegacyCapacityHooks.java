package dev.carphysicsimproved.v1.runtime;

import pzmod.carphysicsimproved.v1.CarPhysicsImprovedV1Mod;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Optional replacement for the old ItemContainer class edit. */
public final class LegacyCapacityHooks {
    private static volatile Access access;
    private static volatile boolean failed;

    private LegacyCapacityHooks() {
    }

    public static int adjust(Object container, int vanillaCapacity) {
        if (container == null || !CarPhysicsImprovedV1Mod.trunkOverhaul() || failed) {
            return vanillaCapacity;
        }
        try {
            Access current = access();
            Object vehicle = current.invoke(current.containerVehicle, container);
            Object part = current.invoke(current.containerVehiclePart, container);
            if (vehicle == null || part == null || !current.isCargoPart(part)) {
                return vanillaCapacity;
            }
            Object script = current.invoke(current.vehicleScript, vehicle);
            String fullType = (String) current.invoke(current.scriptFullName, script);
            CarPhysicsImprovedV1Mod.VehicleOverride override = CarPhysicsImprovedV1Mod.vehicleOverride(fullType);
            double result = override != null && override.cargoKg() > 0.0
                    ? override.cargoKg() * CarPhysicsImprovedV1Mod.trunkMultiplier()
                            + CarPhysicsImprovedV1Mod.trunkAdder()
                    : vanillaCapacity * CarPhysicsImprovedV1Mod.otherTrunkMultiplier()
                            + CarPhysicsImprovedV1Mod.otherTrunkAdder();
            return (int) Math.max(0.0, Math.min(100_000.0, result));
        } catch (ReflectiveOperationException | RuntimeException error) {
            failed = true;
            System.err.println("[CarPhysicsImprovedV1] trunk adapter failed; using vanilla capacity: " + error);
            return vanillaCapacity;
        }
    }

    private static Access access() throws ReflectiveOperationException {
        Access current = access;
        if (current == null) {
            synchronized (LegacyCapacityHooks.class) {
                current = access;
                if (current == null) {
                    current = new Access();
                    access = current;
                }
            }
        }
        return current;
    }

    private static final class Access {
        private final Method containerVehicle;
        private final Method containerVehiclePart;
        private final Method vehicleScript;
        private final Method scriptFullName;
        private final Method partArea;
        private final Method partScriptPart;
        private final Method scriptPartId;

        private Access() throws ReflectiveOperationException {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> containerClass = Class.forName("zombie.inventory.ItemContainer", false, loader);
            Class<?> vehicleClass = Class.forName("zombie.vehicles.BaseVehicle", false, loader);
            Class<?> scriptClass = Class.forName("zombie.scripting.objects.VehicleScript", false, loader);
            Class<?> partClass = Class.forName("zombie.vehicles.VehiclePart", false, loader);
            Class<?> scriptPartClass = Class.forName("zombie.scripting.objects.VehicleScript$Part", false, loader);
            containerVehicle = method(containerClass, "getVehicle");
            containerVehiclePart = method(containerClass, "getVehiclePart");
            vehicleScript = method(vehicleClass, "getScript");
            scriptFullName = method(scriptClass, "getFullName");
            partArea = method(partClass, "getArea");
            partScriptPart = method(partClass, "getScriptPart");
            scriptPartId = method(scriptPartClass, "getId");
        }

        private boolean isCargoPart(Object part) throws ReflectiveOperationException {
            Object scriptPart = invoke(partScriptPart, part);
            String id = scriptPart == null ? "" : String.valueOf(invoke(scriptPartId, scriptPart));
            Object rawArea = invoke(partArea, part);
            String area = rawArea == null ? "" : rawArea.toString();
            return "TruckBed".equals(id) || "TruckBedOpen".equals(id) || "TrailerTrunk".equals(id)
                    || "TruckBed".equals(area);
        }

        private Object invoke(Method method, Object receiver, Object... arguments) throws ReflectiveOperationException {
            try {
                return method.invoke(receiver, arguments);
            } catch (InvocationTargetException error) {
                Throwable cause = error.getCause();
                if (cause instanceof ReflectiveOperationException reflective) {
                    throw reflective;
                }
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new ReflectiveOperationException(cause);
            }
        }

        private static Method method(Class<?> owner, String name, Class<?>... parameters)
                throws NoSuchMethodException {
            Method value = owner.getDeclaredMethod(name, parameters);
            value.setAccessible(true);
            return value;
        }
    }
}
