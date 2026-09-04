package dev.carphysicsimproved.v1.runtime;

/** Exact keyboard chord, sampled by the controller; no latched Lua input state. */
public record LegacyDriftKey(int key, boolean shift, boolean ctrl, boolean alt) {
    public LegacyDriftKey {
        // LWJGL keyboard scan codes, not mouse bindings. Zero means unbound.
        key = key > 0 && key < 256 ? key : 0;
    }

    public boolean matches(boolean primaryDown, boolean shiftDown, boolean ctrlDown, boolean altDown) {
        return key != 0 && primaryDown
                && (isShift(key) || shift == shiftDown)
                && (isCtrl(key) || ctrl == ctrlDown)
                && (isAlt(key) || alt == altDown);
    }

    public boolean usesKey(int candidate) {
        return key != 0 && (candidate == key || shift && isShift(candidate)
                || ctrl && isCtrl(candidate) || alt && isAlt(candidate));
    }

    public boolean suppressesBrake(int primary, int secondary, boolean primaryDown,
            boolean secondaryDown, boolean forcedStop) {
        boolean overlap = usesKey(primary) || usesKey(secondary);
        boolean separateHeld = primary > 0 && !usesKey(primary) && primaryDown
                || secondary > 0 && !usesKey(secondary) && secondaryDown;
        return overlap && !separateHeld && !forcedStop;
    }

    private static boolean isShift(int code) { return code == 42 || code == 54; }
    private static boolean isCtrl(int code) { return code == 29 || code == 157; }
    private static boolean isAlt(int code) { return code == 56 || code == 184; }
}
