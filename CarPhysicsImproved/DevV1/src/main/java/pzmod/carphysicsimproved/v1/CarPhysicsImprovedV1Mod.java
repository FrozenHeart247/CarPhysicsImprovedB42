package pzmod.carphysicsimproved.v1;

import dev.carphysicsimproved.v1.BuildInfo;
import dev.carphysicsimproved.v1.physics.LegacyPhysics;
import dev.carphysicsimproved.v1.physics.LegacySlideDynamics;
import me.zed_0xff.zombie_buddy.Exposer;

import java.util.concurrent.ConcurrentHashMap;

/** Lua-facing configuration and compatibility API for the legacy V1 port. */
@Exposer.LuaClass
public final class CarPhysicsImprovedV1Mod {
    private static final ConcurrentHashMap<Integer, Integer> SHIFT_REQUESTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Double> BURNOUT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Double> SKID = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, VehicleOverride> VEHICLE_OVERRIDES = new ConcurrentHashMap<>();
    private static volatile boolean enabled = true;
    private static volatile boolean manualTransmission;
    private static volatile boolean telemetry;
    private static volatile LegacyPhysics.Settings settings = LegacyPhysics.Settings.defaults();
    private static volatile LegacySlideDynamics.Tuning slideTuning = LegacySlideDynamics.Tuning.defaults();
    private static volatile double rainTraction = 0.70;
    private static volatile double snowTraction = 0.40;
    private static volatile double offroadTraction = 0.60;
    private static volatile double plantImpulse = 0.30;
    private static volatile double zombieImpulse = 0.50;
    private static volatile double corpseImpulse = 1.0;
    private static volatile boolean trunkOverhaul;
    private static volatile double trunkMultiplier = 1.0;
    private static volatile double trunkAdder;
    private static volatile double otherTrunkMultiplier = 1.0;
    private static volatile double otherTrunkAdder;
    private static volatile String status = "loaded; waiting for a locally controlled vehicle";

    public CarPhysicsImprovedV1Mod() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void setManualTransmission(boolean value) {
        manualTransmission = value;
    }

    public static void setTelemetry(boolean value) {
        telemetry = value;
    }

    public static void configurePhysics(
            double sportTorque,
            double standardTorque,
            double heavyTorque,
            double torqueMultiplierLimit,
            double reverseSpeedLimit,
            double aerodynamicSport,
            double aerodynamicStandard,
            double aerodynamicHeavy,
            double rollingResistance,
            double rollingResistanceSpeed,
            double offroadRollingResistance,
            double offroadRollingResistanceSpeed,
            double overallTraction,
            double accelerationTraction,
            double requestedOffroadTraction,
            double requestedRainTraction,
            double requestedSnowTraction) {
        settings = new LegacyPhysics.Settings(
                clamp(sportTorque, 0.0, 5.0),
                clamp(standardTorque, 0.0, 5.0),
                clamp(heavyTorque, 0.0, 5.0),
                clamp(torqueMultiplierLimit, 1.0, 4.0),
                clamp(reverseSpeedLimit, 5.0, 120.0),
                clamp(aerodynamicSport, 0.0, 10.0),
                clamp(aerodynamicStandard, 0.0, 10.0),
                clamp(aerodynamicHeavy, 0.0, 10.0),
                clamp(rollingResistance, 0.0, 10.0),
                clamp(rollingResistanceSpeed, 0.0, 10.0),
                clamp(offroadRollingResistance, 0.0, 10.0),
                clamp(offroadRollingResistanceSpeed, 0.0, 10.0),
                clamp(overallTraction, 0.0, 10.0),
                clamp(accelerationTraction, 0.0, 10.0),
                1.0, 0.1, 1.0, 0.1, 3.0, 75.0, 2_000.0, 800.0);
        offroadTraction = clamp(requestedOffroadTraction, 0.0, 1.0);
        rainTraction = clamp(requestedRainTraction, 0.0, 1.0);
        snowTraction = clamp(requestedSnowTraction, 0.0, 1.0);
    }

    public static void configureSteering(double factorLowSpeed, double factorHighSpeed,
            double centeringLowSpeed, double centeringHighSpeed, double snapback, double highSpeedKph) {
        LegacyPhysics.Settings current = settings;
        settings = new LegacyPhysics.Settings(
                current.sportTorqueMultiplier(), current.standardTorqueMultiplier(), current.heavyTorqueMultiplier(),
                current.torqueMultiplierLimit(), current.reverseSpeedLimitKph(),
                current.aerodynamicDragSport(), current.aerodynamicDragStandard(), current.aerodynamicDragHeavy(),
                current.rollingResistance(), current.rollingResistanceSpeed(),
                current.offroadRollingResistance(), current.offroadRollingResistanceSpeed(),
                current.overallTraction(), current.accelerationTraction(),
                clamp(factorLowSpeed, 0.0, 10.0), clamp(factorHighSpeed, 0.0, 10.0),
                clamp(centeringLowSpeed, 0.0, 10.0), clamp(centeringHighSpeed, 0.0, 10.0),
                clamp(snapback, 0.0, 10.0), clamp(highSpeedKph, 10.0, 120.0),
                current.converterLockupRpm(), current.converterLockupRangeRpm());
    }

