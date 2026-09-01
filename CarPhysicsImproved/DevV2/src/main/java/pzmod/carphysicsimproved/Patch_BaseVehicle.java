package pzmod.carphysicsimproved;

import dev.carphysicsimproved.v2.runtime.RuntimeHooks;
import me.zed_0xff.zombie_buddy.Patch;

/** Observes vanilla crash events without changing damage or Bullet collision code. */
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
        RuntimeHooks.onVehicleCrash(vehicle, impactDelta);
    }
}
