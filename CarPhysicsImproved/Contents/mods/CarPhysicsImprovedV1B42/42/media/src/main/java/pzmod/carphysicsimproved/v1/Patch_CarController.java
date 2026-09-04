package pzmod.carphysicsimproved.v1;

import dev.carphysicsimproved.v1.runtime.LegacyHooks;
import me.zed_0xff.zombie_buddy.Patch;

public final class Patch_CarController {
    private Patch_CarController() {
    }

    @Patch(className = "zombie.core.physics.CarController", methodName = "updateControls",
            warmUp = true, strictMatch = true)
    public static final class Controls {
        private Controls() {
        }

        @Patch.OnExit
        public static void exit(@Patch.This Object controller) {
            LegacyHooks.afterControllerControls(controller);
        }
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
            LegacyHooks.afterControllerUpdate(controller);
        }
    }
}
