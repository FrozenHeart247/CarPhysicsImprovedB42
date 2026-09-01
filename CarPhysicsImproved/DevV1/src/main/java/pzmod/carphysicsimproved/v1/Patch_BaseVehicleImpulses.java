package pzmod.carphysicsimproved.v1;

import dev.carphysicsimproved.v1.runtime.LegacyCollisionHooks;
import me.zed_0xff.zombie_buddy.Patch;

public final class Patch_BaseVehicleImpulses {
    private Patch_BaseVehicleImpulses() {
    }

    @Patch(className = "zombie.vehicles.BaseVehicle", methodName = "applyImpulseFromHitPlant",
            warmUp = true, strictMatch = true)
    public static final class Plant {
        private Plant() {
        }

        @Patch.OnEnter
        public static void enter(@Patch.Argument(value = 1, readOnly = false) float multiplier) {
            multiplier *= (float) CarPhysicsImprovedV1Mod.plantImpulse();
        }
    }

    @Patch(className = "zombie.vehicles.BaseVehicle", methodName = "applyImpulseFromHitPedestrian",
            warmUp = true, strictMatch = true)
    public static final class Pedestrian {
        private Pedestrian() {
        }

        @Patch.OnEnter
        public static void enter(@Patch.This Object vehicle) {
            LegacyCollisionHooks.begin(vehicle, CarPhysicsImprovedV1Mod.zombieImpulse());
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit(@Patch.This Object vehicle) {
            LegacyCollisionHooks.end(vehicle);
        }
    }

    @Patch(className = "zombie.vehicles.BaseVehicle", methodName = "applyImpulseFromHitCorpse",
            warmUp = true, strictMatch = true)
    public static final class Corpse {
        private Corpse() {
        }

        @Patch.OnEnter
        public static void enter(@Patch.This Object vehicle) {
            LegacyCollisionHooks.begin(vehicle, CarPhysicsImprovedV1Mod.corpseImpulse());
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit(@Patch.This Object vehicle) {
            LegacyCollisionHooks.end(vehicle);
        }
    }
}
