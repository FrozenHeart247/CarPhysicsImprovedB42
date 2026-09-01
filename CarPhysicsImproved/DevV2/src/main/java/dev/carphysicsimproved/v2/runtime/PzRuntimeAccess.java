package dev.carphysicsimproved.v2.runtime;

import dev.carphysicsimproved.v2.physics.DriveLayout;
import dev.carphysicsimproved.v2.physics.ScriptVehicleData;
import dev.carphysicsimproved.v2.physics.VehicleCondition;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Version-sensitive B42.20.4 reflection boundary; the core imports no game classes. */
final class PzRuntimeAccess {
    private final Field controllerVehicle;
    private final Field controllerControls;
    private final Field controllerEngineForce;
    private final Field controllerBrakingForce;
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
    private final Method vehicleEngineSounding;
    private final Method vehicleTransmission;
    private final Field vehicleTransmissionState;
    private final Method vehicleSetSteering;
    private final Method vehicleGetThrottle;
    private final Method vehicleKeyboardControlled;
    private final Method vehicleOffroad;
    private final Method vehicleForest;
    private final Method vehicleForwardVector;
    private final Field vehicleId;
    private final Field vehicleVelocity;
    private final Field vehicleWheelInfo;

    private final Method scriptFullName;
    private final Method scriptMass;
    private final Method scriptEngineForce;
    private final Method scriptIdleRpm;
    private final Method scriptWheelFriction;
    private final Method scriptCenterOfMass;
    private final Method scriptSteeringClamp;
    private final Method scriptWheelCount;
    private final Method scriptWheel;
    private final Field scriptMaxSpeed;
    private final Field scriptGearRatioCount;
    private final Field scriptGearRatios;
    private final Field wheelId;
    private final Field wheelFront;
    private final Field wheelRadius;
    private final Field wheelWidth;
    private final Method wheelOffset;

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
    private final Method bulletTorque;
    private final Class<?> transmissionClass;
    private final Class<?> vectorClass;
    private final Field vectorX;
    private final Field vectorY;
    private final Field vectorZ;
    private final Field wheelRotation;
    private final Field wheelSkidInfo;

    PzRuntimeAccess() throws ReflectiveOperationException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> controllerClass = Class.forName("zombie.core.physics.CarController", false, loader);
        Class<?> controlsClass = Class.forName("zombie.core.physics.CarController$ClientControls", false, loader);
        Class<?> vehicleClass = Class.forName("zombie.vehicles.BaseVehicle", false, loader);
        Class<?> scriptClass = Class.forName("zombie.scripting.objects.VehicleScript", false, loader);
        Class<?> wheelClass = Class.forName("zombie.scripting.objects.VehicleScript$Wheel", false, loader);
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
        vehicleEngineSounding = method(vehicleClass, "isEngineSounding");
        vehicleTransmission = method(vehicleClass, "getTransmissionNumber");
        vehicleTransmissionState = field(vehicleClass, "transmissionNumber");
        vehicleSetSteering = method(vehicleClass, "setCurrentSteering", float.class);
        vehicleGetThrottle = method(vehicleClass, "getThrottle");
        vehicleKeyboardControlled = method(vehicleClass, "isKeyboardControlled");
        vehicleOffroad = method(vehicleClass, "isDoingOffroad");
        vehicleForest = method(vehicleClass, "isInForest");
        vehicleForwardVector = method(vehicleClass, "getForwardVector", vectorClass);
        vehicleId = field(vehicleClass, "vehicleId");
        vehicleVelocity = field(vehicleClass, "jniLinearVelocity");
        vehicleWheelInfo = field(vehicleClass, "wheelInfo");

