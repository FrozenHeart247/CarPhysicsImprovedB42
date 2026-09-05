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
            if (CarPhysicsImprovedV1Mod.enabled()) {
                multiplier *= (float) CarPhysicsImprovedV1Mod.plantImpulse();
            }
        }
    }

    @Patch(className = "zombie.vehicles.BaseVehicle", methodName = "applyImpulseFromHitPedestrian",
            warmUp = true, strictMatch = true)
    public static final class Pedestrian {
        private Pedestrian() {
        }

        @Patch.OnEnter
        public static void enter(@Patch.This Object vehicle, @Patch.Argument(0) Object pedestrian) {
            LegacyCollisionHooks.begin(vehicle, CarPhysicsImprovedV1Mod.enabled()
                    ? CarPhysicsImprovedV1Mod.zombieImpulse() : 1.0);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit(@Patch.This Object vehicle, @Patch.Argument(0) Object pedestrian) {
            LegacyCollisionHooks.end(vehicle);
        }
    }

    @Patch(className = "zombie.vehicles.BaseVehicle", methodName = "applyImpulseFromHitCorpse",
            warmUp = true, strictMatch = true)
    public static final class Corpse {
        private Corpse() {
        }

        @Patch.OnEnter
        public static void enter(@Patch.This Object vehicle, @Patch.Argument(0) Object corpse) {
            LegacyCollisionHooks.begin(vehicle, CarPhysicsImprovedV1Mod.enabled()
                    ? CarPhysicsImprovedV1Mod.corpseImpulse() : 1.0);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit(@Patch.This Object vehicle, @Patch.Argument(0) Object corpse) {
            LegacyCollisionHooks.end(vehicle);
        }
    }

    @Patch(className = "zombie.vehicles.BaseVehicle", methodName = "applyAllImpulsesFromProneCharacters",
            warmUp = true, strictMatch = true)
    public static final class ProneCharacter {
        private ProneCharacter() {
        }

        @Patch.OnEnter
        public static void enter(@Patch.This Object vehicle) {
            if (CarPhysicsImprovedV1Mod.enabled()) {
                LegacyCollisionHooks.applyProneBump(vehicle, CarPhysicsImprovedV1Mod.corpseBump());
            }
        }
    }

    @Patch(className = "zombie.vehicles.BaseVehicle", methodName = "updateVelocityMultiplier",
            warmUp = true, strictMatch = true)
    public static final class ObstacleSlowdown {
        private ObstacleSlowdown() {
        }

        @Patch.OnEnter
        public static void enter(@Patch.This Object vehicle) {
            LegacyCollisionHooks.beginObstacleSlowdown(
                    vehicle, CarPhysicsImprovedV1Mod.enabled() ? CarPhysicsImprovedV1Mod.obstacleSlowdown() : 1.0);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit(@Patch.This Object vehicle) {
            LegacyCollisionHooks.endObstacleSlowdown(vehicle);
        }
    }
}
