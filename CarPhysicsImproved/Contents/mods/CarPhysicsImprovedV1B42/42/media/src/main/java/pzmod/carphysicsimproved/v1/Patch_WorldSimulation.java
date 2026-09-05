package pzmod.carphysicsimproved.v1;

import dev.carphysicsimproved.v1.runtime.LegacyHooks;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(
        className = "zombie.core.physics.WorldSimulation",
        methodName = "updateVehiclePhysics",
        warmUp = true,
        strictMatch = true)
public final class Patch_WorldSimulation {
    private Patch_WorldSimulation() {
    }

    @Patch.OnExit
    public static void exit() {
        // updatePhysic calls this once immediately before EACH Bullet step.
        LegacyHooks.afterVehiclePhysics();
    }
}
