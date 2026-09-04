package pzmod.carphysicsimproved.v1;

import dev.carphysicsimproved.v1.runtime.LegacyCabinExposureHooks;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.iso.weather.Temperature", methodName = "getWindChillAmountForPlayer",
        warmUp = true, strictMatch = true)
public final class Patch_Temperature {
    private Patch_Temperature() {
    }

    @Patch.OnExit
    public static void exit(
            @Patch.Argument(0) Object player,
            @Patch.Return(readOnly = false) float amount) {
        amount = LegacyCabinExposureHooks.adjustWindChill(player, amount);
    }
}
