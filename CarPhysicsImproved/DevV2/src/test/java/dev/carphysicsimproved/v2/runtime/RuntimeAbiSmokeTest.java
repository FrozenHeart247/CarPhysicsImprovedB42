package dev.carphysicsimproved.v2.runtime;

import pzmod.carphysicsimproved.CarPhysicsImprovedMod;

/** Constructs the complete reflection adapter against the installed game ABI. */
public final class RuntimeAbiSmokeTest {
    private RuntimeAbiSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        new PzRuntimeAccess();
        Class<?> transmission = Class.forName("zombie.vehicles.BaseVehicle")
                .getDeclaredField("transmissionNumber").getType();
        if (!transmission.getName().equals("zombie.vehicles.TransmissionNumber")) {
            throw new AssertionError("Direct transmission mirror field changed type: " + transmission.getName());
        }
        Class.forName("zombie.vehicles.BaseVehicle").getDeclaredMethod("crash", float.class, boolean.class);
        CarPhysicsImprovedMod.setPhysicsTuning(1.20, 0.80, 1.10, 0.75, 0.90, 2.25, true);
        if (CarPhysicsImprovedMod.physicsTuning().enginePowerMultiplier() != 1.20
                || CarPhysicsImprovedMod.physicsTuning().roadResistanceMultiplier() != 0.80
                || CarPhysicsImprovedMod.physicsTuning().steeringSensitivityMultiplier() != 0.90
                || CarPhysicsImprovedMod.physicsTuning().driftEntryDelaySeconds() != 2.25
                || CarPhysicsImprovedMod.tireGripMultiplier() != 1.10
                || CarPhysicsImprovedMod.recoveryStrengthMultiplier() != 0.75
                || !CarPhysicsImprovedMod.vanillaCollisionResponse()) {
            throw new AssertionError("Lua-facing Sandbox profile must update atomically");
        }
        CarPhysicsImprovedMod.setPhysicsTuning(1.0, 1.0, 1.0, 1.0, 1.0, 1.50, true);
        if (PzRuntimeAccess.nativeSteeringInput(-1.0) != 1.0
                || PzRuntimeAccess.nativeSteeringInput(1.0) != -1.0) {
            throw new AssertionError("CarController steering sign must be inverted at the PZ boundary");
        }
        if (RuntimeHooks.manualGearAfterShift(-1, 1, 5) != 0
                || RuntimeHooks.manualGearAfterShift(0, 1, 5) != 1
                || RuntimeHooks.manualGearAfterShift(1, -1, 5) != 0
                || RuntimeHooks.manualGearAfterShift(0, -1, 5) != -1
                || RuntimeHooks.manualGearAfterShift(5, 1, 5) != 5) {
            throw new AssertionError("Manual selector must follow R-N-1-2-... with bounded endpoints");
        }
        RuntimeHooks.StabilityRecovery normal = RuntimeHooks.recoverUnintendedSlide(
                15.0, 1.0, 0.50, 1_200.0, 2.6, false, 2_000.0, -1_500.0);
        if (normal.amount() != 0.0
                || normal.lateralCommandN() != 2_000.0
                || normal.yawCommandNm() != -1_500.0) {
            throw new AssertionError("ordinary steering must remain identical to the 0.1.9 baseline");
        }
        RuntimeHooks.StabilityRecovery runaway = RuntimeHooks.recoverUnintendedSlide(
                17.0, 8.0, 2.5, 1_200.0, 2.6, false, 5_000.0, -5_000.0);
        if (runaway.amount() < 0.95
                || runaway.lateralCommandN() >= 0.0
                || runaway.yawCommandNm() <= 0.0) {
            throw new AssertionError("unintended runaway must counter sideslip and damp measured yaw");
        }
        RuntimeHooks.StabilityRecovery intentional = RuntimeHooks.recoverUnintendedSlide(
                17.0, 8.0, 2.5, 1_200.0, 2.6, true, 5_000.0, -5_000.0);
        if (intentional.amount() != 0.0
                || intentional.lateralCommandN() != 5_000.0
                || intentional.yawCommandNm() != -5_000.0) {
            throw new AssertionError("handbrake and intentional drift must bypass emergency recovery");
        }
        RuntimeHooks.StabilityRecovery disabled = RuntimeHooks.recoverUnintendedSlide(
                17.0, 8.0, 2.5, 1_200.0, 2.6, false, 0.0, 5_000.0, -5_000.0);
        if (disabled.amount() != 0.0
                || disabled.lateralCommandN() != 5_000.0
                || disabled.yawCommandNm() != -5_000.0) {
            throw new AssertionError("zero recovery sandbox strength must leave V2 steering untouched");
        }
        double lightCrashGrace = RuntimeHooks.collisionGraceSeconds(2.0);
        double heavyCrashGrace = RuntimeHooks.collisionGraceSeconds(30.0);
        if (lightCrashGrace < 0.30 || heavyCrashGrace > 0.60
                || heavyCrashGrace <= lightCrashGrace) {
            throw new AssertionError("vanilla collision window must be bounded and impact-sensitive");
        }
        System.out.println("RuntimeAbiSmokeTest: B42.20.4 adapter constructed successfully");
    }
}
