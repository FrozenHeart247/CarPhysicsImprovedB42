package pzmod.roadcraft;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.roadcraft.runtime.RoadcraftHooks;

/** Keeps vanilla lifecycle behavior, then replaces only the final physics command. */
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

        @Patch.OnEnter
        public static void enter() {
            RoadcraftHooks.beginVanillaControllerUpdate();
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit(@Patch.This Object controller) {
            RoadcraftHooks.endVanillaControllerUpdate();
            RoadcraftHooks.afterControllerUpdate(controller, false);
        }
    }

    @Patch(
            className = "zombie.core.physics.CarController",
            methodName = "updateTrailer",
            warmUp = true,
            strictMatch = true)
    public static final class UpdateTrailer {
        private UpdateTrailer() {
        }

        @Patch.OnExit
        public static void exit(@Patch.This Object controller) {
            RoadcraftHooks.afterControllerUpdate(controller, true);
        }
    }
}
