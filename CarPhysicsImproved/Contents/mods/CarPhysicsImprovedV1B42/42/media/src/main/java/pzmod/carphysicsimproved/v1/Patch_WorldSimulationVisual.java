package pzmod.carphysicsimproved.v1;

import dev.carphysicsimproved.v1.runtime.LegacyHooks;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.core.physics.WorldSimulation", methodName = "updateInternal",
        warmUp = true, strictMatch = true)
public final class Patch_WorldSimulationVisual {
    private Patch_WorldSimulationVisual() { }

    @Patch.OnExit
    public static void exit() { LegacyHooks.afterVehicleReadback(); }
}
