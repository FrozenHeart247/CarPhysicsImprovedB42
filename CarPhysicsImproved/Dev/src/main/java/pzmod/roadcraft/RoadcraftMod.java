package pzmod.roadcraft;

import me.zed_0xff.zombie_buddy.Exposer;
import zombie.roadcraft.runtime.RoadcraftBridge;

/** Small, stable Lua-facing facade exposed by ZombieBuddy. */
@Exposer.LuaClass
public final class RoadcraftMod {
    public RoadcraftMod() {
    }

    public static void setBoolean(String key, boolean value) {
        RoadcraftBridge.setBoolean(key, value);
    }

    public static void setNumber(String key, double value) {
        RoadcraftBridge.setNumber(key, value);
    }

    public static void requestShiftFor(int vehicleId, int direction) {
        RoadcraftBridge.requestShiftFor(vehicleId, direction);
    }

    public static double burnoutAmountFor(int vehicleId) {
        return RoadcraftBridge.burnoutAmountFor(vehicleId);
    }

    public static void activateConfiguration() {
        RoadcraftBridge.activateConfiguration();
    }

    public static String status() {
        return RoadcraftBridge.status();
    }

    public static String statusDetail() {
        return RoadcraftBridge.statusDetail();
    }

    public static String runtimeVersion() {
        return RoadcraftBridge.runtimeVersion();
    }

    public static String targetGameVersion() {
        return RoadcraftBridge.targetGameVersion();
    }

    public static String knownTestedGameVersion() {
        return RoadcraftBridge.knownTestedGameVersion();
    }
}
