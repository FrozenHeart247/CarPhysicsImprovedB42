package zombie.roadcraft.runtime;

import zombie.roadcraft.physics.RoadcraftCalibration;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

/**
 * Versioned reflection adapter for the exact B42.20.4 class contract.
 *
 * <p>The project is compiled without game classes. All version-sensitive
 * private access lives in this one adapter and is validated by the B42.20.4
 * construction smoke test.</p>
 */
final class PzAccess {
    private static final Object BULLET_LOCK = new Object();
    private static final MethodType CONTROL_VEHICLE_TYPE = MethodType.methodType(
            void.class,
            int.class,
            float.class,
            float.class,
            float.class);
    private static volatile MethodHandle fallbackControlVehicle;

    private final Field controllerVehicle;
    private final Field controllerClientControls;
    private final Field controllerEngineForce;
    private final Field controllerBrakingForce;
    private final Method controllerPark;
    private final Field controlForward;
    private final Field controlBackward;
    private final Field controlBrake;
    private final Field controlSteering;
    private final Field gameClientClient;
    private final Field gameServerServer;
    private final Method gameKeyboardIsKeyDown;
    private final Field joypadManagerInstance;
    private final Method joypadIsRightTriggerPressed;
    private final Method joypadIsLeftTriggerPressed;
    private final Method joypadIsBPressed;
    private final Method joypadGetMovementAxisX;

    private final Method vehicleIsLocalPhysics;
    private final Method vehicleIsKeyboardControlled;
    private final Method vehicleGetJoypad;
    private final Method vehicleGetDriver;
    private final Method vehicleGetTowedBy;
    private final Method vehicleGetTowing;
    private final Method vehicleGetController;
    private final Method vehicleGetTowAttachmentSelf;
    private final Method vehicleAddPointConstraint;
    private final Method vehicleGetClosestPointOnPoly;
    private final Method vehicleGetX;
    private final Method vehicleGetY;
    private final Method vehicleGetZ;
    private final Method vehicleGetScriptName;
    private final Method vehicleGetSpeedKph;
    private final Method vehicleGetMass;
    private final Method vehicleGetEnginePower;
    private final Method vehicleGetEngineSpeed;
    private final Method vehicleSetEngineSpeed;
    private final Method vehicleIsEngineRunning;
    private final Method vehicleTryStartEngine;
    private final Method vehicleSetStoplightsOn;
    private final Method vehicleHasBackSignal;
    private final Method vehicleIsBackSignalEmitting;
    private final Method vehicleBackSignalStart;
    private final Method vehicleBackSignalStop;
    private final Method vehicleSetPhysicsActive;
    private final Method vehicleIsAtRest;
    private final Method vehicleSetBraking;
    private final Method vehicleGetScript;
    private final Method vehicleGetBrakingForce;
    private final Method vehicleGetPartById;
    private final Method vehicleIsOffroad;
    private final Method vehicleIsInForest;
    private final Method vehicleGetTransmissionNumber;
    private final Method vehicleChangeTransmission;
    private final Method vehicleGetCurrentSteering;
    private final Method vehicleSetCurrentSteering;
    private final Field vehicleId;
    private final Field vehicleThrottle;
    private final Field vehicleVelocity;
    private final Field vehicleWheelInfo;
    private final Field vehicleImpulses;
    private final Field vehiclePhysicsActiveCheck;
    private final Field vehicleSavedPhysicsZ;
    private final Field vehicleJniTransform;
    private final Field vehicleSavedRotation;

    private final Method scriptGetWheelCount;
    private final Method scriptGetWheel;
    private final Method scriptGetModelOffset;
    private final Method scriptGetCenterOfMassOffset;
    private final Method scriptGetExtents;
    private final Method scriptGetFullName;
    private final Method scriptGetEngineIdleSpeed;
    private final Method scriptGetMechanicType;
    private final Method scriptGetOffroadEfficiency;
    private final Method scriptGetSteeringClamp;
    private final Field scriptMaxSpeed;
    private final Field scriptGearRatioCount;
    private final Field scriptWheelId;
    private final Field scriptWheelRadius;
    private final Field scriptWheelFront;
    private final Method scriptWheelGetOffset;

    private final Method partGetInventoryItem;
    private final Method partGetContainerContentAmount;
    private final Method partGetContainerCapacity;
    private final Method partGetCondition;
    private final Method itemGetWheelFriction;

    private final Method climateGetInstance;
    private final Method climateGetRainIntensity;
    private final Method climateGetSnowStrength;

    private final Field worldInstance;
    private final Method worldGetCell;
    private final Method cellGetVehicles;

    private final Method gameTimeGetInstance;
    private final Method gameTimeGetPhysicsDelta;

    private final Method bulletAddVehicle;
    private final Method bulletApplyCentralForce;
    private final Method bulletSetVehicleMass;

    private final Field impulseVector;

    private final Constructor<?> vector2Constructor;
    private final Field vectorX;
    private final Field vectorY;
    private final Field vectorZ;
    private final Field transformOrigin;
    private final Field quaternionX;
    private final Field quaternionY;
    private final Field quaternionZ;
    private final Field quaternionW;
    private final Field wheelRotation;
    private final Field wheelSkidInfo;

    private final Class<?> transmissionClass;
    private volatile double currentMassScale = 1.0;

