package pzmod.carphysicsimproved.v1;

import dev.carphysicsimproved.v1.runtime.LegacyTireTrackRenderer;
import me.zed_0xff.zombie_buddy.Patch;

/** Draws marks after cached terrain and before players and moving vehicles. */
@Patch(
        className = "zombie.iso.fboRenderChunk.FBORenderChunkManager",
        methodName = "endFrame",
        warmUp = true,
        strictMatch = true)
public final class Patch_FBORenderChunkManager {
    private Patch_FBORenderChunkManager() {
    }

    @Patch.OnExit(onThrowable = Throwable.class)
    public static void exit() {
        LegacyTireTrackRenderer.renderCurrentCameraLayer();
    }
}
