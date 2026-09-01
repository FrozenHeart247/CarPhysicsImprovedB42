package zombie.roadcraft.runtime;

import zombie.roadcraft.BuildInfo;
import zombie.roadcraft.physics.DrivetrainModel;
import zombie.roadcraft.physics.PhysicsSettings;
import zombie.roadcraft.physics.RoadcraftCalibration;
import zombie.roadcraft.physics.VehicleInput;
import zombie.roadcraft.physics.VehicleOutput;
import zombie.roadcraft.physics.VehicleState;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Game-facing callbacks invoked by the clean-room ZombieBuddy patches. */
public final class RoadcraftHooks {
    private static final double NATIVE_BRAKE_SCALE = 0.04;
    private static final Map<Object, VehicleRuntime> VEHICLES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Object ACCESS_LOCK = new Object();
    private static final Object LUA_BRIDGE_LOCK = new Object();
    private static final AtomicBoolean LUA_BRIDGE_REGISTERED = new AtomicBoolean();
    private static final AtomicLong LAST_LUA_BRIDGE_ATTEMPT = new AtomicLong();
    private static final ThreadLocal<ImpulseScope> IMPULSE_SCOPE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> VANILLA_CONTROLLER_SCOPE = new ThreadLocal<>();

    private static volatile PzAccess access;
    private static volatile boolean accessAttempted;

    static {
        RoadcraftBridge.bootstrapZombieBuddyRuntime(BuildInfo.VERSION);
    }

    private RoadcraftHooks() {
    }

    /** Marks calls made by vanilla CarController.update for the AutoStart gate. */
    public static void beginVanillaControllerUpdate() {
        VANILLA_CONTROLLER_SCOPE.set(Boolean.TRUE);
    }

    public static void endVanillaControllerUpdate() {
        VANILLA_CONTROLLER_SCOPE.remove();
    }

    /** Does not affect explicit ignition actions outside CarController.update. */
    public static boolean shouldSkipControllerAutoStart() {
        return RoadcraftBridge.runtimeEnabled()
                && Boolean.TRUE.equals(VANILLA_CONTROLLER_SCOPE.get())
                && !RoadcraftBridge.bool("autoStart", true);
    }

    /**
     * Runs after the vanilla controller. If anything is unavailable or fails,
     * the already-issued vanilla Bullet command is left untouched.
     */
    public static void afterControllerUpdate(Object controller, boolean trailer) {
        if (!RoadcraftBridge.runtimeEnabled() || controller == null) {
            return;
        }
        PzAccess current = access();
        if (current == null) {
            return;
        }
        try {
            Object vehicle = current.vehicle(controller);
            if (vehicle == null || !current.hasControlAuthority(vehicle)) {
                return;
            }
            ControllerFrame frame = new ControllerFrame(controller, vehicle);
            computeFrame(
                    current,
                    frame,
                    current.controllerEngineForce(controller),
                    current.controllerBrakingForce(controller),
                    current.currentSteering(vehicle));
            if (!frame.computed || !RoadcraftBridge.runtimeEnabled()) {
                return;
            }
            PzAccess.fallbackControlVehicle(
                    current.vehicleId(vehicle),
                    finite(frame.engine),
                    finite(frame.brake),
                    finite(frame.steering));
        } catch (Throwable error) {
            RoadcraftBridge.markRuntimeFailure(trailer ? "zb-trailer-update" : "zb-controller-update", error);
        }
    }

    /** Applies the configured native mass immediately before the Bullet physics pass. */
    public static void beforeVehiclePhysics() {
        if (!RoadcraftBridge.runtimeEnabled()) {
            return;
        }
        PzAccess current = access();
        if (current == null) {
            return;
        }
        try {
            current.updateNativeMasses();
        } catch (Throwable error) {
            RoadcraftBridge.markRuntimeFailure("zb-world-mass", error);
        }
    }

    /** Reapplies virtual wheel rotation after Bullet has written WheelInfo. */
    public static void afterVehiclePhysics() {
        if (!RoadcraftBridge.runtimeEnabled()) {
            return;
        }
        PzAccess current = access();
        if (current == null) {
            return;
        }
        try {
            synchronized (VEHICLES) {
                for (Map.Entry<Object, VehicleRuntime> entry : VEHICLES.entrySet()) {
                    Object vehicle = entry.getKey();
                    VehicleRuntime runtime = entry.getValue();
                    if (vehicle == null || runtime == null || !runtime.initialized
                            || !current.hasControlAuthority(vehicle)) {
                        continue;
                    }
                    current.applyWheelVisuals(
                            vehicle,
                            runtime.wheelVisuals,
                            runtime.burnoutAmountKph,
                            runtime.parkingBrake,
                            runtime.visualDeltaSeconds);
                }
            }
        } catch (Throwable error) {
            RoadcraftBridge.markRuntimeFailure("zb-world-wheel-visuals", error);
        }
    }

    /** Captures the first impulse index before a vanilla hit method appends entries. */
    public static void beginImpulse(Object vehicle, String key) {
        IMPULSE_SCOPE.remove();
        if (!RoadcraftBridge.runtimeEnabled() || vehicle == null || key == null) {
            return;
        }
        PzAccess current = access();
        if (current == null) {
            return;
        }
        try {
            IMPULSE_SCOPE.set(new ImpulseScope(vehicle, key, current.beginImpulse(vehicle)));
        } catch (Throwable error) {
            RoadcraftBridge.markRuntimeFailure("zb-impulse-begin", error);
        }
    }

