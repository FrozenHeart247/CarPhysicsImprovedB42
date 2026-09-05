package pzmod.carphysicsimproved.v1;

import dev.carphysicsimproved.v1.runtime.LegacyHooks;
import me.zed_0xff.zombie_buddy.Patch;

/** Release local CPI state as soon as native network ownership changes. */
@Patch(className = "zombie.vehicles.BaseVehicle", methodName = "setNetPlayerAuthorization",
        warmUp = true, strictMatch = true)
public final class Patch_BaseVehicleAuthority {
    private Patch_BaseVehicleAuthority() { }

    @Patch.OnExit
    public static void exit(@Patch.This Object vehicle, @Patch.Argument(0) Object authorization,
            @Patch.Argument(1) int playerId) {
        // Explicit arguments are essential: a This-only advice is matched by
        // ZB as a zero-argument method and misses this two-argument setter.
        LegacyHooks.onVehicleAuthorizationChanged(vehicle);
    }
}
