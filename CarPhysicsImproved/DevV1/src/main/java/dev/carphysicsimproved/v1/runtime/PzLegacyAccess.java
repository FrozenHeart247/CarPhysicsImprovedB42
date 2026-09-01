package dev.carphysicsimproved.v1.runtime;

import dev.carphysicsimproved.v1.physics.LegacyPhysics;
import pzmod.carphysicsimproved.v1.CarPhysicsImprovedV1Mod;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Reflection boundary for the installed B42.20.4 ABI. */
final class PzLegacyAccess {
    private final Field controllerVehicle;
    private final Field controllerControls;
    private final Field controllerEngineForce;
    private final Field controllerBrakingForce;
    private final Field controllerSteering;
    private final Field controlsForward;
    private final Field controlsBackward;
    private final Field controlsBrake;
    private final Field controlsSteering;
    private final Field gameClientClient;
    private final Field gameServerServer;

    private final Method vehicleLocalPhysics;
    private final Method vehicleDriver;
    private final Method vehicleScript;
    private final Method vehiclePart;
    private final Method vehicleMass;
    private final Method vehicleEngineRunning;
    private final Method vehicleEngineSpeed;
    private final Method vehicleSetEngineSpeed;
    private final Method vehicleEnginePower;
    private final Method vehicleMaximumSpeed;
    private final Method vehicleBrakingForce;
    private final Method vehicleTransmission;
    private final Method vehicleSetSteering;
    private final Method vehicleThrottle;
    private final Method vehicleKeyboardControlled;
    private final Method vehicleOffroad;
    private final Method vehicleForest;
    private final Method vehicleForwardVector;
    private final Method vehicleSpeedKph;
    private final Field vehicleId;
    private final Field vehicleVelocity;
    private final Field vehicleTransmissionState;
    private final Field vehicleWheelInfo;

    private final Method scriptFullName;
    private final Method scriptIdleRpm;
    private final Method scriptSteeringClamp;
    private final Method scriptWheelCount;
    private final Method scriptWheel;
    private final Method scriptMechanicType;
    private final Method scriptEngineRpmType;
    private final Method scriptOffroadEfficiency;
    private final Method scriptGearCount;
    private final Field wheelId;
    private final Method partItem;
    private final Method partCondition;
    private final Method partContent;
    private final Method partCapacity;
    private final Method itemWheelFriction;

    private final Method climateInstance;
    private final Method climateRain;
    private final Method climateSnow;
    private final Method gameTimeInstance;
    private final Method gameTimePhysicsDelta;

    private final Method bulletControl;
    private final Method bulletForce;
    private final Class<?> transmissionClass;
    private final Class<?> vectorClass;
    private final Field vectorX;
    private final Field vectorY;
    private final Field vectorZ;
    private final Field wheelSkidInfo;
    private final Field wheelRotation;

    PzLegacyAccess() throws ReflectiveOperationException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> controllerClass = Class.forName("zombie.core.physics.CarController", false, loader);
        Class<?> controlsClass = Class.forName("zombie.core.physics.CarController$ClientControls", false, loader);
        Class<?> vehicleClass = Class.forName("zombie.vehicles.BaseVehicle", false, loader);
        Class<?> scriptClass = Class.forName("zombie.scripting.objects.VehicleScript", false, loader);
        Class<?> scriptWheelClass = Class.forName("zombie.scripting.objects.VehicleScript$Wheel", false, loader);
        Class<?> partClass = Class.forName("zombie.vehicles.VehiclePart", false, loader);
        Class<?> itemClass = Class.forName("zombie.inventory.InventoryItem", false, loader);
        Class<?> climateClass = Class.forName("zombie.iso.weather.ClimateManager", false, loader);
        Class<?> gameTimeClass = Class.forName("zombie.GameTime", false, loader);
        Class<?> bulletClass = Class.forName("zombie.core.physics.Bullet", false, loader);
        Class<?> clientClass = Class.forName("zombie.network.GameClient", false, loader);
        Class<?> serverClass = Class.forName("zombie.network.GameServer", false, loader);
        Class<?> wheelInfoClass = Class.forName("zombie.vehicles.BaseVehicle$WheelInfo", false, loader);
        vectorClass = Class.forName("org.joml.Vector3f", false, loader);
        transmissionClass = Class.forName("zombie.vehicles.TransmissionNumber", false, loader);

