package pzmod.roadcraft;

import zombie.roadcraft.BuildInfo;
import zombie.roadcraft.runtime.RoadcraftBridge;

/** ZombieBuddy lifecycle entry point. */
public final class Main {
    private Main() {
    }

    public static void main(String[] arguments) {
        RoadcraftBridge.bootstrapZombieBuddyRuntime(BuildInfo.VERSION);
    }
}