        scriptFullName = method(scriptClass, "getFullName");
        scriptMass = method(scriptClass, "getMass");
        scriptEngineForce = method(scriptClass, "getEngineForce");
        scriptIdleRpm = method(scriptClass, "getEngineIdleSpeed");
        scriptWheelFriction = method(scriptClass, "getWheelFriction");
        scriptCenterOfMass = method(scriptClass, "getCenterOfMassOffset");
        scriptSteeringClamp = method(scriptClass, "getSteeringClamp", float.class);
        scriptWheelCount = method(scriptClass, "getWheelCount");
        scriptWheel = method(scriptClass, "getWheel", int.class);
        scriptMaxSpeed = field(scriptClass, "maxSpeed");
        scriptGearRatioCount = field(scriptClass, "gearRatioCount");
        scriptGearRatios = field(scriptClass, "gearRatio");
        wheelId = field(wheelClass, "id");
        wheelFront = field(wheelClass, "front");
        wheelRadius = field(wheelClass, "radius");
        wheelWidth = field(wheelClass, "width");
        wheelOffset = method(wheelClass, "getOffset");

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
        bulletTorque = method(bulletClass, "applyTorqueToVehicle", int.class, float.class, float.class, float.class);
        vectorX = field(vectorClass, "x");
        vectorY = field(vectorClass, "y");
        vectorZ = field(vectorClass, "z");
        wheelRotation = field(wheelInfoClass, "rotation");
        wheelSkidInfo = field(wheelInfoClass, "skidInfo");
    }

    Object vehicle(Object controller) throws ReflectiveOperationException {
        return controllerVehicle.get(controller);
    }

    Object script(Object vehicle) throws ReflectiveOperationException {
        return invoke(vehicleScript, vehicle);
    }

    boolean hasAuthority(Object vehicle) throws ReflectiveOperationException {
        boolean client = gameClientClient.getBoolean(null);
        boolean server = gameServerServer.getBoolean(null);
        boolean local = (Boolean) invoke(vehicleLocalPhysics, vehicle);
        return (!client && !server) || local;
    }

    boolean hasDriver(Object vehicle) throws ReflectiveOperationException {
        return invoke(vehicleDriver, vehicle) != null;
    }

    int vehicleId(Object vehicle) throws IllegalAccessException {
        return vehicleId.getShort(vehicle) & 0xffff;
    }

    double deltaSeconds() throws ReflectiveOperationException {
        Object gameTime = invoke(gameTimeInstance, null);
        if (gameTime == null) {
            return 1.0 / 60.0;
        }
        return clamp(((Number) invoke(gameTimePhysicsDelta, gameTime)).doubleValue(), 1.0 / 240.0, 0.05);
    }

    ScriptVehicleData snapshot(Object vehicle) throws ReflectiveOperationException {
        Object script = script(vehicle);
        int count = Math.max(0, ((Number) invoke(scriptWheelCount, script)).intValue());
        List<ScriptVehicleData.Wheel> wheels = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Object wheel = invoke(scriptWheel, script, index);
            Object offset = invoke(wheelOffset, wheel);
            wheels.add(new ScriptVehicleData.Wheel(
                    (String) wheelId.get(wheel),
                    wheelFront.getBoolean(wheel),
                    vectorX.getFloat(offset),
                    vectorZ.getFloat(offset),
                    wheelRadius.getFloat(wheel),
                    wheelWidth.getFloat(wheel)));
        }
        int ratioCount = Math.max(1, Math.min(8, scriptGearRatioCount.getInt(script)));
        float[] rawRatios = (float[]) scriptGearRatios.get(script);
        double reverse = rawRatios.length > 0 ? rawRatios[0] : Double.NaN;
        double[] forward = new double[ratioCount];
        for (int index = 0; index < ratioCount; index++) {
            int sourceIndex = index + 1;
            forward[index] = sourceIndex < rawRatios.length ? rawRatios[sourceIndex] : Double.NaN;
        }
        Object center = invoke(scriptCenterOfMass, script);
        return new ScriptVehicleData(
                (String) invoke(scriptFullName, script),
                ((Number) invoke(scriptMass, script)).doubleValue(),
                ((Number) invoke(scriptEngineForce, script)).doubleValue(),
                ((Number) invoke(scriptIdleRpm, script)).doubleValue(),
                scriptMaxSpeed.getFloat(script),
                ((Number) invoke(scriptWheelFriction, script)).doubleValue(),
                ((Number) invoke(scriptSteeringClamp, script, 0.0f)).doubleValue(),
                vectorZ.getFloat(center),
                DriveLayout.REAR,
                reverse,
                forward,
                wheels);
    }

    VehicleCondition condition(Object vehicle, ScriptVehicleData snapshot) throws ReflectiveOperationException {
        List<VehicleCondition.TireCondition> tires = new ArrayList<>();
        double scriptFriction = Math.max(0.05, snapshot.wheelFriction());
        for (ScriptVehicleData.Wheel wheel : snapshot.wheels()) {
            Object part = invoke(vehiclePart, vehicle, "Tire" + wheel.id());
            if (part == null) {
                tires.add(new VehicleCondition.TireCondition(wheel.id(), 0.0, 0.0, true, false, 0.45));
                continue;
            }
            Object item = invoke(partItem, part);
            boolean installed = item != null;
            double condition = clamp(((Number) invoke(partCondition, part)).doubleValue() / 100.0, 0.0, 1.0);
            double capacity = Math.max(1.0, ((Number) invoke(partCapacity, part)).doubleValue());
            double pressure = clamp(((Number) invoke(partContent, part)).doubleValue() / capacity, 0.0, 1.35);
            double compound = installed
                    ? clamp(((Number) invoke(itemWheelFriction, item)).doubleValue() / scriptFriction, 0.45, 1.65)
                    : 0.45;
            tires.add(new VehicleCondition.TireCondition(
                    wheel.id(), condition, pressure, condition <= 0.0 || pressure <= 0.02, installed, compound));
        }
        double engine = partCondition(vehicle, List.of("Engine"), 1.0);
        List<String> brakeIds = snapshot.wheels().stream().map(wheel -> "Brake" + wheel.id()).toList();
        List<String> suspensionIds = snapshot.wheels().stream().map(wheel -> "Suspension" + wheel.id()).toList();
        double brakes = partCondition(vehicle, brakeIds, 1.0);
        double suspension = partCondition(vehicle, suspensionIds, 1.0);
        double totalMass = ((Number) invoke(vehicleMass, vehicle)).doubleValue();
        double payload = Math.max(0.0, totalMass - snapshot.massKg());
        return new VehicleCondition(engine, brakes, suspension, payload, tires);
    }

    private double partCondition(Object vehicle, List<String> ids, double fallback)
            throws ReflectiveOperationException {
        double sum = 0.0;
        int found = 0;
        for (String id : ids) {
            Object part = invoke(vehiclePart, vehicle, id);
            if (part != null) {
                sum += clamp(((Number) invoke(partCondition, part)).doubleValue() / 100.0, 0.0, 1.0);
                found++;
            }
        }
        return found == 0 ? fallback : sum / found;
    }

    Controls controls(Object controller, Object vehicle) throws ReflectiveOperationException {
        Object controls = controllerControls.get(controller);
        double analog = (Boolean) invoke(vehicleKeyboardControlled, vehicle)
                ? 1.0
                : clamp(((Number) invoke(vehicleGetThrottle, vehicle)).doubleValue(), 0.0, 1.0);
        return new Controls(
                controlsForward.getBoolean(controls),
                controlsBackward.getBoolean(controls),
                controlsBrake.getBoolean(controls),
                nativeSteeringInput(controlsSteering.getFloat(controls)),
                analog);
    }

    /** PZ clientControls uses the opposite sign to Bullet/currentSteering. */
    static double nativeSteeringInput(double controllerValue) {
        return -clamp(controllerValue, -1.0, 1.0);
    }

    MotionSample motion(Object vehicle) throws ReflectiveOperationException {
        Object forward = vectorClass.getConstructor().newInstance();
        invoke(vehicleForwardVector, vehicle, forward);
        double fx = vectorX.getFloat(forward);
        double fy = vectorY.getFloat(forward);
        double fz = vectorZ.getFloat(forward);
        double horizontal = Math.max(1.0e-6, Math.hypot(fx, fz));
        fx /= horizontal;
        fz /= horizontal;
        Object velocity = vehicleVelocity.get(vehicle);
        double vx = vectorX.getFloat(velocity);
        double vz = vectorZ.getFloat(velocity);
        return new MotionSample(
                vx * fx + vz * fz,
                vx * -fz + vz * fx,
                Math.atan2(fz, fx),
                Math.asin(clamp(fy, -0.645, 0.645)),
                fx,
                fz);
    }

    double surfaceGrip(Object vehicle) throws ReflectiveOperationException {
        double grip = (Boolean) invoke(vehicleForest, vehicle) ? 0.52
                : (Boolean) invoke(vehicleOffroad, vehicle) ? 0.68 : 1.0;
        Object climate = invoke(climateInstance, null);
        if (climate != null) {
            double rain = clamp(((Number) invoke(climateRain, climate)).doubleValue(), 0.0, 1.0);
            double snow = clamp(((Number) invoke(climateSnow, climate)).doubleValue(), 0.0, 1.0);
            grip *= 1.0 - rain * 0.28;
            grip *= 1.0 - snow * 0.52;
        }
        return clamp(grip, 0.18, 1.0);
    }

    boolean engineRunning(Object vehicle) throws ReflectiveOperationException {
        return (Boolean) invoke(vehicleEngineRunning, vehicle);
    }

    double engineRpm(Object vehicle) throws ReflectiveOperationException {
        return ((Number) invoke(vehicleEngineSpeed, vehicle)).doubleValue();
    }

    boolean engineSounding(Object vehicle) throws ReflectiveOperationException {
        return (Boolean) invoke(vehicleEngineSounding, vehicle);
    }

    double controllerEngineForce(Object controller) throws IllegalAccessException {
        return controllerEngineForce.getFloat(controller);
    }

    int currentGear(Object vehicle) throws ReflectiveOperationException {
        return ((Number) invoke(vehicleTransmission, vehicle)).intValue();
    }

    void setGear(Object vehicle, int gear) throws ReflectiveOperationException {
        String name = gear < 0 ? "R" : gear == 0 ? "N" : "Speed" + Math.min(8, gear);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object transmission = Enum.valueOf((Class<? extends Enum>) transmissionClass.asSubclass(Enum.class), name);
        // changeTransmission() starts the native automatic shift transition.
        // Repeating that transition while our manual selector holds a gear can
        // suppress propulsion. The V2 drivetrain already owns the shift, so
        // only mirror its result into the field used by the HUD and audio.
        vehicleTransmissionState.set(vehicle, transmission);
    }

    void overrideController(Object controller, Object vehicle, double steering) throws ReflectiveOperationException {
        // Keep the values calculated by CarController available to B42's
        // VehicleLoad/engine-sound code. Only the Bullet command is zeroed, so
        // vanilla propulsion remains replaced without silencing engine audio.
        float audioEngineForce = controllerEngineForce.getFloat(controller);
        float audioBrakingForce = controllerBrakingForce.getFloat(controller);
        invoke(vehicleSetSteering, vehicle, (float) steering);
        invoke(bulletControl, null, vehicleId(vehicle), 0.0f, 0.0f, (float) steering);
        controllerEngineForce.setFloat(controller, audioEngineForce);
        controllerBrakingForce.setFloat(controller, audioBrakingForce);
    }

    void applyBodyForces(Object vehicle, MotionSample motion, double longitudinal, double lateral, double yawTorque)
            throws ReflectiveOperationException {
        double rightX = -motion.forwardZ();
        double rightZ = motion.forwardX();
        float forceX = (float) (motion.forwardX() * longitudinal + rightX * lateral);
        float forceZ = (float) (motion.forwardZ() * longitudinal + rightZ * lateral);
        int id = vehicleId(vehicle);
        invoke(bulletForce, null, id, forceX, 0.0f, forceZ);
        invoke(bulletTorque, null, id, 0.0f, (float) yawTorque, 0.0f);
    }

    void applyWheelSlip(Object vehicle, double drivenWheelSpeed, double bodySpeed, boolean burnout, boolean drifting,
            DriveLayout driveLayout, double deltaSeconds) throws ReflectiveOperationException {
        if (!burnout && !drifting) {
            return;
        }
        Object script = script(vehicle);
        Object infoArray = vehicleWheelInfo.get(vehicle);
        int count = Math.min(((Number) invoke(scriptWheelCount, script)).intValue(), Array.getLength(infoArray));
        for (int index = 0; index < count; index++) {
            Object wheel = invoke(scriptWheel, script, index);
            boolean front = wheelFront.getBoolean(wheel);
            boolean driven = front ? driveLayout.frontShare() > 0.0 : driveLayout.rearShare() > 0.0;
            Object info = Array.get(infoArray, index);
            if (drifting || driven && burnout) {
                wheelSkidInfo.setFloat(info, Math.min(wheelSkidInfo.getFloat(info), drifting ? 0.28f : 0.35f));
            }
            if (driven && burnout) {
                double excess = drivenWheelSpeed - Math.copySign(Math.abs(bodySpeed), drivenWheelSpeed);
                double radius = Math.max(0.18, wheelRadius.getFloat(wheel));
                wheelRotation.setFloat(info, wheelRotation.getFloat(info) + (float) (excess / radius * deltaSeconds));
            }
        }
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field result = owner.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) throws NoSuchMethodException {
        Method result = owner.getDeclaredMethod(name, parameters);
        result.setAccessible(true);
        return result;
    }

    private static Method methodInHierarchy(Class<?> owner, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        try {
            Method publicMethod = owner.getMethod(name, parameters);
            publicMethod.setAccessible(true);
            return publicMethod;
        } catch (NoSuchMethodException ignored) {
            // Continue with non-public declarations in the concrete hierarchy.
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
            if (cause instanceof Error fatal) {
                throw fatal;
            }
            throw new IllegalStateException(cause);
        }
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    record Controls(boolean forward, boolean backward, boolean handbrake, double steering, double analogThrottle) {
    }

    record MotionSample(
            double longitudinalSpeedMps,
            double lateralSpeedMps,
            double headingRadians,
            double roadGradeRadians,
            double forwardX,
            double forwardZ) {
    }
}
