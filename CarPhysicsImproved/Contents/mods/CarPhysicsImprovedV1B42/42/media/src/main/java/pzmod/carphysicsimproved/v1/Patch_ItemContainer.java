package pzmod.carphysicsimproved.v1;

import dev.carphysicsimproved.v1.runtime.LegacyCapacityHooks;
import me.zed_0xff.zombie_buddy.Patch;

@Patch(className = "zombie.inventory.ItemContainer", methodName = "getCapacity",
        warmUp = true, strictMatch = true)
public final class Patch_ItemContainer {
    private Patch_ItemContainer() {
    }

    @Patch.OnExit
    public static void exit(@Patch.This Object container, @Patch.Return(readOnly = false) int capacity) {
        capacity = LegacyCapacityHooks.adjust(container, capacity);
    }
}
