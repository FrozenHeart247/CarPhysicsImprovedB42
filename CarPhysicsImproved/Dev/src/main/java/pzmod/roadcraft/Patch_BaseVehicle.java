package pzmod.roadcraft;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.roadcraft.runtime.RoadcraftHooks;

/** Scales only the impulses appended by the three vanilla collision methods. */
public final class Patch_BaseVehicle {
    private Patch_BaseVehicle() {
    }

    @Patch(
            className = "zombie.vehicles.BaseVehicle",
            methodName = "tryStartEngine",
            warmUp = true,
            strictMatch = true)
    public static final class AutoStart {
        private AutoStart() {
        }

        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return RoadcraftHooks.shouldSkipControllerAutoStart();
        }
    }

    @Patch(
            className = "zombie.vehicles.BaseVehicle",
            methodName = "applyImpulseFromHitPlant",
            warmUp = true)
    public static final class HitPlant {
        private HitPlant() {
        }

        @Patch.OnEnter
        public static void enter(@Patch.This Object vehicle) {
            RoadcraftHooks.beginImpulse(vehicle, "plantImpulse");
        }

        @Patch.OnExit
        public static void exit(@Patch.This Object vehicle) {
            RoadcraftHooks.endImpulse(vehicle, "plantImpulse");
        }
    }

    @Patch(
            className = "zombie.vehicles.BaseVehicle",
            methodName = "applyImpulseFromHitPedestrian",
            warmUp = true)
    public static final class HitPedestrian {
        private HitPedestrian() {
        }

        @Patch.OnEnter
        public static void enter(@Patch.This Object vehicle) {
            RoadcraftHooks.beginImpulse(vehicle, "zombieImpulse");
        }

        @Patch.OnExit
        public static void exit(@Patch.This Object vehicle) {
            RoadcraftHooks.endImpulse(vehicle, "zombieImpulse");
        }
    }

    @Patch(
            className = "zombie.vehicles.BaseVehicle",
            methodName = "applyImpulseFromHitCorpse",
            warmUp = true)
    public static final class HitCorpse {
        private HitCorpse() {
        }

        @Patch.OnEnter
        public static void enter(@Patch.This Object vehicle) {
            RoadcraftHooks.beginImpulse(vehicle, "corpseImpulse");
        }

        @Patch.OnExit
        public static void exit(@Patch.This Object vehicle) {
            RoadcraftHooks.endImpulse(vehicle, "corpseImpulse");
        }
    }
}