    PzAccess() throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> controllerClass = Class.forName("zombie.core.physics.CarController", false, loader);
        Class<?> controlsClass = Class.forName("zombie.core.physics.CarController$ClientControls", false, loader);
        Class<?> gameClientClass = Class.forName("zombie.network.GameClient", false, loader);
        Class<?> gameServerClass = Class.forName("zombie.network.GameServer", false, loader);
        Class<?> gameTimeClass = Class.forName("zombie.GameTime", false, loader);
        Class<?> gameKeyboardClass = Class.forName("zombie.input.GameKeyboard", false, loader);
        Class<?> joypadManagerClass = Class.forName("zombie.input.JoypadManager", false, loader);
        Class<?> vehicleClass = Class.forName("zombie.vehicles.BaseVehicle", false, loader);
        Class<?> scriptClass = Class.forName("zombie.scripting.objects.VehicleScript", false, loader);
        Class<?> scriptWheelClass = Class.forName("zombie.scripting.objects.VehicleScript$Wheel", false, loader);
        Class<?> partClass = Class.forName("zombie.vehicles.VehiclePart", false, loader);
        Class<?> itemClass = Class.forName("zombie.inventory.InventoryItem", false, loader);
        Class<?> climateClass = Class.forName("zombie.iso.weather.ClimateManager", false, loader);
        Class<?> worldClass = Class.forName("zombie.iso.IsoWorld", false, loader);
        Class<?> cellClass = Class.forName("zombie.iso.IsoCell", false, loader);
        Class<?> bulletClass = Class.forName("zombie.core.physics.Bullet", false, loader);
        Class<?> transformClass = Class.forName("zombie.core.physics.Transform", false, loader);
        Class<?> vectorClass = Class.forName("org.joml.Vector3f", false, loader);
        Class<?> vector2Class = Class.forName("org.joml.Vector2f", false, loader);
        Class<?> quaternionClass = Class.forName("org.joml.Quaternionf", false, loader);
        Class<?> isoPlayerClass = Class.forName("zombie.characters.IsoPlayer", false, loader);
        Class<?> wheelInfoClass = Class.forName("zombie.vehicles.BaseVehicle$WheelInfo", false, loader);
        Class<?> vehicleImpulseClass = Class.forName("zombie.vehicles.BaseVehicle$VehicleImpulse", false, loader);
        transmissionClass = Class.forName("zombie.vehicles.TransmissionNumber", false, loader);

        controllerVehicle = field(controllerClass, "vehicleObject");
        controllerClientControls = field(controllerClass, "clientControls");
        controllerEngineForce = field(controllerClass, "engineForce");
        controllerBrakingForce = field(controllerClass, "brakingForce");
        controllerPark = method(controllerClass, "park");
        controlForward = field(controlsClass, "forward");
        controlBackward = field(controlsClass, "backward");
        controlBrake = field(controlsClass, "brake");
        controlSteering = field(controlsClass, "steering");
        gameClientClient = field(gameClientClass, "client");
        gameServerServer = field(gameServerClass, "server");
        gameKeyboardIsKeyDown = method(gameKeyboardClass, "isKeyDown", String.class);
        joypadManagerInstance = field(joypadManagerClass, "instance");
        joypadIsRightTriggerPressed = method(joypadManagerClass, "isRTPressed", int.class);
        joypadIsLeftTriggerPressed = method(joypadManagerClass, "isLTPressed", int.class);
        joypadIsBPressed = method(joypadManagerClass, "isBPressed", int.class);
        joypadGetMovementAxisX = method(joypadManagerClass, "getMovementAxisX", int.class);

        vehicleIsLocalPhysics = method(vehicleClass, "isLocalPhysicSim");
        vehicleIsKeyboardControlled = method(vehicleClass, "isKeyboardControlled");
        vehicleGetJoypad = method(vehicleClass, "getJoypad");
        vehicleGetDriver = method(vehicleClass, "getDriver");
        vehicleGetTowedBy = method(vehicleClass, "getVehicleTowedBy");
        vehicleGetTowing = method(vehicleClass, "getVehicleTowing");
        vehicleGetController = method(vehicleClass, "getController");
        vehicleGetTowAttachmentSelf = method(vehicleClass, "getTowAttachmentSelf");
        vehicleAddPointConstraint = method(
                vehicleClass,
                "addPointConstraint",
                isoPlayerClass,
                vehicleClass,
                String.class,
                String.class);
        vehicleGetClosestPointOnPoly = method(
                vehicleClass,
                "getClosestPointOnPoly",
                vehicleClass,
                vector2Class,
                vector2Class);
        vehicleGetX = methodInHierarchy(vehicleClass, "getX");
        vehicleGetY = methodInHierarchy(vehicleClass, "getY");
        vehicleGetZ = methodInHierarchy(vehicleClass, "getZ");
        vehicleGetScriptName = method(vehicleClass, "getScriptName");
        vehicleGetSpeedKph = method(vehicleClass, "getCurrentSpeedKmHour");
        vehicleGetMass = method(vehicleClass, "getMass");
        vehicleGetEnginePower = method(vehicleClass, "getEnginePower");
        vehicleGetEngineSpeed = method(vehicleClass, "getEngineSpeed");
        vehicleSetEngineSpeed = method(vehicleClass, "setEngineSpeed", double.class);
        vehicleIsEngineRunning = method(vehicleClass, "isEngineRunning");
        vehicleTryStartEngine = method(vehicleClass, "tryStartEngine");
        vehicleSetStoplightsOn = method(vehicleClass, "setStoplightsOn", boolean.class);
        vehicleHasBackSignal = method(vehicleClass, "hasBackSignal");
        vehicleIsBackSignalEmitting = method(vehicleClass, "isBackSignalEmitting");
        vehicleBackSignalStart = method(vehicleClass, "onBackMoveSignalStart");
        vehicleBackSignalStop = method(vehicleClass, "onBackMoveSignalStop");
        vehicleSetPhysicsActive = method(vehicleClass, "setPhysicsActive", boolean.class);
        vehicleIsAtRest = method(vehicleClass, "isAtRest");
        vehicleSetBraking = method(vehicleClass, "setBraking", boolean.class);
        vehicleGetScript = method(vehicleClass, "getScript");
        vehicleGetBrakingForce = method(vehicleClass, "getBrakingForce");
        vehicleGetPartById = methodInHierarchy(vehicleClass, "getPartById", String.class);
        vehicleIsOffroad = method(vehicleClass, "isDoingOffroad");
        vehicleIsInForest = method(vehicleClass, "isInForest");
        vehicleGetTransmissionNumber = method(vehicleClass, "getTransmissionNumber");
        vehicleChangeTransmission = method(vehicleClass, "changeTransmission", transmissionClass);
        vehicleGetCurrentSteering = method(vehicleClass, "getCurrentSteering");
        vehicleSetCurrentSteering = method(vehicleClass, "setCurrentSteering", float.class);
        vehicleId = field(vehicleClass, "vehicleId");
        vehicleThrottle = field(vehicleClass, "throttle");
        vehicleVelocity = field(vehicleClass, "jniLinearVelocity");
        vehicleWheelInfo = field(vehicleClass, "wheelInfo");
        vehicleImpulses = field(vehicleClass, "impulsesFromHitObjects");
        vehiclePhysicsActiveCheck = field(vehicleClass, "physicActiveCheck");
        vehicleSavedPhysicsZ = field(vehicleClass, "savedPhysicsZ");
        vehicleJniTransform = field(vehicleClass, "jniTransform");
        vehicleSavedRotation = field(vehicleClass, "savedRot");

