package dev.carphysicsimproved.v2.runtime;

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
        System.out.println("RuntimeAbiSmokeTest: B42.20.4 adapter constructed successfully");
    }
}