    /** Scales only impulses appended by the matching vanilla hit method. */
    public static void endImpulse(Object vehicle, String key) {
        ImpulseScope scope = IMPULSE_SCOPE.get();
        IMPULSE_SCOPE.remove();
        if (scope == null || scope.vehicle != vehicle || !scope.key.equals(key)) {
            return;
        }
        PzAccess current = access();
        if (current == null || !RoadcraftBridge.runtimeEnabled()) {
            return;
        }
        try {
            if (current.hasControlAuthority(vehicle)) {
                current.scaleImpulsesFrom(
                        vehicle,
                        scope.startIndex,
                        RoadcraftBridge.number(key, 1.0));
            }
        } catch (Throwable error) {
            RoadcraftBridge.markRuntimeFailure("zb-impulse-end", error);
        }
    }

    /** Registers the BaseVehicle with native Bullet before any active-state calls can occur. */
    public static void initializeDirectVehicle(Object vehicle) {
        PzAccess current = access();
        if (current == null) {
            throw new IllegalStateException("B42.20.4 runtime adapter is unavailable");
        }
        try {
            current.initializeVehiclePhysics(vehicle);
            ensureLooseLuaBridge();
        } catch (Throwable error) {
            RoadcraftBridge.markFatal("Native vehicle registration failed: " + error);
            throw new IllegalStateException("Unable to register vehicle with Bullet", error);
        }
    }

    private static boolean registerLuaBridge() {
        if (LUA_BRIDGE_REGISTERED.get()) {
            return true;
        }
        synchronized (LUA_BRIDGE_LOCK) {
            if (LUA_BRIDGE_REGISTERED.get()) {
                return true;
            }
            try {
                Class<?> luaManager = Class.forName("zombie.Lua.LuaManager");
                Class<?> tableClass = Class.forName("se.krka.kahlua.vm.KahluaTable");
                Object exposer = luaManager.getField("exposer").get(null);
                Object environment = luaManager.getField("env").get(null);
                if (exposer == null || environment == null) {
                    throw new IllegalStateException("LuaManager environment is not ready");
                }
                Method expose = exposer.getClass().getMethod(
                        "exposeGlobalClassFunction", tableClass, Class.class, Method.class, String.class);
                expose(expose, exposer, environment, "RCD_setBoolean", "setBoolean", String.class, boolean.class);
                expose(expose, exposer, environment, "RCD_setNumber", "setNumber", String.class, double.class);
                expose(expose, exposer, environment, "RCD_status", "status");
                expose(expose, exposer, environment, "RCD_statusDetail", "statusDetail");
                expose(expose, exposer, environment, "RCD_runtimeVersion", "runtimeVersion");
                expose(expose, exposer, environment, "RCD_targetGameVersion", "targetGameVersion");
                expose(expose, exposer, environment, "RCD_knownTestedGameVersion", "knownTestedGameVersion");
                expose(expose, exposer, environment, "RCD_requestShiftFor", "requestShiftFor", int.class, int.class);
                expose(expose, exposer, environment, "RCD_burnoutAmountFor", "burnoutAmountFor", int.class);
                expose(expose, exposer, environment, "RCD_activateConfiguration", "activateConfiguration");
                LUA_BRIDGE_REGISTERED.set(true);
                System.out.println("[RoadcraftDynamics] Prefixed Lua bridge registered.");
                return true;
            } catch (Throwable error) {
                return false;
            }
        }
    }

    private static void ensureLooseLuaBridge() {
        if (LUA_BRIDGE_REGISTERED.get()) {
            return;
        }
        long now = System.nanoTime();
        long previous = LAST_LUA_BRIDGE_ATTEMPT.get();
        if (previous != 0L && now - previous < 250_000_000L) {
            return;
        }
        if (LAST_LUA_BRIDGE_ATTEMPT.compareAndSet(previous, now)) {
            registerLuaBridge();
        }
    }

    /** Reads the same local keyboard/joypad state as the B42 controller. */
    public static DirectControls readDirectControls(Object vehicle, long forceBrake) {
        ensureLooseLuaBridge();
        PzAccess current = access();
        if (current == null || vehicle == null) {
            return DirectControls.unavailable(forceBrake);
        }
        try {
            PzAccess.PlayerControls controls = current.playerControls(vehicle, forceBrake);
            return new DirectControls(
                    controls.available(),
                    controls.steering(),
                    controls.forward(),
                    controls.backward(),
                    controls.brake(),
                    controls.shift(),
                    controls.brake(),
                    controls.forceBrake());
        } catch (Throwable error) {
            RoadcraftBridge.markRuntimeFailure("direct-controls", error);
            return DirectControls.unavailable(forceBrake);
        }
    }

