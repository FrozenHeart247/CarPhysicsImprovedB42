package pzmod.carphysicsimproved.v1;

import dev.carphysicsimproved.v1.runtime.LegacyHooks;
import me.zed_0xff.zombie_buddy.Patch;

/** Observes vanilla crash events without replacing collision response or damage. */
@Patch(
        className = "zombie.vehicles.BaseVehicle",
        methodName = "crash",
        warmUp = true,
        strictMatch = true)
public final class Patch_BaseVehicle {
    private Patch_BaseVehicle() {
    }

    @Patch.OnEnter
    public static void enter(
            @Patch.This Object vehicle,
            @Patch.Argument(0) float impactDelta) {
        LegacyHooks.onVehicleCrash(vehicle, impactDelta);
    }
}
