package dev.carphysicsimproved.v1.runtime;

import dev.carphysicsimproved.v1.physics.LegacyCabinExposure;
import pzmod.carphysicsimproved.v1.CarPhysicsImprovedV1Mod;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Connects windshield exposure to vanilla Windchill and clothing wetness. */
public final class LegacyCabinExposureHooks {
    private static volatile Access access;
    private static volatile boolean failed;

    private LegacyCabinExposureHooks() {
    }

    public static float adjustWindChill(Object player, float vanillaAmount) {
        if (player == null || failed || !CarPhysicsImprovedV1Mod.enabled()) {
            return vanillaAmount;
        }
        try {
            Access current = access();
            Object vehicle = current.invoke(current.characterVehicle, player);
            if (vehicle == null) {
                return vanillaAmount;
            }
            WindshieldState windshield = current.windshieldState(vehicle);
            double vehicleAmount = LegacyCabinExposure.windChillAmount(
                    current.number(current.invoke(current.vehicleSpeed, vehicle)),
                    windshield.exposure());
            return (float) Math.max(vanillaAmount, vehicleAmount);
        } catch (ReflectiveOperationException | RuntimeException error) {
            reportOnce(error);
            return vanillaAmount;
        }
    }

    /** Amend the single vanilla clothing update, never run sweating/drying twice. */
    public static RainInput adjustRainInputs(Object clothingWetness, float increase, float decrease) {
        RainInput original = new RainInput(increase, decrease);
        if (clothingWetness == null || failed || !CarPhysicsImprovedV1Mod.enabled()) {
            return original;
        }
        try {
            Access current = access();
            Object character = current.clothingCharacter.get(clothingWetness);
            if (character == null) {
                return original;
            }
            Object vehicle = current.invoke(current.characterVehicle, character);
            if (vehicle == null) {
                return original;
            }
            WindshieldState windshield = current.windshieldState(vehicle);
            if (windshield.exposure() <= 0.0) {
                return original;
            }

            double speedKph = current.number(current.invoke(current.vehicleSpeed, vehicle));
            // B42 already applies this exact curve to a destroyed VehicleWindow
            // while moving forwards. Its calculation is signed, so supplement
            // reverse movement but never double the ordinary vanilla case.
            if (windshield.windowDestroyed() && speedKph > 0.0) {
                return original;
            }

            Object climate = current.invoke(current.climateInstance, null);
            if (climate == null || !current.booleanValue(current.invoke(current.climateRaining, climate))) {
                return original;
            }
            double rainExposure = LegacyCabinExposure.rainExposure(
                    speedKph,
                    current.number(current.invoke(current.climateRainIntensity, climate)),
                    windshield.exposure());
            if (rainExposure <= 0.0) {
                return original;
            }

            return mergeRain(original, rainExposure);
        } catch (ReflectiveOperationException | RuntimeException error) {
            reportOnce(error);
        }
        return original;
    }

    static RainInput mergeRain(RainInput vanilla, double rainExposure) {
        if (!Double.isFinite(rainExposure) || rainExposure <= vanilla.increase()) return vanilla;
        return new RainInput((float) Math.min(1, rainExposure),
                (float) Math.max(0, vanilla.decrease() - rainExposure * 3));
    }

    public record RainInput(float increase, float decrease) { }

    public static void validateRuntimeAbi() throws ReflectiveOperationException {
        new Access();
    }

    private static Access access() throws ReflectiveOperationException {
        Access current = access;
        if (current == null) {
            synchronized (LegacyCabinExposureHooks.class) {
                current = access;
                if (current == null) {
                    current = new Access();
                    access = current;
                }
            }
        }
        return current;
    }

    private static void reportOnce(Throwable error) {
        if (!failed) {
            failed = true;
            System.err.println("[CarPhysicsImprovedV1] windshield exposure adapter failed; "
                    + "using vanilla cabin weather: " + error);
        }
    }

    private record WindshieldState(double exposure, boolean windowDestroyed) {
    }

    private static final class Access {
        private final Field clothingCharacter;
        private final Method characterVehicle;
        private final Method vehiclePartById;
        private final Method vehicleSpeed;
        private final Method partInventoryItem;
        private final Method partCondition;
        private final Method partWindow;
        private final Method windowDestroyed;
        private final Method climateInstance;
        private final Method climateRaining;
        private final Method climateRainIntensity;

        private Access() throws ReflectiveOperationException {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> characterClass = Class.forName("zombie.characters.IsoGameCharacter", false, loader);
            Class<?> vehicleClass = Class.forName("zombie.vehicles.BaseVehicle", false, loader);
            Class<?> vehiclePartOwnerClass = Class.forName(
                    "zombie.vehicles.VehiclePartOwner", false, loader);
            Class<?> partClass = Class.forName("zombie.vehicles.VehiclePart", false, loader);
            Class<?> windowClass = Class.forName("zombie.vehicles.VehicleWindow", false, loader);
            Class<?> climateClass = Class.forName("zombie.iso.weather.ClimateManager", false, loader);
            Class<?> clothingWetnessClass = Class.forName(
                    "zombie.characters.ClothingWetness", false, loader);

            clothingCharacter = clothingWetnessClass.getDeclaredField("character");
            clothingCharacter.setAccessible(true);
            characterVehicle = method(characterClass, "getVehicle");
            vehiclePartById = method(vehiclePartOwnerClass, "getPartById", String.class);
            vehicleSpeed = method(vehicleClass, "getCurrentSpeedKmHour");
            partInventoryItem = method(partClass, "getInventoryItem");
            partCondition = method(partClass, "getCondition");
            partWindow = method(partClass, "getWindow");
            windowDestroyed = method(windowClass, "isDestroyed");
            climateInstance = method(climateClass, "getInstance");
            climateRaining = method(climateClass, "isRaining");
            climateRainIntensity = method(climateClass, "getRainIntensity");
        }

        private WindshieldState windshieldState(Object vehicle) throws ReflectiveOperationException {
            Object part = invoke(vehiclePartById, vehicle, "Windshield");
            if (part == null) {
                return new WindshieldState(1.0, false);
            }
            Object window = invoke(partWindow, part);
            boolean destroyed = window != null && booleanValue(invoke(windowDestroyed, window));
            Object item = invoke(partInventoryItem, part);
            if (item == null) {
                return new WindshieldState(1.0, destroyed);
            }
            int condition = ((Number) invoke(partCondition, part)).intValue();
            double exposure = LegacyCabinExposure.windshieldExposure(
                    true, true, destroyed, condition);
            return new WindshieldState(exposure, destroyed);
        }

        private double number(Object value) {
            return value instanceof Number number ? number.doubleValue() : 0.0;
        }

        private boolean booleanValue(Object value) {
            return value instanceof Boolean booleanValue && booleanValue;
        }

        private Object invoke(Method method, Object receiver, Object... arguments)
                throws ReflectiveOperationException {
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
