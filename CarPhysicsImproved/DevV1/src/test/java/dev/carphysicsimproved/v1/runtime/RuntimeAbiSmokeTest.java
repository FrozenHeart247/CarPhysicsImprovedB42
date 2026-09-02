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
        System.out.println("RuntimeAbiSmokeTest: B42.20.4 legacy adapter constructed successfully");
    }
}
