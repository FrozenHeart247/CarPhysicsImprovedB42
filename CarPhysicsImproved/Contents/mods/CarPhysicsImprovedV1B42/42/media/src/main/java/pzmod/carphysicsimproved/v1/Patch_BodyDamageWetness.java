package pzmod.carphysicsimproved.v1;

import dev.carphysicsimproved.v1.runtime.LegacyCabinExposureHooks;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.characters.BodyDamage.BodyDamage", methodName = "UpdateWetness",
        warmUp = true, strictMatch = true)
public final class Patch_BodyDamageWetness {
    private Patch_BodyDamageWetness() {
    }

    @Patch.OnExit
    public static void exit(@Patch.This Object bodyDamage) {
        LegacyCabinExposureHooks.afterWetnessUpdate(bodyDamage);
    }
}
