package dev.carphysicsimproved.v1.runtime;

import dev.carphysicsimproved.v1.physics.LegacyPhysics;
import dev.carphysicsimproved.v1.physics.LegacyDriverTraits;
import dev.carphysicsimproved.v1.physics.LegacySlideDynamics;
import dev.carphysicsimproved.v1.physics.LegacyTerrainDynamics;
import dev.carphysicsimproved.v1.physics.LegacyTireCondition;
import pzmod.carphysicsimproved.v1.CarPhysicsImprovedV1Mod;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

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
    private final Field controlsForceBrake;
    private final Field controlsParkingBrake;
    private final Field controlsShift;
    private final Field controlsSteering;
    private final Field gameClientClient;
    private final Field gameServerServer;
    private final Object sundayDriverTrait;
    private final Object speedDemonTrait;

    private final Method vehicleLocalPhysics;
    private final Method vehicleDriver;
    private final Method characterHasTrait;
    private final Method characterDead;
    private final Method keyboardDown;
    private final LegacyDrivingKeys.KeyState keyboardState = this::keyDown;
    private final Method coreInstance;
    private final Method coreKeyBinding;
    private final Method bindingKey;
    private final Method bindingAltKey;
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
    private final Method scriptCenterOfMass;
    private final Method scriptToBullet;
    private final Field scriptWheelFriction;
    private final Field wheelId;
    private final Field wheelFront;
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
    private final Field wheelSkidInfo;
    private final Field wheelRotation;
    private final Map<Object, ScriptFrictionState> scriptFrictionStates = new WeakHashMap<>();

    PzLegacyAccess() throws ReflectiveOperationException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> controllerClass = Class.forName("zombie.core.physics.CarController", false, loader);
        Class<?> keyboardClass = Class.forName("zombie.input.GameKeyboard", false, loader);
        Class<?> coreClass = Class.forName("zombie.core.Core", false, loader);
        Class<?> bindingClass = Class.forName("zombie.core.Core$KeyBinding", false, loader);
        Class<?> controlsClass = Class.forName("zombie.core.physics.CarController$ClientControls", false, loader);
        Class<?> vehicleClass = Class.forName("zombie.vehicles.BaseVehicle", false, loader);
        Class<?> characterClass = Class.forName("zombie.characters.IsoGameCharacter", false, loader);
        Class<?> characterTraitClass = Class.forName(
                "zombie.scripting.objects.CharacterTrait", false, loader);
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
        controlsForceBrake = field(controlsClass, "forceBrake");
        controlsParkingBrake = field(controlsClass, "wasUsingParkingBrakes");
        controlsShift = field(controlsClass, "shift");
        controlsSteering = field(controlsClass, "steering");
        gameClientClient = field(clientClass, "client");
        gameServerServer = field(serverClass, "server");
        sundayDriverTrait = field(characterTraitClass, "SUNDAY_DRIVER").get(null);
        speedDemonTrait = field(characterTraitClass, "SPEED_DEMON").get(null);

        vehicleLocalPhysics = method(vehicleClass, "isLocalPhysicSim");
        vehicleDriver = method(vehicleClass, "getDriver");
        characterHasTrait = method(characterClass, "hasTrait", characterTraitClass);
        characterDead = methodInHierarchy(characterClass, "isDead");
        keyboardDown = method(keyboardClass, "isKeyDown", int.class);
        coreInstance = method(coreClass, "getInstance");
        coreKeyBinding = method(coreClass, "getKeyBinding", String.class);
        bindingKey = method(bindingClass, "keyValue");
        bindingAltKey = method(bindingClass, "altKey");
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
        scriptCenterOfMass = method(scriptClass, "getCenterOfMassOffset");
        scriptToBullet = method(scriptClass, "toBullet");
        scriptWheelFriction = field(scriptClass, "wheelFriction");
        wheelId = field(scriptWheelClass, "id");
        wheelFront = field(scriptWheelClass, "front");
        wheelOffset = method(scriptWheelClass, "getOffset");
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

    LegacyDriverTraits.Modifiers driverModifiers(Object vehicle) throws ReflectiveOperationException {
        Object driver = invoke(vehicleDriver, vehicle);
        if (driver == null) {
            return LegacyDriverTraits.normal();
        }
        boolean sundayDriver = (Boolean) invoke(characterHasTrait, driver, sundayDriverTrait);
        boolean speedDemon = (Boolean) invoke(characterHasTrait, driver, speedDemonTrait);
        return LegacyDriverTraits.modifiers(sundayDriver, speedDemon);
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
        int mechanicType = ((Number) invoke(scriptMechanicType, script)).intValue();
        LegacyPhysics.Spec legacySpec = new LegacyPhysics.Spec(
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
                mechanicType);

        double frontZ = 0.0;
        double rearZ = 0.0;
        int frontCount = 0;
        int rearCount = 0;
        int wheelCount = Math.max(0, ((Number) invoke(scriptWheelCount, script)).intValue());
        for (int index = 0; index < wheelCount; index++) {
            Object wheel = invoke(scriptWheel, script, index);
            Object offset = invoke(wheelOffset, wheel);
            double z = vectorZ.getFloat(offset);
            if (wheelFront.getBoolean(wheel)) {
                frontZ += z;
                frontCount++;
            } else {
                rearZ += z;
                rearCount++;
            }
        }
        frontZ = frontCount == 0 ? 1.2 : frontZ / frontCount;
        rearZ = rearCount == 0 ? -1.2 : rearZ / rearCount;
        if (frontZ < rearZ) {
            double temporary = frontZ;
            frontZ = rearZ;
            rearZ = temporary;
        }
        double wheelbase = clamp(frontZ - rearZ, 1.2, 8.0);
        Object centerOfMass = invoke(scriptCenterOfMass, script);
        double centerZ = clamp(vectorZ.getFloat(centerOfMass), rearZ + wheelbase * 0.25,
                frontZ - wheelbase * 0.25);
        double frontWeightShare = clamp((centerZ - rearZ) / wheelbase, 0.30, 0.75);
        double centerHeight = clamp(0.46 + Math.max(0.0, mass - 1_000.0) / 8_000.0, 0.42, 0.90);
        LegacySlideDynamics.Spec slideSpec = new LegacySlideDynamics.Spec(
                mass, wheelbase, frontWeightShare, centerHeight, 1.0);
        return new Snapshot(script, legacySpec, slideSpec);
    }

    ConditionSnapshot conditions(Object vehicle, Snapshot snapshot) throws ReflectiveOperationException {
        Object script = snapshot.scriptIdentity;
        int wheelCount = Math.max(0, ((Number) invoke(scriptWheelCount, script)).intValue());
        double pressure = 0.0;
        double conditionGrip = 0.0;
        double frontPressure = 0.0;
        double rearPressure = 0.0;
        double frontConditionGrip = 0.0;
        double rearConditionGrip = 0.0;
        int frontCount = 0;
        int rearCount = 0;
        for (int index = 0; index < wheelCount; index++) {
            Object wheel = invoke(scriptWheel, script, index);
            boolean front = wheelFront.getBoolean(wheel);
            if (front) {
                frontCount++;
            } else {
                rearCount++;
            }
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
            double wheelPressure = clamp(((Number) invoke(partContent, part)).doubleValue() / capacity, 0.0, 1.35);
            double durability = clamp(((Number) invoke(partCondition, part)).doubleValue(), 0.0, 100.0) * 0.01;
            double wheelConditionGrip = LegacyTireCondition.gripMultiplier(
                    durability,
                    ((Number) invoke(itemWheelFriction, item)).doubleValue());
            pressure += wheelPressure;
            conditionGrip += wheelConditionGrip;
            if (front) {
                frontPressure += wheelPressure;
                frontConditionGrip += wheelConditionGrip;
            } else {
                rearPressure += wheelPressure;
                rearConditionGrip += wheelConditionGrip;
            }
        }
        if (wheelCount > 0) {
            pressure /= wheelCount;
            conditionGrip /= wheelCount;
        } else {
            pressure = 1.0;
            conditionGrip = 1.0;
        }
        frontPressure = frontCount == 0 ? pressure : frontPressure / frontCount;
        rearPressure = rearCount == 0 ? pressure : rearPressure / rearCount;
        frontConditionGrip = frontCount == 0 ? conditionGrip : frontConditionGrip / frontCount;
        rearConditionGrip = rearCount == 0 ? conditionGrip : rearConditionGrip / rearCount;

        boolean offroad = (Boolean) invoke(vehicleOffroad, vehicle);
        boolean forest = (Boolean) invoke(vehicleForest, vehicle);
        Object climate = invoke(climateInstance, null);
        double rain = climate == null ? 0.0
                : clamp(((Number) invoke(climateRain, climate)).doubleValue(), 0.0, 1.0);
        double snow = climate == null ? 0.0
                : clamp(((Number) invoke(climateSnow, climate)).doubleValue(), 0.0, 1.0);
        LegacyTerrainDynamics.Output terrain = LegacyTerrainDynamics.evaluate(
                new LegacyTerrainDynamics.Input(
                        snapshot.spec().mechanicType(),
                        snapshot.spec().offroadEfficiency(),
                        pressure,
                        rain,
                        snow,
                        offroad,
                        forest),
                CarPhysicsImprovedV1Mod.terrainTuning());
        double surfaceGrip = terrain.surfaceGrip();
        LegacyPhysics.Conditions longitudinal = new LegacyPhysics.Conditions(
                pressure, conditionGrip, surfaceGrip, offroad, terrain.offroadResistanceScale());
        double overall = CarPhysicsImprovedV1Mod.settings().overallTraction();
        LegacySlideDynamics.AxleGrip lateral = new LegacySlideDynamics.AxleGrip(
                axleGrip(frontPressure, frontConditionGrip, surfaceGrip, overall),
                axleGrip(rearPressure, rearConditionGrip, surfaceGrip, overall));
        return new ConditionSnapshot(longitudinal, lateral, terrain);
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

    boolean driftKeyHeld(Object vehicle) throws ReflectiveOperationException {
        if (!CarPhysicsImprovedV1Mod.enabled() || !CarPhysicsImprovedV1Mod.slideTuning().enabled()
                || !(Boolean) invoke(vehicleKeyboardControlled, vehicle)) {
            return false;
        }
        Object driver = invoke(vehicleDriver, vehicle);
        if (driver == null || (Boolean) invoke(characterDead, driver)) {
            return false;
        }
        // GameKeyboard.isKeyDown also respects text-entry focus. No input is latched between ticks.
        return CarPhysicsImprovedV1Mod.drivingKeys().driftHeld(
                CarPhysicsImprovedV1Mod.manualTransmission(), keyboardState);
    }

    private boolean keyDown(int key) throws ReflectiveOperationException {
        return (Boolean) invoke(keyboardDown, null, key);
    }

    void suppressDriftKeyBrake(Object controller) throws ReflectiveOperationException {
        Object controls = controllerControls.get(controller);
        long forcedAt = controlsForceBrake.getLong(controls);
        long forcedAge = System.currentTimeMillis() - forcedAt;
        // Retain the game's forced stop after entry/collision, even while the chord is held.
        if (forcedAt != 0 && forcedAge > 0 && forcedAge < 1_000) {
            return;
        }
        LegacyDriftKey drift = CarPhysicsImprovedV1Mod.driftKey();
        Object brake = invoke(coreKeyBinding, invoke(coreInstance, null), "Brake");
        int primary = ((Number) invoke(bindingKey, brake)).intValue();
        int secondary = ((Number) invoke(bindingAltKey, brake)).intValue();
        if (drift.suppressesBrake(primary, secondary,
                primary > 0 && keyDown(primary), secondary > 0 && keyDown(secondary), false)) {
            controlsBrake.setBoolean(controls, false);
            controlsParkingBrake.setBoolean(controls, false);
        }
        // Shift belongs to the chord here, not to cruise-control speed adjustment.
        controlsShift.setBoolean(controls, false);
    }

    double speedSteeringClamp(Object vehicle, double speedKph) throws ReflectiveOperationException {
        Object script = invoke(vehicleScript, vehicle);
        return ((Number) invoke(scriptSteeringClamp, script, (float) Math.abs(speedKph))).doubleValue();
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
        return new Motion(
                vx * fx + vz * fz,
                vx * -fz + vz * fx,
                Math.atan2(fz, fx),
                fx,
                fz,
                vx,
                vy,
                vz);
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

    void apply(Object controller, Object vehicle, LegacyPhysics.Output output, Motion motion,
            double steeringRadians, double deltaSeconds)
            throws ReflectiveOperationException {
        float engine = (float) output.engineForce();
        float braking = (float) output.brakingForce();
        float steering = (float) steeringRadians;
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

    void applySlideForces(Object vehicle, Motion motion, LegacySlideDynamics.Output output)
            throws ReflectiveOperationException {
        if (output == null) {
            return;
        }
        double rightX = -motion.forwardZ;
        double rightZ = motion.forwardX;
        int id = vehicleId(vehicle);
        invoke(bulletForce, null, id,
                (float) (rightX * output.lateralForce()),
                0.0f,
                (float) (rightZ * output.lateralForce()));
        invoke(bulletTorque, null, id, 0.0f, (float) output.bulletYawTorque(), 0.0f);
    }

    /**
     * Temporarily lowers the native tire budget for one locally driven vehicle.
     * VehicleScript is shared by every instance of a type, so ownership is tracked
     * and the exact value observed before entry is restored on every exit path.
     */
    void applyWheelFrictionScale(Object vehicle, double requestedScale) throws ReflectiveOperationException {
        Object script = script(vehicle);
        double scale = clamp(requestedScale, 0.12, 1.0);
        synchronized (scriptFrictionStates) {
            ScriptFrictionState state = scriptFrictionStates.get(script);
            if (scale >= 0.999) {
                if (state != null && state.owner.get() == vehicle) {
                    restoreScriptFriction(script, state);
                }
                return;
            }

            if (state == null) {
                state = new ScriptFrictionState(scriptWheelFriction.getFloat(script));
                scriptFrictionStates.put(script, state);
            } else {
                Object owner = state.owner.get();
                if (state.reduced && owner != null && owner != vehicle) {
                    restoreScriptFriction(script, state);
                }
                if (!state.reduced) {
                    state.baseValue = scriptWheelFriction.getFloat(script);
                }
            }

            float target = (float) (state.baseValue * scale);
            if (!state.reduced || Math.abs(scriptWheelFriction.getFloat(script) - target) > 1.0E-4f) {
                scriptWheelFriction.setFloat(script, target);
                invoke(scriptToBullet, script);
            }
            state.owner = new WeakReference<>(vehicle);
            state.reduced = true;
        }
    }

    void restoreWheelFriction(Object vehicle) throws ReflectiveOperationException {
        Object script = script(vehicle);
        synchronized (scriptFrictionStates) {
            ScriptFrictionState state = scriptFrictionStates.get(script);
            if (state != null && state.owner.get() == vehicle) {
                restoreScriptFriction(script, state);
            }
        }
    }

    void restoreAllWheelFriction() throws ReflectiveOperationException {
        synchronized (scriptFrictionStates) {
            for (Map.Entry<Object, ScriptFrictionState> entry : scriptFrictionStates.entrySet()) {
                if (entry.getValue().reduced) {
                    restoreScriptFriction(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    private void restoreScriptFriction(Object script, ScriptFrictionState state)
            throws ReflectiveOperationException {
        if (state.reduced && Math.abs(scriptWheelFriction.getFloat(script) - state.baseValue) > 1.0E-4f) {
            scriptWheelFriction.setFloat(script, state.baseValue);
            invoke(scriptToBullet, script);
        }
        state.owner = new WeakReference<>(null);
        state.reduced = false;
    }

    AxleSkid axleSkid(Object vehicle) throws ReflectiveOperationException {
        Object script = script(vehicle);
        Object wheels = vehicleWheelInfo.get(vehicle);
        int infoCount = wheels == null ? 0 : Array.getLength(wheels);
        int scriptCount = Math.max(0, ((Number) invoke(scriptWheelCount, script)).intValue());
        int count = Math.min(infoCount, scriptCount);
        double front = 0.0;
        double rear = 0.0;
        int frontCount = 0;
        int rearCount = 0;
        for (int index = 0; index < count; index++) {
            Object wheel = invoke(scriptWheel, script, index);
            Object info = Array.get(wheels, index);
            double amount = clamp((0.70 - wheelSkidInfo.getFloat(info)) / 0.70, 0.0, 1.0);
            if (wheelFront.getBoolean(wheel)) {
                front += amount;
                frontCount++;
            } else {
                rear += amount;
                rearCount++;
            }
        }
        return new AxleSkid(frontCount == 0 ? 0.0 : front / frontCount,
                rearCount == 0 ? 0.0 : rear / rearCount);
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

    void applySlideVisual(Object vehicle, double slideBlend) throws ReflectiveOperationException {
        if (slideBlend <= 0.02) {
            return;
        }
        Object script = script(vehicle);
        Object wheels = vehicleWheelInfo.get(vehicle);
        int infoCount = wheels == null ? 0 : Array.getLength(wheels);
        int scriptCount = Math.max(0, ((Number) invoke(scriptWheelCount, script)).intValue());
        int count = Math.min(infoCount, scriptCount);
        for (int index = 0; index < count; index++) {
            Object wheel = invoke(scriptWheel, script, index);
            Object info = Array.get(wheels, index);
            float target = wheelFront.getBoolean(wheel) ? 0.48f : 0.30f;
            float blended = (float) lerp(1.0, target, clamp(slideBlend, 0.0, 1.0));
            wheelSkidInfo.setFloat(info, Math.min(wheelSkidInfo.getFloat(info), blended));
        }
    }

    private static double axleGrip(double pressure, double conditionGrip, double surfaceGrip,
            double overallTraction) {
        double hardwareGrip = LegacyTireCondition.hardwareGrip(pressure, conditionGrip);
        return clamp(hardwareGrip * surfaceGrip * overallTraction, 0.05, 1.8);
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

    record Snapshot(Object scriptIdentity, LegacyPhysics.Spec spec, LegacySlideDynamics.Spec slideSpec) {
    }

    record ConditionSnapshot(LegacyPhysics.Conditions longitudinal, LegacySlideDynamics.AxleGrip lateral,
            LegacyTerrainDynamics.Output terrain) {
    }

    record AxleSkid(double front, double rear) {
    }

    record Controls(boolean forward, boolean backward, boolean handbrake, double steering, double throttle) {
    }

    record Motion(double longitudinalSpeedMps, double lateralSpeedMps, double headingRadians,
            double forwardX, double forwardZ,
            double velocityX, double velocityY, double velocityZ) {
    }

    private static final class ScriptFrictionState {
        private float baseValue;
        private WeakReference<Object> owner = new WeakReference<>(null);
        private boolean reduced;

        private ScriptFrictionState(float baseValue) {
            this.baseValue = baseValue;
        }
    }
}
