package pzmod.carphysicsimproved.v1;

import dev.carphysicsimproved.v1.runtime.LegacyAxleDriftHooks;
import me.zed_0xff.zombie_buddy.Patch;

/** Alter only a fresh wheel's friction slot, before vanilla submits the complete array to Bullet. */
@Patch(className = "zombie.vehicles.BaseVehicle", methodName = "updateBulletStatsWheel",
        warmUp = true, strictMatch = true)
public final class Patch_BaseVehicleWheelGrip {
    private Patch_BaseVehicleWheelGrip() { }

    @Patch.OnExit
    public static void exit(@Patch.This Object vehicle, @Patch.Argument(0) int wheel,
            @Patch.Argument(1) Object parameters) {
        // ZB's argument matcher unwraps float[] to float (its scalar-holder
        // convention), which silently misses the game's actual array argument.
        // Receive the same array as Object; never replace it or copy its contents.
        if (parameters instanceof float[] wheelParameters) {
            LegacyAxleDriftHooks.afterWheel(vehicle, wheel, wheelParameters);
        }
    }
}