    /** Full loose-class update entry point used by the clean-room controller. */
    public static DirectControllerResult updateDirectController(Object controller, boolean trailer) {
        ensureLooseLuaBridge();
        PzAccess current = access();
        if (current == null || controller == null) {
            return DirectControllerResult.idle();
        }
        Object vehicle = null;
        float speed = 0.0f;
        try {
            vehicle = current.vehicle(controller);
            speed = finite((float) current.speedKph(vehicle));
            if (trailer && !current.prepareTrailerUpdate(vehicle)) {
                return DirectControllerResult.idle(speed);
            }
            if (current.isDedicatedServer()
                    || !trailer && !current.hasControlAuthority(vehicle)) {
                return DirectControllerResult.idle(speed);
            }
            if (!RoadcraftBridge.runtimeEnabled()) {
                return failSafeStop(current, controller, vehicle, speed);
            }

            PzAccess.Controls controls = current.controls(controller);
            boolean directionInput = controls.forward() != controls.backward();
            if (directionInput
                    && RoadcraftBridge.bool("autoStart", true)
                    && !current.engineRunning(vehicle)) {
                current.tryStartEngine(vehicle);
            }

            double brakeDemand = RoadcraftCalibration.serviceBrakeDemand(
                    controls.forward(), controls.backward(), speed / 3.6);
            boolean breaking = controls.parkingBrake()
                    || controls.forward() == controls.backward() && controls.forward()
                    || brakeDemand > 0.0;
            boolean gas = controls.forward() && !breaking;
            boolean gasReverse = controls.backward() && !breaking;

            ControllerFrame frame = new ControllerFrame(controller, vehicle);
            float baseTrailerBrake = trailer ? current.baseTrailerBrake(vehicle) : 0.0f;
            computeFrame(current, frame, 0.0f, baseTrailerBrake, 0.0f);
            if (!RoadcraftBridge.runtimeEnabled()) {
                return failSafeStop(current, controller, vehicle, speed);
            }

            float engine = frame != null && frame.computed ? frame.engine : 0.0f;
            float brake = frame != null && frame.computed ? frame.brake : 0.0f;
            float steering = frame != null && frame.computed ? frame.steering : 0.0f;
            current.setStoplights(vehicle, breaking);
            current.setBackSignal(vehicle, gasReverse);
            return new DirectControllerResult(
                    engine,
                    brake,
                    steering,
                    speed,
                    gas,
                    gasReverse,
                    breaking,
                    gas || gasReverse,
                    breaking,
                    true);
        } catch (Throwable error) {
            RoadcraftBridge.markRuntimeFailure(trailer ? "direct-trailer" : "direct-controller", error);
            return failSafeStop(current, controller, vehicle, speed);
        }
    }

    /** Dispatches the already-computed command after CarController has run its active-state check. */
    public static void dispatchDirectController(Object controller, DirectControllerResult result) {
        if (controller == null || result == null || !result.nativeDispatch()) {
            return;
        }
        PzAccess current = access();
        if (current == null) {
            return;
        }
        try {
            Object vehicle = current.vehicle(controller);
            if (!current.isDedicatedServer()) {
                PzAccess.fallbackControlVehicle(
                        current.vehicleId(vehicle),
                        finite(result.engineForce()),
                        finite(result.brakingForce()),
                        finite(result.vehicleSteering()));
            }
        } catch (Throwable error) {
            RoadcraftBridge.markRuntimeFailure("direct-dispatch", error);
        }
    }

    public static boolean shouldDirectControllerBeActive(Object controller) {
        PzAccess current = access();
        if (current == null || controller == null) {
            return false;
        }
        try {
            return shouldDirectControllerBeActive(current, controller, 0);
        } catch (Throwable error) {
            RoadcraftBridge.markRuntimeFailure("direct-active-check", error);
            return true;
        }
    }

    public static boolean shouldManageDirectControllerActivity() {
        PzAccess current = access();
        if (current == null) {
            return false;
        }
        try {
            return !current.isDedicatedServer();
        } catch (Throwable error) {
            RoadcraftBridge.markRuntimeFailure("direct-active-authority", error);
            return false;
        }
    }

    private static boolean shouldDirectControllerBeActive(
            PzAccess current,
            Object controller,
            int towingDepth) throws ReflectiveOperationException {
        if (towingDepth > 8) {
            return true;
        }
        Object vehicle = current.vehicle(controller);
        if (current.hasPendingPhysicsActiveCheck(vehicle)
                || !current.isAtRest(vehicle)
                || current.hasPlayerDrivenVehicleNearby(vehicle)) {
            return true;
        }

        Object towingVehicle = current.towedByVehicle(vehicle);
        if (towingVehicle == null) {
            float engine = current.engineRunning(vehicle)
                    ? current.controllerEngineForce(controller)
                    : 0.0f;
            return Math.abs(engine) > 0.01f;
        }
        Object towingController = current.controller(towingVehicle);
        return towingController != null
                && shouldDirectControllerBeActive(current, towingController, towingDepth + 1);
    }

    public static boolean isDirectControllerAtRest(Object controller) {
        PzAccess current = access();
        if (current == null || controller == null) {
            return false;
        }
        try {
            return current.isAtRest(current.vehicle(controller));
        } catch (Throwable error) {
            RoadcraftBridge.markRuntimeFailure("direct-rest-check", error);
            return false;
        }
    }

    public static float directControllerTimeDelta() {
        PzAccess current = access();
        if (current == null) {
            return 1.0f / 60.0f;
        }
        try {
            return current.physicsDeltaSeconds();
        } catch (Throwable error) {
            RoadcraftBridge.markRuntimeFailure("direct-time-delta", error);
            return 1.0f / 60.0f;
        }
    }

    public static void setDirectControllerActive(Object controller, boolean active) {
        PzAccess current = access();
        if (current == null || controller == null) {
            return;
        }
        try {
            current.setPhysicsActive(current.vehicle(controller), active);
        } catch (Throwable error) {
            RoadcraftBridge.markRuntimeFailure("direct-active-set", error);
        }
    }

    public static DirectControllerResult parkDirectController(Object controller) {
        PzAccess current = access();
        if (current == null || controller == null) {
            return DirectControllerResult.idle();
        }
        try {
            Object vehicle = current.vehicle(controller);
            float speed = finite((float) current.speedKph(vehicle));
            float brake = finite((float) current.baseBrakingForce(vehicle));
            current.applyTransmission(vehicle, 0);
            current.setThrottle(vehicle, 0.0);
            current.applyControllerFields(controller, 0.0f, brake);
            current.setCurrentSteering(vehicle, 0.0f);
            current.setStoplights(vehicle, false);
            current.setBackSignal(vehicle, false);
            current.parkTowingVehicle(vehicle);
            return new DirectControllerResult(
                    0.0f, brake, 0.0f, speed,
                    false, false, false, false, false,
                    !current.isDedicatedServer());
        } catch (Throwable error) {
            RoadcraftBridge.markRuntimeFailure("direct-park", error);
            return DirectControllerResult.idle();
        }
    }

