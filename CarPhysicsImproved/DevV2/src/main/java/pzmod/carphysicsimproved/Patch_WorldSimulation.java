package pzmod.carphysicsimproved;

import dev.carphysicsimproved.v2.runtime.RuntimeHooks;
import me.zed_0xff.zombie_buddy.Patch;

/** Reapplies wheel-slip diagnostics after native Bullet readback. */
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
        RuntimeHooks.afterVehiclePhysics();
    }
}