    public static void configureSlide(boolean enabledValue, double driftIntensity,
            double stabilityAssist, double powerDriftEntryDelaySeconds, boolean clutchKickEnabled) {
        slideTuning = new LegacySlideDynamics.Tuning(
                enabledValue,
                driftIntensity,
                stabilityAssist,
                powerDriftEntryDelaySeconds,
                clutchKickEnabled);
    }

    public static void configureImpulses(double plant, double zombie, double corpse) {
        plantImpulse = clamp(plant, 0.0, 10.0);
        zombieImpulse = clamp(zombie, 0.0, 10.0);
        corpseImpulse = clamp(corpse, 0.0, 10.0);
    }

    public static void configureTrunk(boolean enabledValue, double multiplier, double adder,
            double fallbackMultiplier, double fallbackAdder) {
        trunkOverhaul = enabledValue;
        trunkMultiplier = clamp(multiplier, 0.0, 100.0);
        trunkAdder = clamp(adder, -10_000.0, 10_000.0);
        otherTrunkMultiplier = clamp(fallbackMultiplier, 0.0, 100.0);
        otherTrunkAdder = clamp(fallbackAdder, -10_000.0, 10_000.0);
    }

    public static void requestShiftFor(int vehicleId, int direction) {
        int request = Math.max(-1, Math.min(1, direction));
        if (vehicleId >= 0 && request != 0) {
            SHIFT_REQUESTS.merge(vehicleId, request, Integer::sum);
        }
    }

    /**
     * Clean compatibility point for Workshop vehicles. A patch may provide
     * real-world figures without depending on the deleted RCP Mod ID.
     */
    public static void registerVehicleSpec(String fullType, double horsePower, double massKg, double cargoKg) {
        if (fullType == null || fullType.isBlank()) {
            return;
        }
        VEHICLE_OVERRIDES.put(fullType, new VehicleOverride(
                clamp(horsePower, 1.0, 5_000.0),
                clamp(massKg, 100.0, 20_000.0),
                clamp(cargoKg, 0.0, 100_000.0)));
    }

    public static void unregisterVehicleSpec(String fullType) {
        if (fullType != null) {
            VEHICLE_OVERRIDES.remove(fullType);
        }
    }

    public static VehicleOverride vehicleOverride(String fullType) {
        return fullType == null ? null : VEHICLE_OVERRIDES.get(fullType);
    }

    public static int consumeShiftRequest(int vehicleId) {
        Integer request = SHIFT_REQUESTS.remove(vehicleId);
        return request == null ? 0 : Math.max(-1, Math.min(1, request));
    }

    public static void updateEffects(int vehicleId, double burnoutAmount, double skidAmount) {
        BURNOUT.put(vehicleId, sanitizePositive(burnoutAmount));
        SKID.put(vehicleId, sanitizePositive(skidAmount));
    }

    public static double burnoutAmountFor(int vehicleId) {
        return BURNOUT.getOrDefault(vehicleId, 0.0);
    }

    public static double skidAmountFor(int vehicleId) {
        return SKID.getOrDefault(vehicleId, 0.0);
    }

    public static boolean enabled() {
        return enabled;
    }

    public static boolean manualTransmission() {
        return manualTransmission;
    }

    public static boolean telemetry() {
        return telemetry;
    }

    public static LegacyPhysics.Settings settings() {
        return settings;
    }

    public static LegacySlideDynamics.Tuning slideTuning() {
        return slideTuning;
    }

    public static double rainTraction() {
        return rainTraction;
    }

    public static double snowTraction() {
        return snowTraction;
    }

    public static double offroadTraction() {
        return offroadTraction;
    }

    public static double plantImpulse() {
        return plantImpulse;
    }

    public static double zombieImpulse() {
        return zombieImpulse;
    }

    public static double corpseImpulse() {
        return corpseImpulse;
    }

    public static boolean trunkOverhaul() {
        return trunkOverhaul;
    }

    public static double trunkMultiplier() {
        return trunkMultiplier;
    }

    public static double trunkAdder() {
        return trunkAdder;
    }

    public static double otherTrunkMultiplier() {
        return otherTrunkMultiplier;
    }

    public static double otherTrunkAdder() {
        return otherTrunkAdder;
    }

    public static void updateStatus(String value) {
        status = value == null ? "unknown" : value;
    }

    public static String status() {
        return status;
    }

    public static String runtimeVersion() {
        return BuildInfo.VERSION;
    }

    public static String testedGameVersion() {
        return BuildInfo.TESTED_GAME;
    }

    private static double sanitizePositive(double value) {
        return Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        if (!Double.isFinite(value)) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    public record VehicleOverride(double horsePower, double massKg, double cargoKg) {
    }
}
