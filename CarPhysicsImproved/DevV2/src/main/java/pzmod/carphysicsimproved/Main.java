package pzmod.carphysicsimproved;

import dev.carphysicsimproved.v2.BuildInfo;

/** ZombieBuddy lifecycle entry point. */
public final class Main {
    private Main() {
    }

    public static void main(String[] arguments) {
        System.out.println("[CarPhysicsImproved] ZombieBuddy runtime " + BuildInfo.VERSION
                + " loaded; target B" + BuildInfo.TARGET_GAME
                + ", ABI tested on " + BuildInfo.TESTED_GAME);
    }
}
