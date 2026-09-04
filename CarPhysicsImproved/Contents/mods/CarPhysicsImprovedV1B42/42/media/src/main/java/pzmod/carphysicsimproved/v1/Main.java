package pzmod.carphysicsimproved.v1;

import dev.carphysicsimproved.v1.BuildInfo;

public final class Main {
    private Main() {
    }

    public static void main(String[] arguments) {
        System.out.println("[CarPhysicsImprovedV1] ZombieBuddy physics runtime " + BuildInfo.VERSION
                + " loaded; target B" + BuildInfo.TARGET_GAME + ", ABI tested on " + BuildInfo.TESTED_GAME);
    }
}
