package dev.carphysicsimproved.v1.runtime;

import pzmod.carphysicsimproved.v1.CarPhysicsImprovedV1Mod;

public final class RuntimeAbiSmokeTest {
    private RuntimeAbiSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        new PzLegacyAccess();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Class<?> vehicle = Class.forName("zombie.vehicles.BaseVehicle", false, loader);
        Class<?> object = Class.forName("zombie.iso.IsoObject", false, loader);
        Class<?> character = Class.forName("zombie.characters.IsoGameCharacter", false, loader);
        Class<?> corpse = Class.forName("zombie.iso.objects.IsoDeadBody", false, loader);
        vehicle.getDeclaredMethod("applyImpulseFromHitPlant", object, float.class);
        vehicle.getDeclaredMethod("applyImpulseFromHitPedestrian", character);
        vehicle.getDeclaredMethod("applyImpulseFromHitCorpse", corpse);
        vehicle.getDeclaredMethod("crash", float.class, boolean.class);
        vehicle.getDeclaredField("impulsesFromHitObjects");
        Class<?> impulse = Class.forName("zombie.vehicles.BaseVehicle$VehicleImpulse", false, loader);
        impulse.getDeclaredField("impulse");
        Class<?> container = Class.forName("zombie.inventory.ItemContainer", false, loader);
        container.getDeclaredMethod("getCapacity");
        container.getDeclaredMethod("getVehicle");
        container.getDeclaredMethod("getVehiclePart");
        Class<?> part = Class.forName("zombie.vehicles.VehiclePart", false, loader);
        part.getDeclaredMethod("getArea");
        part.getDeclaredMethod("getScriptPart");
        Class<?> scriptPart = Class.forName("zombie.scripting.objects.VehicleScript$Part", false, loader);
        scriptPart.getDeclaredMethod("getId");
        Class<?> vector3f = Class.forName("org.joml.Vector3f", false, loader);
        vehicle.getDeclaredMethod("getForwardVector", vector3f);
        Class<?> vehicleScript = Class.forName("zombie.scripting.objects.VehicleScript", false, loader);
        vehicleScript.getDeclaredMethod("getFullName");
        vehicleScript.getDeclaredMethod("getWheelCount");
        vehicleScript.getDeclaredMethod("getWheel", int.class);
        Class<?> scriptWheel = Class.forName("zombie.scripting.objects.VehicleScript$Wheel", false, loader);
        scriptWheel.getDeclaredMethod("getOffset");
        Class<?> colorInfo = Class.forName("zombie.core.textures.ColorInfo", false, loader);
        colorInfo.getDeclaredConstructor(float.class, float.class, float.class, float.class);
        colorInfo.getDeclaredMethod("set", float.class, float.class, float.class, float.class);
        Class<?> isoSprite = Class.forName("zombie.iso.sprite.IsoSprite", false, loader);
        isoSprite.getDeclaredConstructor();
        isoSprite.getDeclaredMethod("LoadFrameExplicit", String.class);
        isoSprite.getDeclaredMethod("renderBloodSplat",
                float.class, float.class, float.class, colorInfo);
        Class<?> fboRenderManager = Class.forName(
                "zombie.iso.fboRenderChunk.FBORenderChunkManager", false, loader);
        fboRenderManager.getDeclaredMethod("endFrame");
        LegacyTireTrackRenderer.validateRuntimeAbi();

        CarPhysicsImprovedV1Mod.registerVehicleSpec("Example.ModCar", 250.0, 1_500.0, 300.0);
        var override = CarPhysicsImprovedV1Mod.vehicleOverride("Example.ModCar");
        if (override == null || override.horsePower() != 250.0 || override.massKg() != 1_500.0
                || override.cargoKg() != 300.0) {
            throw new AssertionError("Workshop vehicle registration API failed");
        }
        if (LegacyHooks.manualGearAfterShift(-1, 1, 5) != 0
                || LegacyHooks.manualGearAfterShift(0, 1, 5) != 1
                || LegacyHooks.manualGearAfterShift(1, -1, 5) != 0
                || LegacyHooks.manualGearAfterShift(0, -1, 5) != -1
                || LegacyHooks.manualGearAfterShift(5, 1, 5) != 5) {
            throw new AssertionError("manual R-N-1-2 selector is not bounded");
        }
        CarPhysicsImprovedV1Mod.configureSlide(
                true, 1.2, 0.8, 1.1, false,
                1_600.0, 2_200.0, 0.58, 0.52, 1.65);
        var slide = CarPhysicsImprovedV1Mod.slideTuning();
        if (slide.driftIntensity() != 1.2 || slide.stabilityAssist() != 0.8
                || slide.powerDriftEntryDelaySeconds() != 1.1 || slide.clutchKickEnabled()
                || slide.powerDriftRotation() != 1_600.0
                || slide.handbrakeDriftRotation() != 2_200.0
                || slide.powerWheelFrictionScale() != 0.58
                || slide.handbrakeWheelFrictionScale() != 0.52
                || slide.driftSteeringMultiplier() != 1.65) {
            throw new AssertionError("Lua-facing slide Sandbox profile was not applied atomically");
        }
        CarPhysicsImprovedV1Mod.configureTerrain(0.70, 0.50, 0.80, 0.40, 0.60);
        var terrain = CarPhysicsImprovedV1Mod.terrainTuning();
        if (terrain.heavyOffroadAdvantage() != 0.70
                || terrain.heavyRainAdvantage() != 0.50
                || terrain.heavySnowAdvantage() != 0.80
                || terrain.heavyOffroadResistanceScale() != 0.40
                || terrain.nativeFrictionInfluence() != 0.60) {
            throw new AssertionError("Lua-facing terrain Sandbox profile was not applied atomically");
        }
        System.out.println("RuntimeAbiSmokeTest: B42.20.4 legacy adapter constructed successfully");
    }
}
