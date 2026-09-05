package pzmod.carphysicsimproved.v1;

import dev.carphysicsimproved.v1.runtime.LegacyCabinExposureHooks;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.characters.ClothingWetness", methodName = "updateWetness",
        warmUp = true, strictMatch = true)
public final class Patch_ClothingWetness {
    private Patch_ClothingWetness() { }

    @Patch.OnEnter
    public static void enter(@Patch.This Object clothing,
            @Patch.Argument(value = 0, readOnly = false) float increase,
            @Patch.Argument(value = 1, readOnly = false) float decrease) {
        LegacyCabinExposureHooks.RainInput input =
                LegacyCabinExposureHooks.adjustRainInputs(clothing, increase, decrease);
        increase = input.increase();
        decrease = input.decrease();
    }
}
