package pzmod.roadcraft;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.roadcraft.runtime.RoadcraftHooks;

/** Reapplies the configured native mass immediately before each Bullet update. */
@Patch(
        className = "zombie.core.physics.WorldSimulation",
        methodName = "updateVehiclePhysics",
        warmUp = true,
        strictMatch = true)
public final class Patch_WorldSimulation {
    private Patch_WorldSimulation() {
    }

    @Patch.OnEnter
    public static void enter() {
        RoadcraftHooks.beforeVehiclePhysics();
    }

    @Patch.OnExit
    public static void exit() {
        RoadcraftHooks.afterVehiclePhysics();
    }
}