        scriptGetWheelCount = method(scriptClass, "getWheelCount");
        scriptGetWheel = method(scriptClass, "getWheel", int.class);
        scriptGetModelOffset = method(scriptClass, "getModelOffset");
        scriptGetCenterOfMassOffset = method(scriptClass, "getCenterOfMassOffset");
        scriptGetExtents = method(scriptClass, "getExtents");
        scriptGetFullName = method(scriptClass, "getFullName");
        scriptGetEngineIdleSpeed = method(scriptClass, "getEngineIdleSpeed");
        scriptGetMechanicType = method(scriptClass, "getMechanicType");
        scriptGetOffroadEfficiency = method(scriptClass, "getOffroadEfficiency");
        scriptGetSteeringClamp = method(scriptClass, "getSteeringClamp", float.class);
        scriptMaxSpeed = field(scriptClass, "maxSpeed");
        scriptGearRatioCount = field(scriptClass, "gearRatioCount");
        scriptWheelId = field(scriptWheelClass, "id");
        scriptWheelRadius = field(scriptWheelClass, "radius");
        scriptWheelFront = field(scriptWheelClass, "front");
        scriptWheelGetOffset = method(scriptWheelClass, "getOffset");

        partGetInventoryItem = method(partClass, "getInventoryItem");
        partGetContainerContentAmount = method(partClass, "getContainerContentAmount");
        partGetContainerCapacity = method(partClass, "getContainerCapacity");
        partGetCondition = method(partClass, "getCondition");
        itemGetWheelFriction = method(itemClass, "getWheelFriction");

        climateGetInstance = method(climateClass, "getInstance");
        climateGetRainIntensity = method(climateClass, "getRainIntensity");
        climateGetSnowStrength = method(climateClass, "getSnowStrength");

        worldInstance = field(worldClass, "instance");
        worldGetCell = method(worldClass, "getCell");
        cellGetVehicles = method(cellClass, "getVehicles");

        gameTimeGetInstance = method(gameTimeClass, "getInstance");
        gameTimeGetPhysicsDelta = method(gameTimeClass, "getPhysicsSecondsSinceLastUpdate");

        MethodHandle controlVehicleHandle = controlVehicleHandle(bulletClass);
        bulletAddVehicle = method(
                bulletClass,
                "addVehicle",
                int.class,
                float.class,
                float.class,
                float.class,
                float.class,
                float.class,
                float.class,
                float.class,
                String.class);
        bulletApplyCentralForce = method(bulletClass, "applyCentralForceToVehicle", int.class, float.class, float.class, float.class);
        bulletSetVehicleMass = method(bulletClass, "setVehicleMass", int.class, float.class);
        fallbackControlVehicle = controlVehicleHandle;