        controllerVehicle = field(controllerClass, "vehicleObject");
        controllerControls = field(controllerClass, "clientControls");
        controllerEngineForce = field(controllerClass, "engineForce");
        controllerBrakingForce = field(controllerClass, "brakingForce");
        controllerSteering = field(controllerClass, "vehicleSteering");
        controlsForward = field(controlsClass, "forward");
        controlsBackward = field(controlsClass, "backward");
        controlsBrake = field(controlsClass, "brake");
        controlsSteering = field(controlsClass, "steering");
        gameClientClient = field(clientClass, "client");
        gameServerServer = field(serverClass, "server");

        vehicleLocalPhysics = method(vehicleClass, "isLocalPhysicSim");
        vehicleDriver = method(vehicleClass, "getDriver");
        vehicleScript = method(vehicleClass, "getScript");
        vehiclePart = methodInHierarchy(vehicleClass, "getPartById", String.class);
        vehicleMass = method(vehicleClass, "getMass");
        vehicleEngineRunning = method(vehicleClass, "isEngineRunning");
        vehicleEngineSpeed = method(vehicleClass, "getEngineSpeed");
        vehicleSetEngineSpeed = method(vehicleClass, "setEngineSpeed", double.class);
        vehicleEnginePower = method(vehicleClass, "getEnginePower");
        vehicleMaximumSpeed = method(vehicleClass, "getMaxSpeed");
        vehicleBrakingForce = method(vehicleClass, "getBrakingForce");
        vehicleTransmission = method(vehicleClass, "getTransmissionNumber");
        vehicleSetSteering = method(vehicleClass, "setCurrentSteering", float.class);
        vehicleThrottle = method(vehicleClass, "getThrottle");
        vehicleKeyboardControlled = method(vehicleClass, "isKeyboardControlled");
        vehicleOffroad = method(vehicleClass, "isDoingOffroad");
        vehicleForest = method(vehicleClass, "isInForest");
        vehicleForwardVector = method(vehicleClass, "getForwardVector", vectorClass);
        vehicleSpeedKph = method(vehicleClass, "getCurrentSpeedKmHour");
        vehicleId = field(vehicleClass, "vehicleId");
        vehicleVelocity = field(vehicleClass, "jniLinearVelocity");
        vehicleTransmissionState = field(vehicleClass, "transmissionNumber");
        vehicleWheelInfo = field(vehicleClass, "wheelInfo");

        scriptFullName = method(scriptClass, "getFullName");
        scriptIdleRpm = method(scriptClass, "getEngineIdleSpeed");
        scriptSteeringClamp = method(scriptClass, "getSteeringClamp", float.class);
        scriptWheelCount = method(scriptClass, "getWheelCount");
        scriptWheel = method(scriptClass, "getWheel", int.class);
        scriptMechanicType = method(scriptClass, "getMechanicType");
        scriptEngineRpmType = method(scriptClass, "getEngineRPMType");
        scriptOffroadEfficiency = method(scriptClass, "getOffroadEfficiency");
        scriptGearCount = method(scriptClass, "getGearRatioCount");
        wheelId = field(scriptWheelClass, "id");
        partItem = method(partClass, "getInventoryItem");
        partCondition = method(partClass, "getCondition");
        partContent = method(partClass, "getContainerContentAmount");
        partCapacity = method(partClass, "getContainerCapacity");
        itemWheelFriction = method(itemClass, "getWheelFriction");

        climateInstance = method(climateClass, "getInstance");
        climateRain = method(climateClass, "getRainIntensity");
        climateSnow = method(climateClass, "getSnowStrength");
        gameTimeInstance = method(gameTimeClass, "getInstance");
        gameTimePhysicsDelta = method(gameTimeClass, "getPhysicsSecondsSinceLastUpdate");