    private static DirectControllerResult failSafeStop(
            PzAccess current,
            Object controller,
            Object vehicle,
            float speed) {
        if (current == null || controller == null || vehicle == null) {
            return DirectControllerResult.idle(speed);
        }
        try {
            float brake = finite((float) current.baseBrakingForce(vehicle));
            current.applyTransmission(vehicle, 0);
            current.setThrottle(vehicle, 0.0);
            current.applyControllerFields(controller, 0.0f, brake);
            current.setCurrentSteering(vehicle, 0.0f);
            current.setStoplights(vehicle, false);
            current.setBackSignal(vehicle, false);
            return new DirectControllerResult(
                    0.0f, brake, 0.0f, speed,
                    false, false, false, false, false,
                    !current.isDedicatedServer() && current.hasControlAuthority(vehicle));
        } catch (Throwable ignored) {
            return DirectControllerResult.idle(speed);
        }
    }

    private static void computeFrame(
            PzAccess current,
            ControllerFrame frame,
            float vanillaEngine,
            float vanillaBrake,
            float vanillaSteering) throws ReflectiveOperationException {
        Object vehicle = frame.vehicle;
        if (vehicle == null) {
            return;
        }

        if (current.isTowed(vehicle)) {
            current.setThrottle(vehicle, 0.0);
            frame.engine = current.towingEngineForce(vehicle) > 0.0f ? 1.0e-4f : vanillaEngine;
            frame.brake = RoadcraftBridge.bool("easyTow", true) ? vanillaBrake * 0.20f : vanillaBrake;
            frame.steering = vanillaSteering;
            current.applyControllerFields(frame.controller, frame.engine, frame.brake);
            frame.computed = true;
            return;
        }

        if (!current.hasDriver(vehicle)) {
            current.setThrottle(vehicle, 0.0);
            return;
        }

        if (!current.engineRunning(vehicle)) {
            current.setThrottle(vehicle, 0.0);
            if (!RoadcraftBridge.bool("autoStart", true)) {
                frame.engine = 0.0f;
                frame.brake = vanillaBrake;
                frame.steering = vanillaSteering;
                current.applyControllerFields(frame.controller, frame.engine, frame.brake);
                frame.computed = true;
            }
            return;
        }

        VehicleRuntime runtime = VEHICLES.computeIfAbsent(vehicle, ignored -> new VehicleRuntime());
        double mass = current.massKg(vehicle);
        double enginePower = current.enginePower(vehicle);
        double wheelRadius = current.wheelRadiusMeters(vehicle);
        PzAccess.Surface surface = current.surface(vehicle);
        double offroad = surface.fraction();
        int mechanicType = current.mechanicType(vehicle);
        String powertrainCategory = RoadcraftCalibration.powertrainCategory(mechanicType);
        String vehicleScript = current.scriptName(vehicle);
        double maximumSpeedKph = Math.min(120.0, current.maxSpeedKph(vehicle));
        long revision = RoadcraftBridge.configurationRevision();
        if (runtime.settings == null
                || runtime.configurationRevision != revision
                || Math.abs(runtime.settingsMass - mass) > 5.0
                || Math.abs(runtime.settingsEnginePower - enginePower) > 1.0
                || Math.abs(runtime.settingsWheelRadius - wheelRadius) > 0.005
                || Math.abs(runtime.settingsOffroad - offroad) > 0.05
                || runtime.settingsMechanicType != mechanicType
                || Math.abs(runtime.settingsMaximumSpeedKph - maximumSpeedKph) > 0.5) {
            runtime.settings = buildSettings(
                    current,
                    vehicle,
                    mass,
                    enginePower,
                    wheelRadius,
                    offroad,
                    mechanicType,
                    maximumSpeedKph);
            runtime.configurationRevision = revision;
            runtime.settingsMass = mass;
            runtime.settingsEnginePower = enginePower;
            runtime.settingsWheelRadius = wheelRadius;
            runtime.settingsOffroad = offroad;
            runtime.settingsMechanicType = mechanicType;
            runtime.settingsMaximumSpeedKph = maximumSpeedKph;
        }

        int vehicleId = current.vehicleId(vehicle);
        int gameGear = clampGear(current.currentGear(vehicle), runtime.settings.transmission().forwardGearCount());
        double speedKph = current.speedKph(vehicle);
        double speedMps = speedKph / 3.6;
        if (!runtime.initialized) {
            runtime.desiredGear = gameGear;
            runtime.tireSpeedKph = speedKph;
            runtime.state = new VehicleState(
                    speedMps,
                    Math.max(current.engineRpm(vehicle), runtime.settings.engine().idleRpm()),
                    gameGear,
                    0.0);
            runtime.initialized = true;
        }

        long now = System.nanoTime();
        double deltaSeconds = current.physicsDeltaSeconds();

        PzAccess.Controls controls = current.controls(frame.controller);
        boolean manual = RoadcraftBridge.bool("manualAllowed", true)
                && RoadcraftBridge.bool("manualMode", false);
        boolean automaticReverse = RoadcraftBridge.bool("automaticReverse", true);
        int shiftRequest = 0;
        int desiredGearBeforeShift = runtime.desiredGear;

        if (manual) {
            shiftRequest = RoadcraftBridge.consumeShiftRequest(vehicleId);
            runtime.desiredGear = clampGear(
                    runtime.desiredGear + shiftRequest,
                    runtime.settings.transmission().forwardGearCount());
        } else {
            RoadcraftBridge.consumeShiftRequest(vehicleId);
            if (automaticReverse
                    && controls.backward()
                    && !controls.forward()
                    && Math.abs(speedMps) < 0.65) {
                runtime.desiredGear = -1;
            } else if (controls.forward()
                    && !controls.backward()
                    && Math.abs(speedMps) < 0.65) {
                runtime.desiredGear = 1;
            } else if (runtime.desiredGear == 0) {
                runtime.desiredGear = gameGear < 0 ? -1 : 1;
            }
        }

        double baseBrake = current.baseBrakingForce(vehicle);
        boolean useAnalogThrottle = RoadcraftBridge.bool("useAnalogThrottle", true);
        double acceleratorInput = !useAnalogThrottle || current.isKeyboardControlled(vehicle)
                ? 1.0
                : clamp(current.throttle(vehicle), 0.0, 1.0);
        double serviceBrake = RoadcraftCalibration.serviceBrakeDemand(
                controls.forward(),
                controls.backward(),
                speedMps);
        double throttleTarget;
        if (serviceBrake > 0.0 || controls.forward() == controls.backward()) {
            throttleTarget = 0.0;
        } else if (runtime.desiredGear < 0) {
            throttleTarget = controls.backward() ? acceleratorInput : 0.0;
        } else if (runtime.desiredGear > 0) {
            throttleTarget = controls.forward() ? acceleratorInput : 0.0;
        } else {
            throttleTarget = controls.forward() || controls.backward() ? acceleratorInput : 0.0;
        }
        if (serviceBrake > 0.0 || controls.parkingBrake()) {
            runtime.throttle = 0.0;
        } else {
            runtime.throttle = RoadcraftCalibration.throttleStep(
                    runtime.throttle,
                    throttleTarget,
                    mechanicType,
                    deltaSeconds);
        }
        double throttle = clamp01(runtime.throttle);
        current.setThrottle(vehicle, throttle);

        PzAccess.TireStats tires = current.tireStats(vehicle);
        boolean tractionEnabled = RoadcraftBridge.bool("tractionEnabled", true);
        double tireCondition = tractionEnabled ? tires.condition() : 1.0;
        PzAccess.Weather weather = current.weather();
        double wetFactor = clamp(RoadcraftBridge.number("wetTraction", 0.70), 0.0, 1.0);
        double snowFactor = clamp(RoadcraftBridge.number("snowTraction", 0.40), 0.0, 1.0);
        double environmentMultiplier = tractionEnabled
                ? RoadcraftCalibration.tractionEnvironmentMultiplier(
                        weather.rain(),
                        weather.snow(),
                        tires.pressure(),
                        surface.offroad(),
                        surface.efficiency(),
                        wetFactor,
                        snowFactor,
                        RoadcraftBridge.number("offroadTraction", 0.60))
                : 1.0;

        runtime.state = new VehicleState(
                speedMps,
                runtime.state.engineRpm(),
                runtime.state.gear(),
                runtime.state.wheelSlip());
        VehicleInput input = new VehicleInput(
                throttle,
                serviceBrake,
                controls.parkingBrake() ? 1.0 : 0.0,
                clamp(controls.steering(), -1.0, 1.0),
                !manual,
                runtime.desiredGear,
                tireCondition,
                1.0 - environmentMultiplier,
                0.0);
        VehicleOutput output = DrivetrainModel.step(runtime.settings, runtime.state, input, deltaSeconds);
        runtime.state = output.state();
        runtime.desiredGear = output.state().gear();

        if (output.state().gear() != gameGear) {
            current.applyTransmission(vehicle, output.state().gear());
        }
        current.setEngineRpm(vehicle, output.state().engineRpm());

        runtime.tireSpeedKph = RoadcraftCalibration.updateTireSpeedKph(
                runtime.tireSpeedKph,
                speedKph,
                output.rawDriveForceN(),
                output.tractionLimitN(),
                output.state().gear(),
                output.sanitizedDeltaSeconds());
        double burnoutAmount = RoadcraftCalibration.burnoutAmountKph(
                runtime.tireSpeedKph,
                speedKph,
                output.state().gear());
        if (!output.burnout()) {
            burnoutAmount = 0.0;
        }
        runtime.burnoutAmountKph = burnoutAmount;
        runtime.parkingBrake = controls.parkingBrake();
        runtime.visualDeltaSeconds = output.sanitizedDeltaSeconds();
        RoadcraftBridge.updateBurnoutAmount(vehicleId, burnoutAmount);

        // WorldSimulation receives a reduced native mass for stable impacts.
        // Drive and free-running resistance follow the same ratio so their
        // acceleration is preserved. Brake intentionally stays unscaled to
        // retain the stronger native brake response of the reference behavior.
        double massScale = current.massScale();
        double nativeEngine = output.appliedDriveForceN() * massScale;
        double nativeBrake = (output.serviceBrakeForceN() + output.parkingBrakeForceN())
                * NATIVE_BRAKE_SCALE;
        double maximumBrake = Math.max(baseBrake * 20.0, 100.0);
        nativeBrake = clamp(nativeBrake, 0.0, maximumBrake);
        double modeledDragN = output.aerodynamicDragForceN()
                + output.rollingDragForceN()
                + output.engineBrakingForceN();
        double nativeDragScale = 1.0;
        double coastAssistN = 0.0;
        runtime.observedCoastDecelerationMps2 = 0.0;
        boolean automaticCoast = !manual
                && throttle < 0.02
                && serviceBrake <= 0.0
                && !controls.parkingBrake()
                && Math.abs(speedMps) > 0.35;
        if (automaticCoast) {
            double asphaltBlend = 1.0 - clamp01(offroad);
            asphaltBlend *= asphaltBlend;
            double coastDragScale = RoadcraftCalibration.automaticCoastDragScale(mechanicType);
            nativeDragScale += (coastDragScale - 1.0)
                    * asphaltBlend;
            double baseAssistMps2 = RoadcraftCalibration.automaticCoastAssistMps2(mechanicType);
            if (!runtime.automaticCoasting) {
                runtime.coastAssistMps2 = baseAssistMps2;
            } else if (asphaltBlend > 0.50
                    && Math.signum(runtime.lastSpeedMps) == Math.signum(speedMps)
                    && Math.abs(controls.steering()) <= 0.10) {
                runtime.observedCoastDecelerationMps2 = clamp(
                        (Math.abs(runtime.lastSpeedMps) - Math.abs(speedMps)) / deltaSeconds,
                        -5.0,
                        8.0);
                double targetDecelerationMps2 = modeledDragN * coastDragScale / Math.max(mass, 1.0);
                runtime.coastAssistMps2 = RoadcraftCalibration.automaticCoastAssistStep(
                        runtime.coastAssistMps2,
                        runtime.observedCoastDecelerationMps2,
                        targetDecelerationMps2,
                        mechanicType,
                        deltaSeconds);
            }
            coastAssistN = runtime.coastAssistMps2
                    * mass
                    * massScale
                    * RoadcraftCalibration.automaticCoastSpeedBlend(speedMps)
                    * asphaltBlend;
        } else {
            runtime.coastAssistMps2 = 0.0;
        }
        runtime.automaticCoasting = automaticCoast;
        runtime.lastSpeedMps = speedMps;
        double nativeDrag = modeledDragN * massScale * nativeDragScale - coastAssistN;

        if (Math.abs(speedKph) >= maximumSpeedKph
                && Math.signum(nativeEngine) == Math.signum(speedMps)) {
            nativeEngine = 0.0;
        }

        double scriptSteeringClamp = current.steeringClamp(vehicle, speedKph);
        double steeringClamp = clamp(
                scriptSteeringClamp * RoadcraftCalibration.steeringClampMultiplier(
                        mechanicType,
                        speedKph,
                        maximumSpeedKph),
                0.01,
                Math.PI / 2.0);
        double steeringResponse = RoadcraftCalibration.steeringResponseMultiplier(
                mechanicType,
                speedKph,
                maximumSpeedKph);
        if (!runtime.steeringInitialized) {
            runtime.steering = clamp(vanillaSteering, -steeringClamp, steeringClamp);
            runtime.steeringInitialized = true;
        }
        runtime.steering = RoadcraftCalibration.steeringStep(
                runtime.steering,
                controls.steering(),
                speedKph,
                steeringClamp,
                RoadcraftBridge.number("steeringRateLow", 1.0) * steeringResponse,
                RoadcraftBridge.number("steeringRateHigh", 0.10) * steeringResponse,
                RoadcraftBridge.number("steeringCenterLow", 1.0),
                RoadcraftBridge.number("steeringCenterHigh", 0.10),
                RoadcraftBridge.number("steeringSnapback", 3.0),
                RoadcraftBridge.number("steeringHighSpeed", 75.0),
                output.sanitizedDeltaSeconds());
        double appliedSteering = runtime.steering;

        frame.engine = finite((float) nativeEngine);
        frame.brake = finite((float) nativeBrake);
        frame.steering = finite((float) appliedSteering);
        frame.computed = true;

        current.applyControllerFields(frame.controller, frame.engine, frame.brake);
        current.setCurrentSteering(vehicle, frame.steering);
        current.applyDrag(vehicle, nativeDrag);

        if (!runtime.controllerLogged) {
            System.out.println("[RoadcraftDynamics] Controller active: vehicle=" + vehicleId
                    + " script=" + vehicleScript
                    + " profile=" + powertrainCategory
                    + " manual=" + manual
                    + " gameGear=" + gameGear
                    + " appliedGear=" + output.state().gear());
            runtime.controllerLogged = true;
            runtime.lastReportedGear = output.state().gear();
        } else if (runtime.lastReportedGear != output.state().gear()) {
            double roundedSpeed = Math.rint(speedKph * 10.0) / 10.0;
            double roundedRpm = Math.rint(output.state().engineRpm());
            System.out.println("[RoadcraftDynamics] Gear changed: vehicle=" + vehicleId
                    + " mode=" + (manual ? "manual" : "automatic")
                    + " gear=" + runtime.lastReportedGear + "->" + output.state().gear()
                    + " speedKph=" + roundedSpeed
                    + " rpm=" + roundedRpm
                    + " gameGearBeforeApply=" + gameGear);
            runtime.lastReportedGear = output.state().gear();
        }
        if (shiftRequest != 0) {
            System.out.println("[RoadcraftDynamics] Manual shift: vehicle=" + vehicleId
                    + " request=" + (shiftRequest > 0 ? "+" : "") + shiftRequest
                    + " desired=" + desiredGearBeforeShift + "->" + runtime.desiredGear
                    + " gameGear=" + gameGear);
        }
        boolean burnoutActive = output.burnout() && Math.abs(burnoutAmount) > 0.50;
        if (burnoutActive && !runtime.burnoutActive) {
            double roundedAmount = Math.rint(burnoutAmount * 10.0) / 10.0;
            System.out.println("[RoadcraftDynamics] Burnout active: vehicle=" + vehicleId
                    + " gear=" + output.state().gear()
                    + " wheelspinKph=" + roundedAmount);
        }
        runtime.burnoutActive = burnoutActive;

        if (runtime.lastTelemetryNanos == 0L || now - runtime.lastTelemetryNanos >= 2_000_000_000L) {
            runtime.lastTelemetryNanos = now;
            if (Math.abs(speedKph) > 1.0 || throttle > 0.01 || nativeBrake > 0.01) {
                double coupledRpm = DrivetrainModel.coupledEngineRpm(
                        runtime.settings, speedMps, output.state().gear());
                System.out.println("[RoadcraftDynamics] Telemetry: vehicle=" + vehicleId
                        + " script=" + vehicleScript
                        + " profile=" + powertrainCategory
                        + " massKg=" + rounded(mass, 0)
                        + " engineForce=" + rounded(enginePower, 0)
                        + " peakTorqueNm=" + rounded(runtime.settings.engine().peakTorqueNm(), 1)
                        + " mode=" + (manual ? "manual" : "automatic")
                        + " speedKph=" + rounded(speedKph, 1)
                        + " gear=" + output.state().gear()
                        + " rpm=" + rounded(output.state().engineRpm(), 0)
                        + " coupledRpm=" + rounded(coupledRpm, 0)
                        + " throttle=" + rounded(throttle, 2)
                        + " converter=" + rounded(output.converterTorqueMultiplier(), 2)
                        + " delivery=" + rounded(output.driveDeliveryFactor(), 2)
                        + " rawDriveN=" + rounded(output.rawDriveForceN(), 0)
                        + " driveN=" + rounded(output.appliedDriveForceN(), 0)
                        + " tractionLimitN=" + rounded(output.tractionLimitN(), 0)
                        + " brakeN=" + rounded(output.serviceBrakeForceN()
                                + output.parkingBrakeForceN(), 0)
                        + " roadLoadN=" + rounded(output.aerodynamicDragForceN()
                                + output.rollingDragForceN(), 0)
                        + " engineBrakeN=" + rounded(output.engineBrakingForceN(), 0)
                        + " nativeDragN=" + rounded(nativeDrag, 0)
                        + " coastAssistN=" + rounded(coastAssistN, 0)
                        + " coastAssistMps2=" + rounded(runtime.coastAssistMps2, 2)
                        + " coastObservedDecel=" + rounded(runtime.observedCoastDecelerationMps2, 2)
                        + " slip=" + rounded(output.state().wheelSlip(), 2)
                        + " wheelSpeedKph=" + rounded(runtime.tireSpeedKph, 1)
                        + " wheelspinKph=" + rounded(burnoutAmount, 1)
                        + " massScale=" + rounded(massScale, 3)
                        + " dt=" + rounded(output.sanitizedDeltaSeconds(), 4)
                        + " steerInput=" + rounded(controls.steering(), 2)
                        + " steerApplied=" + rounded(appliedSteering, 3)
                        + " steerClamp=" + rounded(steeringClamp, 3)
                        + " steerScriptClamp=" + rounded(scriptSteeringClamp, 3)
                        + " steerResponse=" + rounded(steeringResponse, 2));
            }
        }
    }

