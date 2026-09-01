package pzmod.carphysicsimproved;

import dev.carphysicsimproved.v2.runtime.RuntimeHooks;
import me.zed_0xff.zombie_buddy.Patch;

/** Keeps vanilla lifecycle behavior, then replaces the final force request. */
public final class Patch_CarController {
    private Patch_CarController() {
    }

    @Patch(
            className = "zombie.core.physics.CarController",
            methodName = "update",
            warmUp = true,
            strictMatch = true)
    public static final class Update {
        private Update() {
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit(@Patch.This Object controller) {
            RuntimeHooks.afterControllerUpdate(controller);
        }
    }
}