        bulletControl = method(bulletClass, "controlVehicle", int.class, float.class, float.class, float.class);
        bulletForce = method(bulletClass, "applyCentralForceToVehicle", int.class, float.class, float.class, float.class);
        vectorX = field(vectorClass, "x");
        vectorY = field(vectorClass, "y");
        vectorZ = field(vectorClass, "z");
        wheelSkidInfo = field(wheelInfoClass, "skidInfo");
        wheelRotation = field(wheelInfoClass, "rotation");
    }

    Object vehicle(Object controller) throws ReflectiveOperationException {
        return controllerVehicle.get(controller);
    }

    boolean hasAuthority(Object vehicle) throws ReflectiveOperationException {
        boolean client = gameClientClient.getBoolean(null);
        boolean server = gameServerServer.getBoolean(null);
        boolean local = (Boolean) invoke(vehicleLocalPhysics, vehicle);
        return !server && (!client || local);
    }

    boolean hasDriver(Object vehicle) throws ReflectiveOperationException {
        return invoke(vehicleDriver, vehicle) != null;
    }

    int vehicleId(Object vehicle) throws IllegalAccessException {
        return vehicleId.getShort(vehicle) & 0xffff;
    }

    Object script(Object vehicle) throws ReflectiveOperationException {
        return invoke(vehicleScript, vehicle);
    }

    double deltaSeconds() throws ReflectiveOperationException {
        Object time = invoke(gameTimeInstance, null);
        return time == null ? 1.0 / 60.0
                : clamp(((Number) invoke(gameTimePhysicsDelta, time)).doubleValue(), 1.0 / 240.0, 0.05);
    }

    Snapshot snapshot(Object vehicle) throws ReflectiveOperationException {
        Object script = script(vehicle);
        String fullType = (String) invoke(scriptFullName, script);
        CarPhysicsImprovedV1Mod.VehicleOverride override = CarPhysicsImprovedV1Mod.vehicleOverride(fullType);
        double mass = override == null
                ? ((Number) invoke(vehicleMass, vehicle)).doubleValue()
                : override.massKg();
        double enginePower = override == null
                ? ((Number) invoke(vehicleEnginePower, vehicle)).doubleValue() / 10.0
                : override.horsePower() * 4.0;
        int gearCount = Math.max(1, Math.min(8, ((Number) invoke(scriptGearCount, script)).intValue()));
        String rpmType = String.valueOf(invoke(scriptEngineRpmType, script));
        double redline = "firebird".equalsIgnoreCase(rpmType) ? 6_000.0 : 4_500.0;
        double shiftRpm = "firebird".equalsIgnoreCase(rpmType) ? 5_800.0 : 4_350.0;
        double maximumSpeed = Math.min(120.0, ((Number) invoke(vehicleMaximumSpeed, vehicle)).doubleValue());
        double steeringClamp = ((Number) invoke(scriptSteeringClamp, script, 0.0f)).doubleValue();
        return new Snapshot(script, new LegacyPhysics.Spec(
                fullType,
                mass,
                enginePower,
                maximumSpeed,
                ((Number) invoke(scriptIdleRpm, script)).doubleValue(),
                redline,
                shiftRpm,
                gearCount == 3 ? 2.6 : gearCount == 5 ? 3.2 : 3.0,
                LegacyPhysics.legacyRatios(gearCount),
                ((Number) invoke(vehicleBrakingForce, vehicle)).doubleValue(),
                steeringClamp,
                ((Number) invoke(scriptOffroadEfficiency, script)).doubleValue(),
                ((Number) invoke(scriptMechanicType, script)).intValue()));
    }

    LegacyPhysics.Conditions conditions(Object vehicle, Snapshot snapshot) throws ReflectiveOperationException {
        Object script = snapshot.scriptIdentity;
        int wheelCount = Math.max(0, ((Number) invoke(scriptWheelCount, script)).intValue());
        double pressure = 0.0;
        double condition = 0.0;
        for (int index = 0; index < wheelCount; index++) {
            Object wheel = invoke(scriptWheel, script, index);
            String id = (String) wheelId.get(wheel);
            Object part = invoke(vehiclePart, vehicle, "Tire" + id);
            if (part == null) {
                continue;
            }
            Object item = invoke(partItem, part);
            if (item == null) {
                continue;
            }
            double capacity = Math.max(1.0, ((Number) invoke(partCapacity, part)).doubleValue());
            pressure += clamp(((Number) invoke(partContent, part)).doubleValue() / capacity, 0.0, 1.35);
            condition += clamp(((Number) invoke(partCondition, part)).doubleValue(), 0.0, 100.0)
                    * clamp(((Number) invoke(itemWheelFriction, item)).doubleValue(), 0.0, 2.0) * 0.01;
        }
        if (wheelCount > 0) {
            pressure /= wheelCount;
            condition /= wheelCount;
        } else {
            pressure = 1.0;
            condition = 1.0;
        }

        boolean offroad = (Boolean) invoke(vehicleOffroad, vehicle);
        boolean forest = (Boolean) invoke(vehicleForest, vehicle);
        double grip = 1.0;
        Object climate = invoke(climateInstance, null);
        double rain = climate == null ? 0.0
                : clamp(((Number) invoke(climateRain, climate)).doubleValue(), 0.0, 1.0);
        double snow = climate == null ? 0.0
                : clamp(((Number) invoke(climateSnow, climate)).doubleValue(), 0.0, 1.0);
        if (snow > 0.5) {
            double loss = (1.0 - CarPhysicsImprovedV1Mod.snowTraction()) * clamp(snow, 0.0, 1.0);
            grip *= (1.0 - loss) * snapshot.spec().offroadEfficiency();
        }
        if (offroad) {
            double loss = (1.0 - CarPhysicsImprovedV1Mod.offroadTraction()) * (0.5 + pressure * 0.5);
            grip *= (1.0 - loss) * snapshot.spec().offroadEfficiency();
        }
        if (forest) {
            grip *= 0.80;
        }
        if (rain > 0.01) {
            grip *= lerp(1.0, CarPhysicsImprovedV1Mod.rainTraction(), rain);
        }
        return new LegacyPhysics.Conditions(pressure, condition, clamp(grip, 0.1, 1.0), offroad);
    }

    Controls controls(Object controller, Object vehicle) throws ReflectiveOperationException {
        Object controls = controllerControls.get(controller);
        double throttle = (Boolean) invoke(vehicleKeyboardControlled, vehicle)
                ? 1.0
                : clamp(((Number) invoke(vehicleThrottle, vehicle)).doubleValue(), 0.0, 1.0);
        return new Controls(
                controlsForward.getBoolean(controls),
                controlsBackward.getBoolean(controls),
                controlsBrake.getBoolean(controls),
                -clamp(controlsSteering.getFloat(controls), -1.0, 1.0),
                throttle);
    }

    Motion motion(Object vehicle) throws ReflectiveOperationException {
        Object forward = vectorClass.getConstructor().newInstance();
        invoke(vehicleForwardVector, vehicle, forward);
        double fx = vectorX.getFloat(forward);
        double fz = vectorZ.getFloat(forward);
        double length = Math.max(1.0E-6, Math.hypot(fx, fz));
        fx /= length;
        fz /= length;
        Object velocity = vehicleVelocity.get(vehicle);
        double vx = vectorX.getFloat(velocity);
        double vy = vectorY.getFloat(velocity);
        double vz = vectorZ.getFloat(velocity);
        return new Motion(vx * fx + vz * fz, vx, vy, vz);
    }

    boolean engineRunning(Object vehicle) throws ReflectiveOperationException {
        return (Boolean) invoke(vehicleEngineRunning, vehicle);
    }

    double engineRpm(Object vehicle) throws ReflectiveOperationException {
        return ((Number) invoke(vehicleEngineSpeed, vehicle)).doubleValue();
    }

    double speedKph(Object vehicle) throws ReflectiveOperationException {
        return ((Number) invoke(vehicleSpeedKph, vehicle)).doubleValue();
    }

    int currentGear(Object vehicle) throws ReflectiveOperationException {
        return ((Number) invoke(vehicleTransmission, vehicle)).intValue();
    }

    void setGear(Object vehicle, int gear) throws ReflectiveOperationException {
        String name = gear < 0 ? "R" : gear == 0 ? "N" : "Speed" + Math.min(8, gear);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object transmission = Enum.valueOf((Class<? extends Enum>) transmissionClass.asSubclass(Enum.class), name);
        vehicleTransmissionState.set(vehicle, transmission);
    }

    void apply(Object controller, Object vehicle, LegacyPhysics.Output output, Motion motion, double deltaSeconds)
            throws ReflectiveOperationException {
        float engine = (float) output.engineForce();
        float braking = (float) output.brakingForce();
        float steering = (float) output.steeringRadians();
        controllerEngineForce.setFloat(controller, engine);
        controllerBrakingForce.setFloat(controller, braking);
        controllerSteering.setFloat(controller, steering);
        invoke(vehicleSetSteering, vehicle, steering);
        invoke(vehicleSetEngineSpeed, vehicle, output.engineRpm());
        invoke(bulletControl, null, vehicleId(vehicle), engine, braking, steering);

        double velocityLength = Math.sqrt(motion.velocityX * motion.velocityX
                + motion.velocityY * motion.velocityY + motion.velocityZ * motion.velocityZ);
        if (velocityLength > 0.1 && output.dragMagnitude() > 0.0) {
            double drag = output.dragMagnitude() * deltaSeconds * -200.0 / velocityLength;
            invoke(bulletForce, null, vehicleId(vehicle),
                    (float) (motion.velocityX * drag),
                    (float) (motion.velocityY * drag),
                    (float) (motion.velocityZ * drag));
        }
    }

    double wheelSkid(Object vehicle) throws IllegalAccessException {
        Object wheels = vehicleWheelInfo.get(vehicle);
        int count = wheels == null ? 0 : Array.getLength(wheels);
        double skid = 0.0;
        for (int index = 0; index < count; index++) {
            Object wheel = Array.get(wheels, index);
            skid += clamp(1.0 - wheelSkidInfo.getFloat(wheel) - 0.3, 0.0, 0.5);
        }
        return clamp(skid, 0.0, 1.0);
    }

    void applyBurnoutVisual(Object vehicle, double burnoutSpeedKph, double deltaSeconds) throws IllegalAccessException {
        if (burnoutSpeedKph <= 0.1) {
            return;
        }
        Object wheels = vehicleWheelInfo.get(vehicle);
        int count = wheels == null ? 0 : Array.getLength(wheels);
        for (int index = Math.max(0, count - 2); index < count; index++) {
            Object wheel = Array.get(wheels, index);
            float rotation = wheelRotation.getFloat(wheel);
            wheelRotation.setFloat(wheel, rotation + (float) (burnoutSpeedKph * deltaSeconds * 0.12));
        }
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field value = owner.getDeclaredField(name);
        value.setAccessible(true);
        return value;
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) throws NoSuchMethodException {
        Method value = owner.getDeclaredMethod(name, parameters);
        value.setAccessible(true);
        return value;
    }

    private static Method methodInHierarchy(Class<?> owner, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        try {
            Method inherited = owner.getMethod(name, parameters);
            inherited.setAccessible(true);
            return inherited;
        } catch (NoSuchMethodException ignored) {
            // Continue through non-public superclass declarations.
        }
        Class<?> current = owner;
        while (current != null) {
            try {
                return method(current, name, parameters);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name);
    }

    private static Object invoke(Method method, Object receiver, Object... arguments)
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

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    record Snapshot(Object scriptIdentity, LegacyPhysics.Spec spec) {
    }

    record Controls(boolean forward, boolean backward, boolean handbrake, double steering, double throttle) {
    }

    record Motion(double longitudinalSpeedMps, double velocityX, double velocityY, double velocityZ) {
    }
}