    private static PhysicsSettings buildSettings(
            PzAccess current,
            Object vehicle,
            double mass,
            double enginePower,
            double wheelRadius,
            double offroad,
            int mechanicType,
            double maximumSpeedKph) throws ReflectiveOperationException {
        double redline = clamp(RoadcraftBridge.number("redlineRpm", 4_500.0), 3_500.0, 8_000.0);
        double idle = clamp(current.engineIdleRpm(vehicle), 500.0, redline * 0.35);
        String category = RoadcraftCalibration.powertrainCategory(mechanicType);
        double torqueMultiplier = Math.max(0.0, RoadcraftBridge.number("torque" + category, 1.0));
        double peakTorque = RoadcraftCalibration.peakTorqueNm(
                enginePower,
                mechanicType,
                torqueMultiplier);
        double peakTorqueRpm = clamp(
                redline * RoadcraftCalibration.peakTorqueRpmFraction(mechanicType),
                idle + 100.0,
                redline - 100.0);

        int gearCount = current.gearCount(vehicle);
        double[] ratios = RoadcraftCalibration.forwardGearRatios(gearCount);

        double overallTraction = clamp(RoadcraftBridge.number("overallTraction", 1.0), 0.0, 10.0);
        double accelerationTraction = clamp(RoadcraftBridge.number("accelerationTraction", 1.0), 0.0, 10.0);
        if (!RoadcraftBridge.bool("tractionEnabled", true)) {
            overallTraction = 1.0;
        }

        double roadRolling = Math.max(0.0, RoadcraftBridge.number("rollingResistance", 0.05)) * 0.29;
        double offroadRolling = roadRolling
                + Math.max(0.0, RoadcraftBridge.number("offroadRollingResistance", 0.20)) * 0.15;
        double rollingCoefficient = roadRolling + (offroadRolling - roadRolling) * clamp01(offroad);
        double aero = Math.max(0.0, RoadcraftBridge.number("aeroDrag" + category, 1.0));
        aero += Math.max(0.0, RoadcraftBridge.number("rollingResistanceSpeed", 0.10)) * 0.25;
        if (offroad > 0.0) {
            aero += Math.max(0.0, RoadcraftBridge.number("offroadRollingResistanceSpeed", 1.0))
                    * 0.25 * offroad;
        }

        double lowSteer = Math.max(0.01, RoadcraftBridge.number("steeringRateLow", 1.0));
        double highSteer = Math.max(0.01, RoadcraftBridge.number("steeringRateHigh", 0.10));
        double highReferenceMps = Math.max(3.0, RoadcraftBridge.number("steeringHighSpeed", 75.0) / 3.6);
        double serviceBrakeN = Math.max(0.0, current.baseBrakingForce(vehicle) / NATIVE_BRAKE_SCALE);

        return new PhysicsSettings(
                mass,
                clamp(wheelRadius, 0.12, 1.5),
                clamp(RoadcraftBridge.number("reverseSpeedLimit", 40.0) / 3.6, 1.0, 40.0),
                new PhysicsSettings.Engine(
                        idle,
                        peakTorqueRpm,
                        redline,
                        peakTorque * RoadcraftCalibration.idleTorqueFraction(mechanicType),
                        peakTorque,
                        peakTorque * RoadcraftCalibration.redlineTorqueFraction(mechanicType),
                        10_000.0),
                new PhysicsSettings.Transmission(
                        ratios,
                        RoadcraftCalibration.reverseGearRatio(gearCount),
                        RoadcraftCalibration.finalDriveRatio(redline, maximumSpeedKph, wheelRadius),
                        0.88,
                        redline * 0.855556,
                        redline * 0.966667),
                new PhysicsSettings.Converter(
                        RoadcraftCalibration.torqueConverterMultiplier(
                                RoadcraftBridge.number("torqueConverterLimit", 2.5),
                                mechanicType),
                        2_000.0),
                new PhysicsSettings.Grip(
                        overallTraction,
                        0.0,
                        1.0,
                        0.50,
                        clamp(
                                RoadcraftCalibration.drivenWeightFraction(mechanicType)
                                        * accelerationTraction,
                                0.0,
                                2.5),
                        4.6,
                        2.1),
                new PhysicsSettings.Brakes(serviceBrakeN, serviceBrakeN * 3.0, 0.40, 0.46),
                new PhysicsSettings.Resistance(aero, rollingCoefficient),
                new PhysicsSettings.Steering(
                        Math.toRadians(32.5),
                        highReferenceMps,
                        clamp(highSteer / lowSteer, 0.05, 1.0)),
                new PhysicsSettings.TimeStep(1.0 / 240.0, 0.10, 1.0 / 60.0));
    }