        impulseVector = field(vehicleImpulseClass, "impulse");
        vector2Constructor = vector2Class.getConstructor();
        vectorX = field(vectorClass, "x");
        vectorY = field(vectorClass, "y");
        vectorZ = field(vectorClass, "z");
        transformOrigin = field(transformClass, "origin");
        quaternionX = field(quaternionClass, "x");
        quaternionY = field(quaternionClass, "y");
        quaternionZ = field(quaternionClass, "z");
        quaternionW = field(quaternionClass, "w");
        wheelRotation = field(wheelInfoClass, "rotation");
        wheelSkidInfo = field(wheelInfoClass, "skidInfo");
    }

    Object vehicle(Object controller) throws ReflectiveOperationException {
        return controllerVehicle.get(controller);
    }

    int vehicleId(Object vehicle) throws ReflectiveOperationException {
        return ((Number) vehicleId.get(vehicle)).intValue();
    }

    void initializeVehiclePhysics(Object vehicle) throws ReflectiveOperationException {
        if (gameServerServer.getBoolean(null)) {
            return;
        }

        Object script = invoke(vehicleGetScript, vehicle);
        if (script == null) {
            throw new IllegalStateException("VehicleScript is unavailable during native vehicle registration");
        }

        float physicsZ = vehicleSavedPhysicsZ.getFloat(vehicle);
        if (Float.isNaN(physicsZ)) {
            float wheelBottom = 0.0f;
            int wheelCount = ((Number) invoke(scriptGetWheelCount, script)).intValue();
            if (wheelCount > 0) {
                Object modelOffset = invoke(scriptGetModelOffset, script);
                Object firstWheel = invoke(scriptGetWheel, script, 0);
                Object wheelOffset = invoke(scriptWheelGetOffset, firstWheel);
                wheelBottom = vectorY.getFloat(modelOffset)
                        + vectorY.getFloat(wheelOffset)
                        - scriptWheelRadius.getFloat(firstWheel);
            }

            Object centerOfMass = invoke(scriptGetCenterOfMassOffset, script);
            Object extents = invoke(scriptGetExtents, script);
            float chassisBottom = vectorY.getFloat(centerOfMass) - vectorY.getFloat(extents) / 2.0f;
            float vehicleZ = ((Number) invoke(vehicleGetZ, vehicle)).floatValue();
            float levelBase = (float) Math.floor(vehicleZ) * 3.0f * 0.8164967f;
            physicsZ = levelBase - Math.min(wheelBottom, chassisBottom);
            if (wheelCount == 0) {
                physicsZ = Math.max(physicsZ, levelBase + 0.1f);
            }
        }

        Object transform = vehicleJniTransform.get(vehicle);
        Object origin = transformOrigin.get(transform);
        vectorY.setFloat(origin, physicsZ);

        Object rotation = vehicleSavedRotation.get(vehicle);
        invoke(
                bulletAddVehicle,
                null,
                vehicleId(vehicle),
                ((Number) invoke(vehicleGetX, vehicle)).floatValue(),
                ((Number) invoke(vehicleGetY, vehicle)).floatValue(),
                physicsZ,
                quaternionX.getFloat(rotation),
                quaternionY.getFloat(rotation),
                quaternionZ.getFloat(rotation),
                quaternionW.getFloat(rotation),
                (String) invoke(scriptGetFullName, script));
    }

    boolean hasControlAuthority(Object vehicle) throws ReflectiveOperationException {
        return RoadcraftCalibration.hasControlAuthority(
                gameClientClient.getBoolean(null),
                gameServerServer.getBoolean(null),
                (Boolean) invoke(vehicleIsLocalPhysics, vehicle));
    }

    boolean isDedicatedServer() throws IllegalAccessException {
        return gameServerServer.getBoolean(null);
    }

    boolean isClient() throws IllegalAccessException {
        return gameClientClient.getBoolean(null);
    }

    PlayerControls playerControls(Object vehicle, long forceBrake) throws ReflectiveOperationException {
        if (isDedicatedServer()) {
            return PlayerControls.unavailable(forceBrake);
        }

        boolean available = false;
        float steering = 0.0f;
        boolean forward = false;
        boolean backward = false;
        boolean brake = false;
        boolean shift = false;

        if ((Boolean) invoke(vehicleIsKeyboardControlled, vehicle)) {
            available = true;
            boolean left = (Boolean) invoke(gameKeyboardIsKeyDown, null, "Left");
            boolean right = (Boolean) invoke(gameKeyboardIsKeyDown, null, "Right");
            steering = (left ? -1.0f : 0.0f) + (right ? 1.0f : 0.0f);
            forward = (Boolean) invoke(gameKeyboardIsKeyDown, null, "Forward");
            backward = (Boolean) invoke(gameKeyboardIsKeyDown, null, "Backward");
            brake = (Boolean) invoke(gameKeyboardIsKeyDown, null, "Brake");
            shift = (Boolean) invoke(gameKeyboardIsKeyDown, null, "CruiseControl");
        }

        int joypad = ((Number) invoke(vehicleGetJoypad, vehicle)).intValue();
        if (joypad != -1) {
            Object manager = joypadManagerInstance.get(null);
            if (manager != null) {
                available = true;
                steering = ((Number) invoke(joypadGetMovementAxisX, manager, joypad)).floatValue();
                forward = (Boolean) invoke(joypadIsRightTriggerPressed, manager, joypad);
                backward = (Boolean) invoke(joypadIsLeftTriggerPressed, manager, joypad);
                brake = (Boolean) invoke(joypadIsBPressed, manager, joypad);
            }
        }

        if (forceBrake != 0L) {
            long elapsed = System.currentTimeMillis() - forceBrake;
            if (elapsed > 0L && elapsed < 1_000L) {
                available = true;
                brake = true;
                shift = false;
            }
        }
        return new PlayerControls(available, steering, forward, backward, brake, shift, forceBrake);
    }

    void tryStartEngine(Object vehicle) throws ReflectiveOperationException {
        invoke(vehicleTryStartEngine, vehicle);
    }

    void setStoplights(Object vehicle, boolean active) throws ReflectiveOperationException {
        invoke(vehicleSetStoplightsOn, vehicle, active);
        invoke(vehicleSetBraking, vehicle, active);
    }

    void setBackSignal(Object vehicle, boolean active) throws ReflectiveOperationException {
        if (!(Boolean) invoke(vehicleHasBackSignal, vehicle)) {
            return;
        }
        if ((Boolean) invoke(vehicleIsBackSignalEmitting, vehicle) == active) {
            return;
        }
        invoke(active ? vehicleBackSignalStart : vehicleBackSignalStop, vehicle);
    }

    void setPhysicsActive(Object vehicle, boolean active) throws ReflectiveOperationException {
        invoke(vehicleSetPhysicsActive, vehicle, active);
    }

    boolean isAtRest(Object vehicle) throws ReflectiveOperationException {
        return (Boolean) invoke(vehicleIsAtRest, vehicle);
    }

    boolean hasPendingPhysicsActiveCheck(Object vehicle) throws IllegalAccessException {
        return vehiclePhysicsActiveCheck.getLong(vehicle) != -1L;
    }

    float physicsDeltaSeconds() throws ReflectiveOperationException {
        Object gameTime = invoke(gameTimeGetInstance, null);
        if (gameTime == null) {
            return 1.0f / 60.0f;
        }
        float delta = ((Number) invoke(gameTimeGetPhysicsDelta, gameTime)).floatValue();
        return Float.isFinite(delta) && delta > 0.0f ? Math.min(delta, 0.10f) : 1.0f / 60.0f;
    }

    boolean hasDriver(Object vehicle) throws ReflectiveOperationException {
        return invoke(vehicleGetDriver, vehicle) != null;
    }

    boolean isTowed(Object vehicle) throws ReflectiveOperationException {
        return invoke(vehicleGetTowedBy, vehicle) != null;
    }

    Object towedByVehicle(Object vehicle) throws ReflectiveOperationException {
        return invoke(vehicleGetTowedBy, vehicle);
    }

    Object towingVehicle(Object vehicle) throws ReflectiveOperationException {
        return invoke(vehicleGetTowing, vehicle);
    }

    Object controller(Object vehicle) throws ReflectiveOperationException {
        return vehicle == null ? null : invoke(vehicleGetController, vehicle);
    }

    float controllerEngineForce(Object controller) throws IllegalAccessException {
        return controller == null ? 0.0f : controllerEngineForce.getFloat(controller);
    }

    float controllerBrakingForce(Object controller) throws IllegalAccessException {
        return controller == null ? 0.0f : controllerBrakingForce.getFloat(controller);
    }

    float towingEngineForce(Object vehicle) throws ReflectiveOperationException {
        Object towingController = controller(towedByVehicle(vehicle));
        return towingController == null ? 0.0f : controllerEngineForce(towingController);
    }

    float baseTrailerBrake(Object vehicle) throws ReflectiveOperationException {
        String scriptName = (String) invoke(vehicleGetScriptName, vehicle);
        return scriptName != null && scriptName.contains("Trailer") ? 0.0f : 10.0f;
    }

    boolean hasPlayerDrivenVehicleNearby(Object vehicle) throws ReflectiveOperationException {
        Object world = worldInstance.get(null);
        if (world == null) {
            return false;
        }
        Object cell = invoke(worldGetCell, world);
        if (cell == null) {
            return false;
        }
        Object rawVehicles = invoke(cellGetVehicles, cell);
        if (!(rawVehicles instanceof Collection<?> vehicles)) {
            return false;
        }

        Object firstPoint = vector2Constructor.newInstance();
        Object secondPoint = vector2Constructor.newInstance();
        for (Object candidate : vehicles) {
            if (candidate == null || candidate == vehicle || !hasDriver(candidate)) {
                continue;
            }
            float distanceSquared = ((Number) invoke(
                    vehicleGetClosestPointOnPoly,
                    vehicle,
                    candidate,
                    firstPoint,
                    secondPoint)).floatValue();
            if (Float.isFinite(distanceSquared) && distanceSquared < 9.0f) {
                return true;
            }
        }
        return false;
    }

    /** Mirrors the B42.20.4 server/SP tow-constraint repair before trailer control. */
    boolean prepareTrailerUpdate(Object trailer) throws ReflectiveOperationException {
        Object towingVehicle = towedByVehicle(trailer);
        if (towingVehicle == null) {
            return false;
        }

        boolean driverMovedToTrailer = !hasDriver(towingVehicle) && hasDriver(trailer);
        if (isDedicatedServer()) {
            if (driverMovedToTrailer) {
                repairTrailerConstraint(trailer, towingVehicle);
            }
            return false;
        }
        if (driverMovedToTrailer && !isClient()) {
            repairTrailerConstraint(trailer, towingVehicle);
            return false;
        }
        return true;
    }

    void parkTowingVehicle(Object vehicle) throws ReflectiveOperationException {
        Object trailer = towingVehicle(vehicle);
        if (trailer == null || trailer == vehicle) {
            return;
        }
        Object trailerController = controller(trailer);
        if (trailerController != null) {
            invoke(controllerPark, trailerController);
        }
    }

    private void repairTrailerConstraint(Object trailer, Object towingVehicle)
            throws ReflectiveOperationException {
        String trailerAttachment = (String) invoke(vehicleGetTowAttachmentSelf, trailer);
        String towingAttachment = (String) invoke(vehicleGetTowAttachmentSelf, towingVehicle);
        invoke(
                vehicleAddPointConstraint,
                trailer,
                null,
                towingVehicle,
                trailerAttachment,
                towingAttachment);
    }

    boolean engineRunning(Object vehicle) throws ReflectiveOperationException {
        return (Boolean) invoke(vehicleIsEngineRunning, vehicle);
    }

    double speedKph(Object vehicle) throws ReflectiveOperationException {
        return ((Number) invoke(vehicleGetSpeedKph, vehicle)).doubleValue();
    }

    double massKg(Object vehicle) throws ReflectiveOperationException {
        return Math.max(1.0, ((Number) invoke(vehicleGetMass, vehicle)).doubleValue());
    }

    double enginePower(Object vehicle) throws ReflectiveOperationException {
        return Math.max(1.0, ((Number) invoke(vehicleGetEnginePower, vehicle)).doubleValue());
    }

    String scriptName(Object vehicle) throws ReflectiveOperationException {
        Object script = invoke(vehicleGetScript, vehicle);
        String fullName = (String) invoke(scriptGetFullName, script);
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        String fallback = (String) invoke(vehicleGetScriptName, vehicle);
        return fallback == null || fallback.isBlank() ? "unknown" : fallback;
    }

    double engineRpm(Object vehicle) throws ReflectiveOperationException {
        return Math.max(0.0, ((Number) invoke(vehicleGetEngineSpeed, vehicle)).doubleValue());
    }

    void setEngineRpm(Object vehicle, double rpm) throws ReflectiveOperationException {
        invoke(vehicleSetEngineSpeed, vehicle, rpm);
    }

    void setThrottle(Object vehicle, double value) throws IllegalAccessException {
        vehicleThrottle.setFloat(vehicle, (float) clamp(value, 0.0, 1.0));
    }

    double throttle(Object vehicle) throws IllegalAccessException {
        return clamp(vehicleThrottle.getFloat(vehicle), 0.0, 1.0);
    }

    boolean isKeyboardControlled(Object vehicle) throws ReflectiveOperationException {
        return (Boolean) invoke(vehicleIsKeyboardControlled, vehicle);
    }

    double baseBrakingForce(Object vehicle) throws ReflectiveOperationException {
        return Math.max(1.0, ((Number) invoke(vehicleGetBrakingForce, vehicle)).doubleValue());
    }

    double maxSpeedKph(Object vehicle) throws ReflectiveOperationException {
        Object script = invoke(vehicleGetScript, vehicle);
        return Math.max(10.0, ((Number) scriptMaxSpeed.get(script)).doubleValue());
    }

    int gearCount(Object vehicle) throws ReflectiveOperationException {
        Object script = invoke(vehicleGetScript, vehicle);
        return Math.max(1, Math.min(8, scriptGearRatioCount.getInt(script)));
    }

    double wheelRadiusMeters(Object vehicle) throws ReflectiveOperationException {
        Object script = invoke(vehicleGetScript, vehicle);
        int count = Math.max(0, ((Number) invoke(scriptGetWheelCount, script)).intValue());
        double sum = 0.0;
        int valid = 0;
        for (int index = 0; index < count; index++) {
            Object wheel = invoke(scriptGetWheel, script, index);
            double radius = ((Number) scriptWheelRadius.get(wheel)).doubleValue();
            if (Double.isFinite(radius) && radius > 0.05) {
                sum += radius;
                valid++;
            }
        }
        return valid == 0 ? 0.32 : sum / valid;
    }

    double engineIdleRpm(Object vehicle) throws ReflectiveOperationException {
        Object script = invoke(vehicleGetScript, vehicle);
        return Math.max(400.0, ((Number) invoke(scriptGetEngineIdleSpeed, script)).doubleValue());
    }

    int mechanicType(Object vehicle) throws ReflectiveOperationException {
        Object script = invoke(vehicleGetScript, vehicle);
        return ((Number) invoke(scriptGetMechanicType, script)).intValue();
    }

    double steeringClamp(Object vehicle, double speedKph) throws ReflectiveOperationException {
        Object script = invoke(vehicleGetScript, vehicle);
        double result = ((Number) invoke(scriptGetSteeringClamp, script, (float) Math.abs(speedKph))).doubleValue();
        return clamp(Math.abs(result), 0.01, Math.PI / 2.0);
    }

    TireStats tireStats(Object vehicle) throws ReflectiveOperationException {
        Object script = invoke(vehicleGetScript, vehicle);
        int count = Math.max(0, ((Number) invoke(scriptGetWheelCount, script)).intValue());
        if (count == 0) {
            return new TireStats(1.0, 1.0);
        }
        double pressureTotal = 0.0;
        double conditionTotal = 0.0;
        for (int index = 0; index < count; index++) {
            Object wheel = invoke(scriptGetWheel, script, index);
            String id = (String) scriptWheelId.get(wheel);
            Object part = invoke(vehicleGetPartById, vehicle, "Tire" + id);
            if (part == null) {
                continue;
            }
            Object item = invoke(partGetInventoryItem, part);
            if (item == null) {
                continue;
            }
            double capacity = Math.max(1.0, ((Number) invoke(partGetContainerCapacity, part)).doubleValue());
            double content = ((Number) invoke(partGetContainerContentAmount, part)).doubleValue();
            pressureTotal += clamp(content / capacity, 0.0, 1.0);

            double condition = ((Number) invoke(partGetCondition, part)).doubleValue();
            double wheelFriction = ((Number) invoke(itemGetWheelFriction, item)).doubleValue();
            conditionTotal += Math.max(0.0, condition) * Math.max(0.0, wheelFriction);
        }
        double pressure = clamp(pressureTotal / count, 0.0, 1.0);
        double condition = clamp(conditionTotal * 0.01 / count, 0.0, 2.0);
        return new TireStats(pressure, condition);
    }

    Weather weather() throws ReflectiveOperationException {
        Object climate = invoke(climateGetInstance, null);
        if (climate == null) {
            return new Weather(0.0, 0.0);
        }
        double rain = ((Number) invoke(climateGetRainIntensity, climate)).doubleValue();
        double snow = ((Number) invoke(climateGetSnowStrength, climate)).doubleValue();
        return new Weather(clamp(rain, 0.0, 1.0), clamp(snow, 0.0, 1.0));
    }

    Surface surface(Object vehicle) throws ReflectiveOperationException {
        boolean offroad = (Boolean) invoke(vehicleIsOffroad, vehicle);
        boolean forest = (Boolean) invoke(vehicleIsInForest, vehicle);
        Object script = invoke(vehicleGetScript, vehicle);
        double efficiency = ((Number) invoke(scriptGetOffroadEfficiency, script)).doubleValue();
        return new Surface(offroad, forest, forest ? 1.0 : offroad ? 0.75 : 0.0, Math.max(0.05, efficiency));
    }

    Controls controls(Object controller) throws ReflectiveOperationException {
        Object controls = controllerClientControls.get(controller);
        return new Controls(
                controlForward.getBoolean(controls),
                controlBackward.getBoolean(controls),
                controlBrake.getBoolean(controls),
                controlSteering.getFloat(controls));
    }

    int currentGear(Object vehicle) throws ReflectiveOperationException {
        return ((Number) invoke(vehicleGetTransmissionNumber, vehicle)).intValue();
    }

    void applyTransmission(Object vehicle, int gear) throws ReflectiveOperationException {
        String name = gear < 0 ? "R" : gear == 0 ? "N" : "Speed" + Math.min(8, gear);
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object transmission = Enum.valueOf((Class<? extends Enum>) transmissionClass.asSubclass(Enum.class), name);
        invoke(vehicleChangeTransmission, vehicle, transmission);
    }

    void applyControllerFields(Object controller, float engineForce, float brakingForce) throws IllegalAccessException {
        controllerEngineForce.setFloat(controller, engineForce);
        controllerBrakingForce.setFloat(controller, brakingForce);
    }

    void setCurrentSteering(Object vehicle, float steering) throws ReflectiveOperationException {
        invoke(vehicleSetCurrentSteering, vehicle, steering);
    }

    float currentSteering(Object vehicle) throws ReflectiveOperationException {
        return ((Number) invoke(vehicleGetCurrentSteering, vehicle)).floatValue();
    }

    int beginImpulse(Object vehicle) throws IllegalAccessException {
        Object raw = vehicleImpulses.get(vehicle);
        return raw instanceof List<?> impulses ? impulses.size() : 0;
    }

    void scaleImpulsesFrom(Object vehicle, int startIndex, double multiplier)
            throws IllegalAccessException {
        Object raw = vehicleImpulses.get(vehicle);
        if (!(raw instanceof List<?> impulses)) {
            return;
        }
        float scale = (float) clamp(multiplier, 0.0, 10.0);
        int start = Math.max(0, Math.min(startIndex, impulses.size()));
        for (int index = start; index < impulses.size(); index++) {
            Object entry = impulses.get(index);
            Object vector = impulseVector.get(entry);
            vectorX.setFloat(vector, vectorX.getFloat(vector) * scale);
            vectorY.setFloat(vector, vectorY.getFloat(vector) * scale);
            vectorZ.setFloat(vector, vectorZ.getFloat(vector) * scale);
        }
    }

    void updateNativeMasses() throws ReflectiveOperationException {
        Object world = worldInstance.get(null);
        if (world == null) {
            return;
        }
        Object cell = invoke(worldGetCell, world);
        if (cell == null) {
            return;
        }
        Object raw = invoke(cellGetVehicles, cell);
        if (!(raw instanceof Collection<?> vehicles)) {
            return;
        }

        double heaviestMass = 1.0;
        for (Object vehicle : vehicles) {
            if (vehicle != null) {
                heaviestMass = Math.max(heaviestMass, massKg(vehicle));
            }
        }
        double reference = RoadcraftBridge.number("dynamicMassReference", 750.0);
        float scale = (float) RoadcraftCalibration.dynamicMassScale(reference, heaviestMass);
        currentMassScale = scale;
        for (Object vehicle : vehicles) {
            if (vehicle == null || !hasControlAuthority(vehicle)) {
                continue;
            }
            float targetMass = (float) (massKg(vehicle) * scale);
            invoke(bulletSetVehicleMass, null, vehicleId(vehicle), targetMass);
        }
    }

    double massScale() {
        return currentMassScale;
    }

    void applyWheelVisuals(
            Object vehicle,
            WheelVisualState state,
            double burnoutAmount,
            boolean parkingBrake,
            double deltaSeconds) throws ReflectiveOperationException {
        Object script = invoke(vehicleGetScript, vehicle);
        int count = Math.max(0, ((Number) invoke(scriptGetWheelCount, script)).intValue());
        Object wheelInfo = vehicleWheelInfo.get(vehicle);
        int available = Array.getLength(wheelInfo);
        int usable = Math.min(count, available);
        state.ensureSize(usable);

        double skidIntensity = Math.abs(burnoutAmount) * 0.10;
        for (int index = 0; index < usable; index++) {
            Object wheel = invoke(scriptGetWheel, script, index);
            if (scriptWheelFront.getBoolean(wheel)) {
                continue;
            }
            Object info = Array.get(wheelInfo, index);
            skidIntensity += clamp((1.0 - wheelSkidInfo.getFloat(info)) - 0.30, 0.0, 0.50);
        }
        skidIntensity = clamp(skidIntensity, 0.0, 1.0);
        double delta = clamp(deltaSeconds, 1.0 / 240.0, 0.10);

        for (int index = 0; index < usable; index++) {
            Object wheel = invoke(scriptGetWheel, script, index);
            if (scriptWheelFront.getBoolean(wheel)) {
                continue;
            }
            Object info = Array.get(wheelInfo, index);
            double currentRotation = wheelRotation.getFloat(info);
            if (!state.initialized[index]) {
                state.accumulatedRotation[index] = currentRotation;
                state.lastNativeRotation[index] = currentRotation;
                state.lastWrittenRotation[index] = currentRotation;
                state.initialized[index] = true;
            }

            double rotationDelta = wheelRotationDelta(
                    currentRotation,
                    state.lastNativeRotation[index],
                    state.lastWrittenRotation[index]);
            // Preserve the raw Bullet readback, not the visual value written below.
            if (hasNativeWheelReadback(currentRotation, state.lastWrittenRotation[index])) {
                state.lastNativeRotation[index] = currentRotation;
            }
            if (parkingBrake && skidIntensity > 0.20) {
                rotationDelta = 0.0;
            } else {
                rotationDelta += burnoutAmount * delta;
            }
            state.accumulatedRotation[index] += rotationDelta;
            wheelRotation.setFloat(info, (float) state.accumulatedRotation[index]);
            state.lastWrittenRotation[index] = state.accumulatedRotation[index];
            if (Math.abs(state.accumulatedRotation[index]) > 1_000.0) {
                state.accumulatedRotation[index] = 0.0;
            }

            // Keep the base game's contact effects informed between native updates.
            if (Math.abs(burnoutAmount) > 0.01) {
                wheelSkidInfo.setFloat(info, Math.min(wheelSkidInfo.getFloat(info), 0.35f));
            }
        }
    }

    /** Positive force opposes motion; negative force compensates native damping. */
    void applyDrag(Object vehicle, double nativeForce) throws ReflectiveOperationException {
        if (!Double.isFinite(nativeForce) || Math.abs(nativeForce) < 1.0e-6) {
            return;
        }
        Object vector = vehicleVelocity.get(vehicle);
        double x = vectorX.getFloat(vector);
        double z = vectorZ.getFloat(vector);
        double length = Math.hypot(x, z);
        if (length < 1.0e-4) {
            return;
        }
        float forceX = (float) (-x / length * nativeForce);
        float forceZ = (float) (-z / length * nativeForce);
        invoke(bulletApplyCentralForce, null, vehicleId(vehicle), forceX, 0.0f, forceZ);
    }

    static void fallbackControlVehicle(int id, float engineForce, float brakingForce, float steering) {
        try {
            MethodHandle target = fallbackControlVehicle;
            if (target == null) {
                synchronized (BULLET_LOCK) {
                    target = fallbackControlVehicle;
                    if (target == null) {
                        Class<?> bullet = Class.forName("zombie.core.physics.Bullet");
                        target = controlVehicleHandle(bullet);
                        fallbackControlVehicle = target;
                    }
                }
            }
            target.invokeExact(id, engineForce, brakingForce, steering);
        } catch (Throwable error) {
            RoadcraftBridge.markFatal("Unable to dispatch vanilla Bullet.controlVehicle: " + error);
            throw new IllegalStateException("Vanilla vehicle control dispatch failed", error);
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
            // Fall through to non-public declarations in the class hierarchy.
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

    private static MethodHandle controlVehicleHandle(Class<?> bulletClass) throws ReflectiveOperationException {
        Method target = method(
                bulletClass,
                "controlVehicle",
                int.class,
                float.class,
                float.class,
                float.class);
        return MethodHandles.lookup().unreflect(target).asType(CONTROL_VEHICLE_TYPE);
    }

    private static Object invoke(Method method, Object receiver, Object... arguments) throws ReflectiveOperationException {
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

    static double wheelRotationDelta(double current, double lastNative, double lastWritten) {
        if (!Double.isFinite(current)
                || !Double.isFinite(lastNative)
                || !Double.isFinite(lastWritten)) {
            return 0.0;
        }
        // A second controller tick can occur before WorldSimulation refreshes
        // WheelInfo from Bullet. Do not feed our own visual override back in.
        if (!hasNativeWheelReadback(current, lastWritten)) {
            return 0.0;
        }
        return clamp(current - lastNative, -100.0, 100.0);
    }

    static boolean hasNativeWheelReadback(double current, double lastWritten) {
        return Double.isFinite(current)
                && Double.isFinite(lastWritten)
                && Math.abs(current - lastWritten) > 1.0e-5;
    }

    static final class WheelVisualState {
        private double[] accumulatedRotation = new double[0];
        private double[] lastNativeRotation = new double[0];
        private double[] lastWrittenRotation = new double[0];
        private boolean[] initialized = new boolean[0];

        private void ensureSize(int size) {
            if (accumulatedRotation.length == size) {
                return;
            }
            accumulatedRotation = new double[size];
            lastNativeRotation = new double[size];
            lastWrittenRotation = new double[size];
            initialized = new boolean[size];
        }
    }

    record Controls(boolean forward, boolean backward, boolean parkingBrake, double steering) {
    }

    record PlayerControls(
            boolean available,
            float steering,
            boolean forward,
            boolean backward,
            boolean brake,
            boolean shift,
            long forceBrake) {
        private static PlayerControls unavailable(long forceBrake) {
            return new PlayerControls(false, 0.0f, false, false, false, false, forceBrake);
        }
    }

    record Weather(double rain, double snow) {
    }

    record TireStats(double pressure, double condition) {
    }

    record Surface(boolean offroad, boolean forest, double fraction, double efficiency) {
    }
}
