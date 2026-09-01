package zombie.roadcraft.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;

/** Loads the loose shadow before the game jar and validates its B42 contract. */
public final class LooseCarControllerSmokeTest {
    private LooseCarControllerSmokeTest() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected the loose payload directory");
        }
        Path expectedRoot = Path.of(arguments[0]).toAbsolutePath().normalize();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> controller = Class.forName("zombie.core.physics.CarController", false, loader);
        Path actualRoot = Path.of(controller.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toAbsolutePath().normalize();
        check(expectedRoot.toString().equalsIgnoreCase(actualRoot.toString()),
                "CarController was not loaded from the loose payload: " + actualRoot);

        Class<?> baseVehicle = Class.forName("zombie.vehicles.BaseVehicle", false, loader);
        Class<?> vector = Class.forName("org.joml.Vector3f", false, loader);
        Class<?> controls = Class.forName("zombie.core.physics.CarController$ClientControls", false, loader);
        requiredConstructor(controller, baseVehicle);
        requiredFields(controller, controls);
        requiredMethods(controller, vector);
        requiredControlFields(controls);

        Class.forName("zombie.roadcraft.runtime.RoadcraftHooks", true, loader);
        check("ACTIVE".equals(RoadcraftBridge.status()),
                "loose runtime did not bootstrap: " + RoadcraftBridge.statusDetail());

        Constructor<?> adapter = PzAccess.class.getDeclaredConstructor();
        adapter.setAccessible(true);
        adapter.newInstance();
        System.out.println("LooseCarControllerSmokeTest: loose class, B42 ABI and adapter passed");
    }

    private static void requiredConstructor(Class<?> controller, Class<?> vehicle) throws Exception {
        controller.getConstructor(vehicle);
    }

    private static void requiredFields(Class<?> controller, Class<?> controls) throws Exception {
        field(controller, "vehicleObject", Object.class);
        field(controller, "clientForce", float.class);
        field(controller, "engineForce", float.class);
        field(controller, "brakingForce", float.class);
        field(controller, "isEnable", boolean.class);
        field(controller, "acceleratorOn", boolean.class);
        field(controller, "brakeOn", boolean.class);
        field(controller, "speed", float.class);
        Field clientControls = controller.getField("clientControls");
        check(clientControls.getType() == controls, "clientControls type mismatch");
    }

    private static void requiredMethods(Class<?> controller, Class<?> vector) throws Exception {
        method(controller, "update");
        method(controller, "updateTrailer");
        method(controller, "updateControls");
        method(controller, "park");
        method(controller, "control_NoControl");
        method(controller, "checkShouldBeActive");
        method(controller, "getVehicleSteering");
        method(controller, "isGas");
        method(controller, "isGasR");
        method(controller, "isBreak");
        method(controller, "isGasPedalPressed");
        method(controller, "isBrakePedalPressed");
        method(controller, "drawRect", vector, float.class, float.class, float.class, float.class);
        method(controller, "drawRect", vector, float.class, float.class, float.class, float.class,
                float.class, float.class, float.class);
        method(controller, "drawCircle", float.class, float.class, float.class);
        method(controller, "drawCircle", float.class, float.class, float.class, float.class,
                float.class, float.class, float.class);
    }

    private static void requiredControlFields(Class<?> controls) throws Exception {
        field(controls, "steering", float.class);
        field(controls, "forward", boolean.class);
        field(controls, "backward", boolean.class);
        field(controls, "brake", boolean.class);
        field(controls, "shift", boolean.class);
        field(controls, "wasUsingParkingBrakes", boolean.class);
        field(controls, "forceBrake", long.class);
        method(controls, "reset");
    }

    private static void field(Class<?> owner, String name, Class<?> expectedType) throws Exception {
        Field field = owner.getField(name);
        check(field.getType() == expectedType || expectedType == Object.class,
                owner.getName() + "." + name + " type mismatch");
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) throws Exception {
        return owner.getMethod(name, parameters);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