    private static PzAccess access() {
        PzAccess current = access;
        if (current != null || accessAttempted) {
            return current;
        }
        synchronized (ACCESS_LOCK) {
            if (access == null && !accessAttempted) {
                accessAttempted = true;
                try {
                    access = new PzAccess();
                } catch (Throwable error) {
                    RoadcraftBridge.markFatal("B42.20.4 runtime adapter initialization failed: " + error);
                }
            }
            return access;
        }
    }

    private static void expose(
            Method exposerMethod,
            Object exposer,
            Object environment,
            String luaName,
            String javaName,
            Class<?>... parameters) throws ReflectiveOperationException {
        Method javaMethod = RoadcraftBridge.class.getMethod(javaName, parameters);
        exposerMethod.invoke(exposer, environment, RoadcraftBridge.class, javaMethod, luaName);
    }

    private static int clampGear(int gear, int maximum) {
        return Math.max(-1, Math.min(maximum, gear));
    }

    private static float finite(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double rounded(double value, int decimals) {
        double scale = Math.pow(10.0, Math.max(0, decimals));
        return Math.rint(value * scale) / scale;
    }

    private record ImpulseScope(Object vehicle, String key, int startIndex) {
    }

    /** Input snapshot copied into CarController.ClientControls. */
    public record DirectControls(
            boolean available,
            float steering,
            boolean forward,
            boolean backward,
            boolean brake,
            boolean shift,
            boolean wasUsingParkingBrakes,
            long forceBrake) {
        private static DirectControls unavailable(long forceBrake) {
            return new DirectControls(
                    false, 0.0f, false, false, false, false, false, forceBrake);
        }
    }

    /** ABI-neutral result copied by the loose clean-room CarController. */
    public record DirectControllerResult(
            float engineForce,
            float brakingForce,
            float vehicleSteering,
            float speed,
            boolean gas,
            boolean gasReverse,
            boolean breaking,
            boolean acceleratorOn,
            boolean brakeOn,
            boolean nativeDispatch) {
        private static DirectControllerResult idle() {
            return idle(0.0f);
        }

        private static DirectControllerResult idle(float speed) {
            return new DirectControllerResult(
                    0.0f, 0.0f, 0.0f, speed,
                    false, false, false, false, false, false);
        }
    }

    private static final class ControllerFrame {
        private final Object controller;
        private final Object vehicle;
        private boolean computed;
        private float engine;
        private float brake;
        private float steering;

        private ControllerFrame(Object controller, Object vehicle) {
            this.controller = controller;
            this.vehicle = vehicle;
        }
    }

    private static final class VehicleRuntime {
        private PhysicsSettings settings;
        private VehicleState state;
        private long configurationRevision = Long.MIN_VALUE;
        private long lastTelemetryNanos;
        private double settingsMass;
        private double settingsEnginePower;
        private double settingsWheelRadius;
        private double settingsOffroad;
        private double settingsMaximumSpeedKph;
        private double steering;
        private double throttle;
        private double tireSpeedKph;
        private double burnoutAmountKph;
        private double lastSpeedMps;
        private double coastAssistMps2;
        private double observedCoastDecelerationMps2;
        private double visualDeltaSeconds = 1.0 / 60.0;
        private final PzAccess.WheelVisualState wheelVisuals = new PzAccess.WheelVisualState();
        private int settingsMechanicType = Integer.MIN_VALUE;
        private int lastReportedGear = Integer.MIN_VALUE;
        private int desiredGear;
        private boolean initialized;
        private boolean steeringInitialized;
        private boolean controllerLogged;
        private boolean burnoutActive;
        private boolean automaticCoasting;
        private boolean parkingBrake;
    }

}
